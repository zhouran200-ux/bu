# 全项目审计报告（正向审计 + 反向合规）v2

- 基线：`ff2e6bb`（main，比 origin/main 领先 1，工作区干净）
- 依据：`AGENTS.md` 全部条款 + 项目现有工具链
- 方法：六个领域并行深挖（持久化、网络会话、UI 职责、资源文案、测试 CI、反向合规矩阵），每条结论回到源码取证；本文覆盖并更正 v1 的两处错误
- 边界：静态审计 + 本地可执行检查。性能、内存、真实端点、真机 UI、厂商 ROM 不在覆盖内

## 0. 对 v1 的更正

| v1 的说法 | 实际情况 |
|---|---|
| 「颜色无硬编码，审计 100/100 合规」 | 审计恒为 0 的原因：ui/ 全部十六进制色（实测 131 处）都在 `design_audit.py` 的 7 个整文件豁免里，白名单外 0 处。检查被绕过，不是没问题 |
| 「verify.py 用正则拦截 assemble/package/bundle」 | 无此代码。`verify.py` 只是预先定义命令清单，靠 `test_cloud_validation.py` 断言「清单里不出现打包任务」，不构成运行时拦截 |
| 「最大手写文件 NotebookChatTab 670 行」（只测了 main） | 全项目最大是 `AppDatabaseMigrationTest.kt` **743** 行；main 代码内最大 `NotebookChatTab.kt` **712** 总行 |
| 「MIGRATION_18_19/23_24/24_25 无测试」 | 它们在 legacy 迁移数组里被实际执行（:35-37），只是缺独立断言；真正零引用的是 **MIGRATION_25_26** |

## 1. 阻断级风险（P0/P1）

### P1-1 旧库升级可能无法打开（高置信推断，需实测确认）

- `DatabaseMigrations.kt:293-305`：25→26 用 `ALTER TABLE … DROP COLUMN` 删 6 列，整段包在 `catch (_: Throwable) {}`；26→27 删 `agentTrace` 同样吞异常。
- `DatabaseMigrations.kt:135-136`：11→12 用裸 SQL 建了 `index_notes_isDraft`、`index_notes_hidden`；全文件无任何 `DROP INDEX`；26.json/33.json 的 notes 只声明 3 个索引。
- `minSdk = 28`，未引入 bundled SQLite；`AppDatabase` 无破坏性回退。
- 迁移测试从 v26 起（schema 最低 26.json），25→26 无覆盖。

推导：`DROP COLUMN` 需 SQLite 3.35+（Android 14/API 34）。API 28–33 上从 v25 及更早升级时删除失败被吞 → 旧列残留 → 最终 v33 校验列集合不匹配 → 数据库打不开。

建议：改用「建新表—复制—删旧表—改名」；并补一个手工构造 v25 库、迁移至 33 的插桩测试，在 API 33 上实跑确认。

### P1-2 页面层直连持久化与网络（明确违反 AGENTS.md:27-28）

| 位置 | 事实 |
|---|---|
| `ui/NotebookChatTab.kt:94,208,301,354,367,368,380` | Composable 内 `AppDatabase.getInstance`，直接 `chatDao` 删/插/改 |
| `ui/NotebooksScreen.kt:76,78,79,137,166,183,206` | 直接订阅 DAO Flow，执行 purge/insert/update/updateAll |
| `ui/NotebookDetailScreen.kt:64,102,104` | 直接查 notebookDao/noteDao |
| `ui/NotebookSourcesTab.kt:75,94` | 直接取库、直接调 `NotebookPurge.importDocument` |
| `ui/settings/ApiSettingsPage.kt:246,494` | 页面直接调 `LlmClient.fetchModels / testConnection` |
| `ui/settings/FontSettingsPage.kt:121,571` | 页面直接 `FontStore.import/delete` |

### P1-3 最高优先级「残留审计」零门禁

`check.yml:18-45` 的 source-hygiene 只有 unittest + verify quick + i18n diff，**没有任何旧符号/死代码/残留扫描**。AGENTS.md 用整章要求的功能，机器完全不设防。

### P1-4 数据被静默覆盖（P1）

- `Entities.kt:76-88`：`chat_messages` 的 `attachmentMime/attachmentData/attachmentName/attachmentList/replyToId/reasoningBlocks/quotedRole/quotedText` 生产代码从不读取。
- `ChatSessions.kt:70-79` 用默认值构造后 `@Upsert`：编辑或重生成旧回答会把这些列写成 null。
- `SettingsRepository.kt:255`：`api_profiles_json` 解析失败静默回落默认方案，下一次写入即覆盖，全部 API 方案（含 key）被替换且无提示。

### P1-5 写入失败语义不完整（P1）

- `NotebookChatTab.kt:300-310` 用户消息 insert 的 Deferred 不 await、不进 `pendingMessages`；失败只置 storageError，仍继续生成并保存回答 → 重启后问题丢失、回答成孤儿。
- `ChatSession.kt:71` 通用 `write` 失败不进重试队列，只有 `saveMessage` 有。

### P1-6 错误信息未脱敏（P1）

- `LlmClient.kt:323,338` 非 2xx 把服务端原始错误拼进异常，`ChatSession.kt:162-177` 会把它存进对话内容并展示；与 testConnection 的三条脱敏路径不一致。
- `LlmClient.kt:89` fetchModels 失败把完整 body 拼进异常，`ApiSettingsPage.kt:262` 截 180 字直接显示。

## 2. 正向审计：合规项（有证据）

| 规则/领域 | 结论 | 证据 |
|---|---|---|
| 禁止注释、括号平衡、行数上限 | 部分合规 | 199 文件通过（但上限 3000 与规则 1200 不一致，见 §4） |
| 不得阻塞主线程 | 合规 | main 源码 `runBlocking`/`Thread.sleep` 0 处 |
| 长文本解析后台化 | 合规 | `Markdown.kt:56-81`、`ChatRequestRunner.kt:76-93`、`ChatCitations.kt:21-28` 均在 Default |
| 流式节流 | 合规 | `StreamUiScheduler.kt:48-59` 35ms/48 字符窗口，Session 已启用 |
| SSE 帧边界与坏事件拒绝 | 合规 | `SseEventReader.kt:21-48`；`StreamingResponseReader.kt:34-46` 拒多 JSON/坏 JSON |
| OpenAI/Anthropic 结束条件 | 合规 | `[DONE]`、`message_stop` 终止，EOF 无结束信号抛错 |
| 取消与资源释放 | 合规 | `BufferedTransport.kt:15-29`、`LlmClient.kt:303-332` call.cancel + response.use |
| 生成所有权与删除互斥 | 合规 | `ChatSession.kt:108-121,192-207`、`NotebookDeletionGuard.kt:13-24` |
| DB schema 与迁移注册 | 合规 | v33，2→33 共 31 个迁移无缺号，33.json 与实体逐项一致，无破坏性回退 |
| 聊天查询索引 | 合规 | `chat_messages(conversationId,timestamp)` 对应 `Daos.kt:33` |
| 事务 | 部分合规 | 导入/删除用 `withTransaction`；编辑重生成两步写入未包事务（`NotebookChatTab.kt:366-369`） |
| 缓存并发 | 合规 | `SettingsCache` AtomicReference 组合更新；`NovelSourceIndex` 锁外计算+锁内复核 |
| 测试执行守卫 | 合规 | `test_inventory` 拒 0 测试/全 skip/失败/畸形 XML；基线 419/66 防删 |
| 版本与缓存一致性 | 合规 | 3.1.2 双源一致；11 读/11 写 |
| 签名与发布安全 | 合规 | Gradle 9.8.1+SHA256；正式发布缺密钥即失败 |
| 权限与组件最小化 | 部分 | 组件仅 1 activity + 1 service；但 `ACCESS_NETWORK_STATE` 无任何使用 |

## 3. 反向合规：规则 ↔ 强制手段

| AGENTS.md 规则 | 强制手段 | 缺口 |
|---|---|---|
| 无注释/括号/行数卫生 | `code_hygiene_check.py`（阻断） | 阈值 3000≠1200；无 500–800 提示；禁止注释是本工具自设，规则里没有 |
| 单测/编译/Lint | check.yml（阻断） | 本地 `verify.py` 不跑 63 项契约与 i18n |
| 测试不得静默减少 | `test-baseline.json`（阻断） | 只覆盖 JVM 419/66，设备测试 36 例无下限 |
| i18n 生成一致 | check.yml（阻断） | 只校验生成物=catalog，不校验 UI 是否用 `S.*` |
| 页面不得混 DAO/网络/Prompt | **无** | 现状 8+ 处违反 |
| Route/Screen 分工 | **无** | 63 项契约中 48 项只是源码文本匹配 |
| 500–800 审查 / 1200 上限 | **无**（3000 硬线） | `NotebookChatTab` 712、`ApiSettingsPage` 611、`FontSettingsPage` 593 均未记录审查 |
| 残留审计（整章） | **无** | 无旧符号/死代码扫描；`lastDelete`、`untitled`、`getAllOnce` 等仍在 |
| 未使用 import / 重复实现 | **无** | 无 detekt/ktlint；`MainActivity` 38 个、`ApiSettingsComponents` 62 个未使用 import（抽样确认） |
| 不得本地打包 APK | **无** | 无 hook，`verify.py` 不拦截 |
| 迁移须行为验证 | 部分 | 25→26、26→27 的关键删列无测试 |
| 正式发布须已验证 | **无** | `release.yml` 不依赖 check，测试全红也能发 |
| 设计规范 | 参考（不阻断） | 审计无 `--strict`；白名单整文件豁免 |

## 4. 检查工具自身的缺陷（本轮新发现）

| 工具 | 缺陷 | 证据 |
|---|---|---|
| `code_hygiene_check.py` | `MAX_LINES=3000` 与规则 1200 冲突；只打印「最大文件」；强制 `ScaleIndication.kt` 保留版权注释却又全局禁止注释 | :8、:125-126、:131-139 |
| `design_audit.py` | `if len(hex_hits) == 1` 只报单色行，一行多个色漏检；白名单 7 文件豁免全部颜色 | :65、:10 |
| `verify.py` | 设计审计不带 `--strict`，永不阻断；不跑契约与 i18n；无打包拦截 | :17、:72-74 |
| `test_report_summary.py` | 恒返回 0，CI 汇总步骤永不失败 | :56 |
| `settings_cache_check.py` | 只比对「读过的键是否有写」，不校验值与原子性 | :31-48 |
| `test_inventory.py` | 只比 tests/suites 总数，等量空测试或大面积 skip 可绕过；基线键缺失直接跳过 | :19-21、:57 |
| `release.yml` | 只在 release_notes 非空时校验格式；prepare 只跑 scroll check | :66、:75 |
| `check.yml` | 模拟器 api 35，target/compile 36；dependencyInsight 三次调用只 tee 不解析 | :128、:121 |

## 5. 其余问题清单（按领域）

**持久化/数据**
- `chat_messages` 8 列、`Note` 14 列、`Notebook` 4 列在生产代码无读写；`notes_fts/tasks_fts/task_versions/tasks` 旧表与 6 个触发器永久保留，FTS 触发器每次导入/删除复制整段正文。
- `TrashCleanup.kt:24-27` 每次启动无条件 `deleteRecursively` 两个附件目录且忽略返回值；函数名与 trashedAt 无关。
- `SettingsRepository.kt:255` 解析失败静默回落（见 P1-4）；DataStore 无 corruption handler。
- 设置页先写 cache 再异步落盘（`ThemeSettingsPage.kt:66-67` 等），失败只 Log；同类写入协程作用域不一致（`BackgroundSettingsPage.kt:85` 用页面 scope，离开即取消）。
- `ChatSessions.kt:61-68` 一次读整段会话；dispatcher 为 `Main.immediate`；`forNotebook` 不查 tombstone；`linkedMap` 插入序淘汰非 LRU；`finishDelete` 不清 tombstone。
- `FontStore.kt:35-57` 在 `@Synchronized` 内做磁盘 IO。
- 索引覆盖不足：`Daos.kt:15` 用 `TRIM(title)`；`notebooks` 缺 manualOrder；`notebook_items` 缺 addedAt。

**网络/会话**
- `ChatSession.kt:112,137` 先写空 assistant 占位，流中只改内存；进程被杀后 DB 留空行，`load` 原样返回，历史里会把空内容发给模型。
- `ChatGenerationService.kt:50,69-70` 停止后立即 `stopProtection`，最终保存未完成即撤通知。
- `StreamingResponseReader.kt:72` Google finishReason 不终止读取，依赖 EOF，可能等满 40s 判失败。
- `StreamingRequestBuilder.kt:114` Google key 未 URL 编码且 query+header 双发。
- `StreamingRequestBuilder.kt:81` Anthropic `max_tokens` 固定 ≥8192，部分 4096 上限模型会 400。
- `LlmClient.kt:17-341` 单文件四职责（header/UA、fetchModels、testConnection、chatStream）。
- `LlmClientModels.kt:96` `toFullReport` 仅测试调用，`fullReportRaw` 成死代码。

**UI/性能**
- 0 个 ViewModel；`ApiSettingsPage` 611 行 / 29 入参 / 9 个自持状态。
- 高频重组重活：`NotebookChatTab.kt:97` 每次重组重建 readingKeys；`ChatMessageComponents.kt:148-157` 每帧跑正则+新建 SimpleDateFormat；`ChatHistorySheet.kt:160-180` 每行跑 1024 字符正则。
- 三套滚动位置记忆并存（`ListScrollMemory`/`SettingsScrollMemory`/`ReadingPositionMemory`）。
- `NotebookChatTab.kt:482-491` 页面自写指针循环做浮层外点命中。

**资源/文案**
- 用户可见硬编码中文 189 处 / 29 文件；其中 11 处 catalog 已有键仍写死（“取消”“删除”“保存”“恢复默认”）。
- `untitled` 键 0 引用；`ic_launcher_round` 无引用；`ACCESS_NETWORK_STATE` 无使用。
- `values-night` 缺 themes.xml；`values/colors.xml` 与 `ThemeModes` 常量逐值重复。
- 契约测试锁死中文字面量（“详细信息”“跳到顶部”“管理 API 配置”），迁移到 `S.*` 会先让测试变红。
- 文案口径不一致（“模型选择” vs “选择模型”、“资料/文档/来源”）；错误提示丢 cause、不可执行。

## 6. 量化基线（本次实测）

| 项 | 数值 |
|---|---|
| Kotlin 文件（main） | 126 个；<100 行 75、100–300 行 39、300–500 行 9、500–800 行 3、>800 行 0 |
| 最大手写文件 | `AppDatabaseMigrationTest.kt` 743（androidTest）；main 内 `NotebookChatTab.kt` 712 |
| 测试 | JVM 66 文件 / 419 例；插桩 6 文件 / 36 例；无 `@Ignore` |
| Python 契约 | 63 项，其中 48 项只读源码文本 |
| 中文字面量 | UI+chat 189 处；含 data/network 合计约 286 处 |
| 十六进制色 | ui/ 内 131 处，全部在 7 个白名单文件 |
| 未使用 import | `MainActivity` 38、`ApiSettingsComponents` 62 |
| 数据库 | v33；2→33 共 31 个迁移；schema 26–33 |
| 当前自动化 | hygiene 通过、design 100/100、契约 63 通过、verify quick 6 项通过、编译/单测/Lint 通过 |

## 7. 整改优先级

| 优先级 | 事项 | 理由 |
|---|---|---|
| P0 | 迁移 25→26/26→27 改为全 API 安全实现 + 补 v25→33 插桩测试（API 33） | 可能是旧用户升级即崩的缺陷 |
| P0 | 增加「残留审计」门禁（旧符号清单 + 未使用 import/dead code） | AGENTS 最高优先级规则，当前零强制 |
| P1 | 页面 DAO/网络上移（R01/R10 收尾） | 明确违反规则，且是写失败语义混乱的根源 |
| P1 | 修复静默数据覆盖：`@Upsert` 默认值覆盖 8 列；`api_profiles_json` 解析回落 | 数据丢失 |
| P1 | 补齐写入失败语义（通用 write 进重试、user insert 失败阻断生成） | 数据不一致 |
| P1 | 错误信息统一走 `DiagnosticRedaction` | 密钥/服务端内容泄露 |
| P1 | 契约测试补行为断言 + 设备测试基线 | 当前 76% 契约是空转 |
| P2 | 工具阈值对齐（3000→1200、500–800 提示）、设计审计 `--strict` 入阻断、release 依赖 check | 让规则真正生效 |
| P2 | 文案迁 `S.*`（同步改契约测试）与未使用资源清理 | 一致性 |
| P2 | 索引与查询优化、FTS/旧表清理、TrashCleanup 语义修正 | 性能与残留 |

## 8. 未覆盖

- 真机动画手感、手势冲突、视觉观感、厂商 ROM、IME
- 长文峰值内存、增量解析性能、滚动帧耗时、多会话内存
- 真实第三方中转与各模型端点协议兼容
- P1-1 的升级故障未实机复现，结论基于代码逻辑推导，需按 §7 的测试验证后再定性

## 9. 整改结果（本轮集成验证）

本节替代旧版“只剩两项”“没有 Compose 测试依赖”等过时结论。§0–8 是原审计快照，不代表当前实现。实现、编译、实际执行分别记录，不以基线代替修复。

### 已推送基线

本轮从 0a5d13c 开始；独立核查确认 [check #103](https://github.com/zhouran200-ux/bu/actions/runs/39116541401) 与 [APK/Preview](https://github.com/zhouran200-ux/bu/actions/runs/39116541365) 全成功。模型选择回调时序及字体并发目录失败本次未再出现。未读取需认证的设备报告 ZIP，不报未经读取的逐测试数量。

### 第一集成批次（本地提交 b476c46）

| 原清单 | 本轮补齐 | 验收边界 |
|---|---|---|
| P0 迁移 | 手工v25→26→35完整链，最终Room校验；notes/chat存续字段、空值、载荷、默认值、索引与退休对象断言 | 编译通过；API33/35新矩阵设备执行待最终推送 |
| P0 残留门禁 | source_audit退役符号/重复import硬失败，verify启用--fail-on-candidates；清理确认的116生产import、4测试import、2私有尺寸常量 | 234源码/配置0错误/0候选；候选阻断不是语义dead-code证明 |
| P1 写失败 | 全持久化FIFO重试，成功项不重放，相邻非active同消息可合并、删除边界不合并；初始保存失败阻断网络并恢复原回答；不再重生成前删旧答案 | 故障注入、顺序、取消、回滚测试通过；队列仅进程内，不承诺被杀后恢复未落盘数据 |
| P1 生命周期 | Session.afterWrite持有保存后生成；pending action防淘汰/重入；删除准备拒绝后续生成 | 离页/挂起保存/删除用例通过；回执取消只取消观察，内部操作取消不重放 |
| P1 脱敏 | 网络catch安全包装无原始cause/suppressed；先脱敏后截断；识别已知key的JSON转义；正则避免长文本栈溢出 | 实际loopback HTTP、非法URL、格式化/复制、流式关闭回归通过；不识别未知秘密/任意多层编码 |
| P1 行为测试 | 四类Compose共19条真实交互：模型分组/选择关闭、勾选/取消重开、历史回调/空态、设置草稿/保存/恢复默认/边界 | 全部设备源码编译；未本地运行，不能算设备通过。其余契约逐项映射仍需收口 |
| P1 测试执行 | inventory按真实testcase核对汇总，缺baseline/key失败，skip不满足executed下限 | JVM真实444/71已锁；设备保留此前确认的36/6，不把源码声明64例当实跑 |
| P2 设计工具 | 每次颜色命中均检查；移除整文件/图标整行豁免，精确文件+作用域+声明+用途预算，重复/挪用/新增色阻断 | 247个明确颜色定义，0未审/陈旧条目；启发式不是视觉验收 |
| P2 发布 | API33/35完整矩阵及check汇总全部成功才放行；预览等待约40分钟，失败拒发 | Python契约通过；新工作流云端执行待推送 |
| P2 文案 | UI/chat CJK和直接Text/contentDescription英文回归门禁；Locked迁目录；未保存提示涵盖全部更改 | 80文件0违规；不涵盖间接英文变量/所有data内部字符串 |
| P2 标题清理 | title精确索引查询实际接入；兼容空白标题仅首次投影id/title再按digest核验；删除/unlink事务；Mutex+独立durable marker | 编译通过；设备回滚/取消/并发/EXPLAIN实际执行待云端；旧TRIM DAO已移除 |

前序已推送修复保留：安全重建表及33→34退休表/索引/FTS清理、v35标题索引、六页面数据操作上移、消息保存保留旧载荷、API JSON损坏拒绝覆盖、空答案过滤、Google编码/结束帧、Anthropic预算、文案/资源/权限清理。未重写既有历史。

### 验证证据

- Debug、Release JVM各444 tests/71 suites，0 failure/error/skip，真实XML及新版inventory均通过。
- Debug/Release Kotlin、完整androidTest Kotlin编译通过；未在本地运行assemble/package/bundle/connected。
- lintDebug通过，仍18 warnings/4 hints，另有baseline过滤项；不是零告警。最后一处afterWrite清理只补跑受影响测试/编译，之前Lint结果不冒称覆盖未测改动。
- Python完整工具测试112项通过，verify quick八项通过，git diff --check通过。
- i18n生成器已运行，现379条；旧写入符号登记retired。
- 新API33/35迁移与设备用例仍待最终推送后Actions执行，上轮全绿不能替代新代码。

### 第一批次后的待办快照（已由下文最终集成更新）

1. 契约逐函数映射和剩余真实交互验收：共享drawer下拉/遮罩关闭、键盘焦点、选择/重排等仍不能仅靠源码字符串断言。19条组件测试不等于所有契约行为化。
2. 新迁移和UI测试需要云端API33/35实际执行；本轮未提前推送。
3. §5其他观察项并未全清：设置异步写失败可见性、主线程映射/全量会话内存、停止服务早于最终落盘、滚动记忆职责和readingKeys/统计缓存等须继续按证据界定，不能宣称全项目无遗留。
4. 大文件拆分审查：迁移测试898行应抽legacy fixture/快照断言；NotebookChatTab683行应继续将操作所有权放Session；API583/字体593行须保持单一状态源拆分。未超1200不等于已完成职责解耦。
5. 历史2523197崩溃日志已由32c3f12从工作树删并忽略；历史副本清除须明确授权，不擅自重写或强推。正式签名需所有者配置，不擅自换签或发布正式版。

### 第二集成批次与交付验收

- 新增共享抽屉10个、推理/入口5个、真实Window flags 2个、滑块3个、选中动画/主题行5个、重排7个、文本选择4个、文本派生缓存2个真实Compose用例；连同前批四类19个，共57个UI行为用例定义。所有androidTest源码合计102例/20类已编译，并将102/20作为CI要求的执行下限；**这是要求与源码数量，不是本地设备通过数量**。
- 原58个纯源码/配置/资产契约已逐函数映射到真实Kotlin方法，详见[契约覆盖矩阵](<contract-behavior-coverage-2026-10-18.md>)。结构护栏保留，不以删除它们降低数量；字段、视觉参数、路由接线的源码检查不冒充运行时集成测试。
- 正文统计和历史摘要已在Dispatchers.Default计算，按每行真实输入缓存；流式摘要100ms有界节流并保留同一流上一摘要，静态正文编辑立即失效；readingKeys采用轻量derivedStateOf，逐token仍会重新映射但相同结果不向下传播。
- 通知停止和划掉任务不提前撤生成保护，等待activeCount随最终保存结算归零；平台onTimeout仍立即停止以遵守系统时限。新增3个最终保存挂起/失败/多Session结算测试。
- 外观设置由应用级SettingsWriteCoordinator统一接受并有序落盘，页面离开不取消；每key revision避免旧成功/失败覆盖新输入，失败回滚已知持久值并Toast提示；reset三字段同事务；新增5个故障/顺序/取消/离页用例。首次读持久值失败只回退已知缓存，不声称其必为落盘值。
- 严格源码审计修正private扩展接收者误报；仓库当前0硬错误/0候选。文案目录380条，重新生成字节一致。

最终本地证据：Debug/Release JVM各 **455 tests/74 suites**，无failure/error/skip；完整androidTest Kotlin与Debug/Release主源码编译通过；lintDebug在最终源码上通过，仍18 warnings/4 hints及baseline过滤；Python113项、verify quick8项通过。没有本地打包或运行设备任务。

残留审计：新旧写入符号已登记退役；旧TRIM DAO零调用；六页面无原直接DAO/网络调用回潮；服务停止无其他提前撤保护入口；所有外观setter/reset生产调用只在AppScope；清理无效import/重复实现，新增文件均职责单一。已删除确认的编辑器临时副本，未纳入产物/崩溃日志。

交付范围是§7优先级表的修复、门禁、行为覆盖补充和报告验收，不是承诺所有静态结构检查改成UI截图。以下仍是明示边界而非冒称完成：新API33/35设备矩阵待推送后执行；系统toolbar点击/跨段句柄拖动、厂商IME/预测返回/旋转、完整父页面端到端持久化链路、性能峰值尚未实机测量。保留实体旧列以避免无收益不可逆删数据，仅有新增死列门禁；DataStore损坏不静默重置密钥。滚动记忆分工及全量会话内存属于§5观察项，未伪称已经重构。

历史日志重写/正式签名需用户另行授权或配置，不自动执行。最终提交推送后不轮询本轮Actions，下一实际开发轮再独立核查。

### 后续一次快照与取消边界修复

- 对 b8d10ff 的独立一次快照（2026-10-18 20:00 UTC+8）：source-hygiene、android-jvm-check、build-apk success；API33/35及publish-preview仍运行，无设备失败注解。入口check run 39125059389 / APK run 39125059385。未轮询等待，不将运行中写成通过。
- 新增确定复现：设置命令接受后更新缓存，但worker尚未调度即取消时，原try/catch不会执行，缓存残留draft。已用先失败后通过的JUnit证实。新增统一且幂等finish，并在Job completion兜底；不更改普通保存顺序或revision保护。
- 新增3个取消边界用例（调度前取消、旧命令取消不回滚新输入、已取消owner），SettingsWriteCoordinatorTest共8项实际通过；verify quick8项通过、主源码增量编译通过。其余结果复用b8d10ff，不冒称本次全量测试；本修复未变设备测试。
- 此小修先本地提交，不立即推送：避免仓库cancel-in-progress配置取消正在跑的API33/35完整验收。下一轮一次核查取得设备结论后，合并必要修复统一推送。
