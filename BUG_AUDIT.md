# 全项目 Bug 审计结果

## 2026-10-31：API 方案导入冲突与模型合并

- TXT 解析按最先出现的 `:` / `=` 分栏，修复 `baseUrl=https://...` 和含冒号密钥被静默截断。
- 未知 `[标题]` 段落现在会结束当前记录并跳过段内字段，不再污染前一条方案，也不会把未知段落当成方案导入。
- 导入文件内相同非空 API Key 的方案会先合并模型并去重，例如 1、2 与 3、4 合并为 1、2、3、4。
- 合并导入只新增方案，不更新现有方案；导入 Key 与现有方案冲突时先弹窗列出冲突，确认后跳过冲突项。替换模式仍按用户明确选择替换全部现有方案。
- 新增等号分栏、未知段落隔离、同 Key 模型并集、现有方案不被改写、冲突跳过和完全重复备份等回归测试。
- 验证：Debug JVM 688 项 / 105 套件全通过；Python 工具测试 154 项通过；quick 门禁 10/10 通过；Debug Lint 通过（31 warnings、5 hints，baseline 未扩大）。未运行设备测试、Release JVM 或本地 APK/AAB 打包。

## 2026-10-30：思考能力范围按实际使用收窄

- GPT 只保留 5.5 及以后；5.4、5.2、5.1、5、o1/o3/o4-mini 等旧模型不再映射思考参数，未知时使用服务商默认。
- Claude 只保留 4.5 及以后：Sonnet/Haiku 4.5 走 thinking budget，Opus 4.5 走 effort+budget，4.6+ 走 effort/adaptive；3.x、3.7、Sonnet 4、Opus 4/4.1 不再识别，也不会被装入思考字段。
- Grok 3 mini 移除；Grok 4.5/4.6/4.7 保留。
- Claude/Gemini 的验证重点改为中转站 OpenAI 兼容路径：Claude 4.5 预算、Opus 4.5 effort+budget、Claude 4.6+ adaptive effort、Gemini level/budget 均新增实际 JSON 断言，确认不会混入另一协议的字段。
- 旧模型输出上限常量仍保留为安全截断，不代表继续提供旧模型思考档位；旧模型命中时会落到“未识别/模型默认”。
- 同步更新模型能力审计测试、旧别名期望、测试连接探测范围与文档。验证以本轮集中运行为准，不把静态浏览当成执行结果。
- API 方案卡导入/导出合并为一个按钮；方案数量取消 50 上限并只显示当前数量；同一非空 API 密钥禁止重复保存，冲突提示包含已有方案名称。方案卡与模型选择弹窗增加搜索，模型弹窗的全选/取消全选合并为单按钮。
- 验证：全量 Debug JVM 682 项/105 套件全通过；Python 153 项、quick 10 项门禁、Debug/Release Kotlin、Android 测试代码编译和 Debug Lint 通过；未运行设备测试或打包 APK。API 方案卡改为每 10 个一页，卡片高度压缩约 1/4，下方字段区域未改。

## 2026-10-29：思考强度下发专项审计

基线 `02c3d44d15b2953bcdccf70d54aa39d48b9e5858`。检查链路：聊天偏好 → ReasoningResolver → 模型能力/协议策略 → StreamingRequestBuilder → 实际 JSON；同时复核 API 设置摘要与连接探测。

### 确认问题与修复

1. **组合中转别名漏识别**：`[RP]gpt-5.5-reasoning` 去标签后未继续应用已支持的后缀规则，导致识别失败、所选档位没有下发。统一候选集组合标签/前缀与后缀，保留原始请求 model ID；未知名称仍不推测能力。
2. **档位回退受 metadata 顺序影响**：只支持 LOW/HIGH 的模型，MEDIUM 等距时，返回结果取决于服务商数组顺序。现在等距固定取较低强度，并纳入合法 none 档位；不会因为数组倒序悄悄变强。
3. **不完整能力信息猜出 max**：未知模型只提供 effort 类型、不提供 levels 时，旧代码沿用任意目标值。现在必须提供有效 levels，或继承同类型已知模型的 levels；显式 supported=false 无需额外 control 字段即可禁用覆盖。
4. **Claude 输出上限与摘要预算不一致**：旧上限只匹配区分大小写的一部分拼写，反向顺序、紧凑别名、部分快照会漏限；解析阶段也未采用输出上限，导致摘要显示的 budget 被序列化再次缩小。共享 AnthropicModelLimits 做已知模型别名归一化和预算预限制，保留至少 1024 token 回答空间。未新增未经验证的供应商额度。
5. **成功刷新后旧 metadata 残留、摘要不刷新**：原先只累加发现记录，服务商撤回能力仍可能沿用旧控制；摘要 remember 也只观察模型/协议/地址。完整拉取成功后按端点/协议原子替换，失败保留原数据；发布 revision 使摘要更新。未改变精确模型 ID/命名空间隔离规则。
6. **发送边界少一道协议校验**：旧的或外部构造的跨协议配置能绕过解析层，在原生 Claude 请求中误显示/误带控制。请求构造再按实际协议及最终 URL 应用同一策略；地址首尾空白不再使官方 Claude 兼容限制失效。
7. **连接测试与聊天档位易混淆**：连接测试发送 hello world，未携带聊天所选思考档位；按钮下方摘要是聊天各档位映射，不是本次测试抓包。补充原位置小字说明，不新增控件、不更改聊天档位选择。

### 验证

- 新增 7 项 JVM 回归（组合别名、等距/none、缺失 levels、Claude cap 别名与预算一致性、请求协议过滤、成功/失败 catalog 刷新）。原有供应商 JSON、AUTO 不下发、budget/effort 互斥测试保留。
- 全量 Debug JVM：679 项、105 套件，0 失败/错误/跳过；XML inventory 门禁通过。
- Debug/Release Kotlin 与 Android 测试代码编译通过；新增摘要随 metadata 变化的 Compose 测试仅编译，设备执行交 CI。测试使用项目已有 Compose Rule API，编译有弃用提示，未声称设备运行通过。
- Python 工具测试 151 项通过；quick 10 项全通过；source audit 350 文件、0 错误/候选。Debug Lint 通过，仍有 29 warnings、5 hints，baseline 未扩大。
- 没有本地 APK/AAB 打包、没有真实供应商付费请求。新测试核对的是本地产生的请求 JSON，不证明任意中转站接受、转换或执行这些字段。

### 保留边界与审计残留

- AUTO 表示不覆盖服务商默认，不是强制关思考；极速也不保证关闭。模型不支持五个独立强度时，多个 UI 档位可以映射到同一值，这是现有设计，不虚构额外供应商档位。
- 模型目录的 reasoning metadata 是可选扩展，不是所有供应商的统一标准。当前仍按端点/协议/精确模型隔离，未按 API Key 分账号；并发目录请求的先后次序与第三方网关字段转换属于后续边界。
- Google 兼容 raw HTTP 中 extra_body.google.thinking_config 的结构已与官方 REST 示例核对，未误当 SDK 参数而扁平化。Claude/OpenAI/Google 的原有原生与兼容字段保持。
- 已全局搜索旧 registerDiscovered 与旧 cap 声明，旧累加写入路径/网络层重复 cap 已移除；测试 import 同步。未改 Manifest、导航、通知、数据库/偏好键、资源主题、Gradle/依赖、R8、主副包名或工作流，不存在该次迁移专属资源/配置待删。
- 上轮 [check](https://github.com/zhouran200-ux/bu/actions/runs/40835490291) 及 [Build APK only](https://github.com/zhouran200-ux/bu/actions/runs/40835490148) 均成功；源码/JVM/API33/API35 jobs 成功。独立审查代理调用失败，未完成 artifact/完整日志级独立复核，以上为主代理一次性 API 查询。

参考：[Google OpenAI 兼容 REST 示例](https://ai.google.dev/gemini-api/docs/openai)、[Claude effort](https://platform.claude.com/docs/en/build-with-claude/effort)、[Claude extended thinking](https://platform.claude.com/docs/en/build-with-claude/extended-thinking)、[DeepSeek 思考默认与 effort](https://api-docs.deepseek.com/guides/thinking_mode/)。

---

## 2026-10-26：可选模型生成测试及全项目审计

本轮基线 `699dc28`。下方旧基线的数字仅为历史结果，不代表本轮。

### 已修复

- API 设置原先固定向测试函数传空模型，实际只有配置探测。现在在现有可用模型列表中点选一行作为测试模型，选中时发送一条 `hello world`，再次点选取消；未选择时只检查配置。测试不自动保存、不改变聊天模型。切换配置或模型取消测试并隔离过期结果。
- 统一三协议探测 payload，移除旧 `ping` 构造和重复分支；普通 OpenAI/Anthropic 预算 64 token，既有推理模型预算保留。生成测试有独立的 90 秒读/100 秒调用超时，避免直接套用模型列表的 20 秒读超时。
- HTTP 2xx 不等于生成成功：检查协议对应可见文本，空正文、错误对象、仅推理内容不再显示生成成功。诊断摘要在脱敏后限制为 600 字符，原响应仍保留。
- SSE 的内容、思考、Google finishReason 改为严格字符串读取，避免 Android JSONObject.NULL 和 JVM org.json 差异导致字面量 null/对象进入正文。
- 非成功流式 HTTP 响应使用已有 2 MiB 缓冲上限，并设置错误正文 source deadline；不能将这一点声称为所有网络阻塞场景已经证明具有硬超时。
- 删除无调用的 SettingsRepository.apiProfileSelected 单独 Flow，保留存储键、启动快照及原子组合 Flow。更新 i18n 生成文件、Python 契约与文档。

### 审计范围与残留处理

|范围|结果与边界|
|---|---|
|Kotlin/Java、Activity、Compose、状态与回调|全项目静态扫描加关键路径阅读；请求所有者、会话写队列及取消路径复核。没有新增 ViewModel 或第二份持久化模型选择。|
|Repository、网络、Service|修复上述协议、正文体积与诊断问题；鉴权头、禁止自动重定向、前台生成服务及通知权限声明保留。|
|导航、Deep Link、Intent|无新增导航路由/深链；Manifest 仅 launcher 及非导出生成服务，没有旧测试功能 Intent 需要删除。|
|Room、DataStore、SharedPreferences|无 schema/偏好键迁移。旧实体字段由现有兼容基线记录，不因无 UI 引用擅自删列；损坏设置备份保留以避免破坏恢复证据。|
|XML、图标、字符串、颜色、主题|生成文案与主题检查通过；未新增资源或权限；未将 Lint baseline 内的警告称为已修复。|
|Gradle、依赖、R8、工作流|没有更改依赖和构建发布逻辑；保持主副 APK 包名分离。serialization debug 依赖有历史 CI 修复背景，不以无源码 import 为由删除。|
|Worker、通知、日志、Feature Flag|未引入后台测试服务/通知/开关；测试使用现有页面 scope；检查前台服务声明与停止路径。|
|单测、UI 测试、Mock/Fake|新增 loopback 请求测试覆盖三协议 POST、raw POST、GET/HEAD、probeKind；新增响应验证与 null 字段回归测试。可用模型列表的点选交互尚未实机验证。|
|文档、TODO、unused import、死代码|旧测试功能标识全局复查；余下 ping 为聊天测试输入而非生成探测。源码门禁 346 文件、0 错误/候选；这不是语义死代码完备证明。|

### 明确保留的审计后续项

- NotebookChatTab 511 行经过职责审查：入口协调、聊天布局、偏好同步、弹窗接线仍在同一文件；适合后续按偏好所有者/弹窗宿主拆分，本轮未把纯行数拆分当作 bug 修复。ChatSessionTest 642 行保留共享状态机测试。
- 紧凑按钮触摸区域、页签状态同步、滚动性能等静态候选需 Compose 设备证据；不凭布局尺寸直接断言实际扩展触摸区域失效，也不贸然改变用户紧凑布局。
- 损坏 DataStore 备份包含敏感配置、恢复通知未接线；需要恢复/清除交互设计，未直接删除备份。字体删除失败后的缓存恢复、清理任务相互影响、更新时间回退为后续容错整改项。
- 现有 CI 测试数量下限较旧；设备基线不能依据 @Test 数直接提高。版本码拼接公式对未来多位版本号有单调性风险；需单独制定与既有安装版本兼容的策略。
- HTTP 明文地址受 Android 平台策略限制；本轮未扩大明文访问。未验证真实付费供应商调用、慢速错误体极端网络行为、UI 手感或设备运行。

### 上轮云端复核

已用 GitHub API 核对基线提交的 [check](https://github.com/zhouran200-ux/bu/actions/runs/40206105011) 和 [Build APK only](https://github.com/zhouran200-ux/bu/actions/runs/40206104963) 均成功，check 的源码/JVM/API33/API35 jobs 均成功。未下载完整日志和测试 artifact，未声称完成独立代理的日志级复核。

### 本轮本地验证

- Python 151 项全部通过；quick 10 项门禁全部通过。
- Debug JVM XML 实际统计：670 项、104 套件，0 失败/错误/跳过；test_inventory 通过。
- Debug/Release Kotlin 与 Android 测试源码编译通过；Debug Lint 通过：29 warnings、5 hints；baseline 另过滤 18 warnings、2 hints，并报告 13 条过期 baseline 条目。本轮未扩大 baseline，未声称警告清零。
- 没有本地运行设备测试或 APK/AAB 打包；Release JVM、Release Lint 和本轮设备矩阵交云端。

---

## 历史审计（以下“本轮”均指旧基线）

基线：`f7b6db0982abc652e95b6d8b284a48096fdce306`。标准：崩溃/数据完整性、请求正确性、凭据隔离、异步生命周期、UI 进度与恢复。

## 本轮修复

|范围|确认问题与处理|
|---|---|
|文档|DOCX 不规则表格填充可导致平方级内存膨胀：先用 Long 计算完整输出预算再生成；保留普通表格结果。|
|文档|DOCX 超链接、插入修订、内容容器里的可见文字漏读：按顺序读取允许的容器，排除删除修订和元数据。|
|文档|DOC 尾随 footnote/header 等 story 片段导致负区间：先验证原始 CP，再裁剪主正文；压缩正文越界不再静默返回截断内容。|
|聊天|毫秒 ID 与 id+1 可能覆盖另一条消息：全局持久高水位分配，加载时对齐数据库最大 ID，写入/编辑/删除均验证会话归属。|
|聊天|保存失败后重试丢失生成意图：待执行操作跟随同一持久化承诺，成功排空队列后恰好执行一次；停止/删除取消待执行意图。|
|聊天|Undo 删除与发送排队竞争可能丢失回答：发送前结算删除，生成等待后续排队写入完成。|
|聊天|首次加载失败永久卡住：加入去重加载重试、过期加载结果隔离及失败空闲会话回收。|
|聊天|页面退出后通知权限 launcher 已注销，延续回调可能崩溃：容忍已注销 launcher；延续回调异常显示为错误而非未处理异常。|
|网络|自定义 API-key 请求头可能跨域重定向泄漏：两种 HTTP client 均禁止自动重定向。|
|网络|发现模型能力时污染所有 API 配置：动态元数据按端点、协议、精确模型 ID 隔离，不改写内置规则；校验元数据类型/值。|
|网络|带 query 的 URL 被拼接错路径：统一 HttpUrl 路径/query 构造，保留 raw URL 与模型路径编码。|
|网络|o3/o4 等连接探测参数错误：使用兼容的 max_completion_tokens；Google/Anthropic 模型列表补齐分页并限制页数、检测重复游标。|
|UI|原文页滚动后直接退出会丢进度：单一存储所有者跟随当前页面；两种排版切换以切片/章节锚点对齐，不再复用不相容像素偏移。|
|UI|字体预览重复应用已生效字体缩放：去除已应用比例，再应用草稿比例，保留系统辅助字体缩放。|

## 上轮云端及仍待设备复核

[上轮 check](https://github.com/zhouran200-ux/bu/actions/runs/39942674737) 的 API33/35 都在 SourceIndexPreviewTest.pageSwitchesCarryReadingProgressInBothDirections 失败，目标 〔S：11-1〕 不可见，故 Preview 发布被绿灯门禁阻止。源码/JVM/Lint/APK 构建通过。完整日志下载受权限限制，结论来自公开 job/annotation。

本轮保留原显示断言，新增锚点/index/offset 诊断、非零偏移切换、原文页退出重开、系统返回和引用跳转覆盖。已修复可证明的像素映射与持久化问题，但原来零偏移设备断言的唯一根因尚未实机证明；不得将代码修复或测试编译称作设备问题已验证解决。

## 验证结果

- Debug/Release Kotlin 编译、Room KSP、baselineprofile 编译：通过。
- Debug/Release JVM：各 97 个测试类、621 用例，0 失败/错误/跳过；报告清点门禁通过。
- Debug/Release Lint：通过，无阻断错误；分别保留 30/31 个 warning 和 5 个 hint，未扩大 baseline 掩盖问题。主要为依赖版本建议、Compose API/性能提示、Locale、数据迁移规则等，不等于所有 warning 已修复。
- Android 测试代码编译：通过；没有在本地运行设备测试或打包 APK/AAB。
- Python 工具测试：130 项，6 项按现有条件跳过，其余通过。
- quick 静态检查全部通过：源码残留/重复 import/文案/版本/缓存/嵌套滚动/i18n/主题/设计门禁。
- 首轮 Debug Lint 曾在同时清理文件期间触发 IntelliJ PSI 内部异常；源码稳定后以 max-workers=2 重跑成功。

## 残留审计与边界

已删除旧 URL query helper，调用统一 URL 构造；已移除旧毫秒配对 ID 生成与旧全局模型元数据写入路径；无无效 import、静态残留候选。主/副安装包包名、发布工作流、数据库 schema 均未改变。

ChatSessionTest 当前 642 行，审查后保留：同一会话状态机的队列/取消/生成/删除测试共享确定性调度器；未为了行数拆散关联测试，后续独立状态机职责增加时再拆。

剩余限制：DOCX 输出预算不是峰值堆内存保证；嵌套表格和块级内容控件未泛化支持；动态模型能力仍仅运行期缓存；禁止重定向后需填写最终 API URL；同端点同模型不同账号能力未按账号细分；字体与动画手感/系统返回等需设备复核。原文/阅读页切换定位到同一切片或章节起点，非段内逐像素一致。

本轮不宣称全项目没有 bug。设备执行及本次提交云端结果留待下一轮复核；推送后不轮询。
