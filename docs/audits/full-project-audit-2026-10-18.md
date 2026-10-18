# 全项目审计报告（正向审计 + 反向合规）

- 审计基线：提交 `9a3a9e8`（main，与 origin/main 同步，工作区干净）
- 依据：`AGENTS.md`（交付约定、源码结构与 UI 复杂度约束、强制残留审计规范）+ 项目既有工具链
- 方法：正向审计（规则 → 代码，找违反）+ 反向合规（代码/规则 → 检查手段，找无强制约束的盲区），两侧独立取证后交叉核对
- 结论边界：静态审计，不等于设备验证。性能、内存、真实端点兼容、动画手感、厂商 ROM 行为不在本报告覆盖范围

## 一、结论摘要

| 维度 | 结果 |
|---|---|
| 已有自动检查且当前通过 | 源码卫生、设计审计、Python 契约 63 项、版本一致性、设置缓存回写、嵌套滚动、Debug/Release 编译、JVM 单测、Lint |
| 明确违反 AGENTS.md 的代码事实 | 1 类结构性问题（页面层直连持久化，共 6 处入口）+ 1 类文案问题（155 处 UI 硬编码中文） |
| 规则无任何检查强制（反向盲区） | 5 条强制规则无自动检查，见第四节矩阵 |
| 检查强度与规则不一致 | 1 处：源码卫生上限 3000 行，规则要求 1200 行上限 |
| 配置矛盾/死配置 | 2 处：备份规则与 allowBackup 矛盾；values-night 缺少 theme 覆盖 |

## 二、正向审计（规则 → 代码）

### 2.1 已合规（有证据）

| 规则 | 结论 | 证据 |
|---|---|---|
| 禁止注释 | 合规 | `tools/code_hygiene_check.py` 全量扫描 199 个 Kotlin 文件：无注释、括号平衡 |
| 手写文件规模 | 合规但接近审查线 | 126 个 main Kotlin 文件：<100 行 75 个、100–300 行 39 个、300–500 行 9 个、500–800 行 3 个、>800 行 0 个 |
| 文件不得超 1200 行 | 合规 | 最大文件 `ui/NotebookChatTab.kt` 670 行 |
| 长文本解析不得在高频重组路径同步执行 | 合规 | `ui/Markdown.kt:62`、`ui/ChatRequestRunner.kt:77-78`、`ui/NotebookSourcesTab.kt:95,216` 均 `withContext(Dispatchers.Default)` |
| 不得阻塞主线程 | 合规 | main 源码 `runBlocking`/`Thread.sleep` 命中 0 处 |
| 版本号单一来源 | 合规 | `app/build.gradle.kts` 与 `LucentBuild.kt` 均为 3.1.2，`version_check.py` 通过 |
| 设置缓存回写 | 合规 | `settings_cache_check.py`：11 个缓存值均被 UI 读取且有写入方 |
| 历史迁移不得删除 | 合规 | `AppDatabase` version=33，`addMigrations` 保留 2→33 全链；schema 26–33 已入库跟踪；无 destructive fallback |
| 颜色不得硬编码 | 合规 | `design_audit.py --strict`：0 硬编码色、0 偏离调色板、0 小触摸目标，100/100 |
| 构建与发布安全 | 合规 | Gradle 9.8.1 固定 SHA-256；正式发布缺密钥直接失败；预览保留调试签名回退 |
| 仓库卫生 | 合规 | 无被跟踪构建产物、无 >512KB 大文件；.gitignore 覆盖 build/.gradle/.kotlin/local.properties/keystore |
| 无 TODO/FIXME/注释残留 | 合规 | 全量搜索仅 `ui/Haptics.kt:19,32` 两处 `@Suppress("DEPRECATION")`，为必要的旧 API 兼容 |

### 2.2 不合规（有代码事实）

**[P1] 页面 Composable 直接持有 Room 数据库与 DAO**

AGENTS.md 明确：「页面 Composable 不得直接混合 DAO/Repository 持久化」「业务及异步协调使用 ViewModel、Session 或项目既有状态所有者」。

| 位置 | 事实 |
|---|---|
| `ui/NotebookChatTab.kt:94` | `AppDatabase.getInstance(context)` 在 Composable 内获取 |
| `ui/NotebookChatTab.kt:301` | `db.chatDao().insert(...)` 直接写库 |
| `ui/NotebooksScreen.kt:76,166,183` | 页面内 `getInstance` + `notebookDao().insert/update` |
| `ui/NotebookDetailScreen.kt:64` | 页面内 `getInstance` |
| `ui/NotebookSourcesTab.kt:75` | 页面内 `getInstance` |

影响：页面同时承担布局与持久化所有权，事务边界、失败重试、取消语义都散落在 UI；这与台账里 R01/R10「DAO 上移」未完成项一致。建议抽到既有 Session/状态所有者，页面只消费。

**[P1] 用户可见文案大量硬编码，与既有 i18n 机制并存**

项目已有 `i18n/I18n.kt`（`S.*` 目录 207 条，实际引用 216 处），CI 还会校验生成文件与 `tools/i18n/catalog.py` 一致；但 UI 层仍有 **155 处中文字面量、分布在 26 个文件**（`ui/NotebookChatTab.kt` 22、`ui/settings/ApiSettingsPage.kt` 19、`ui/ChatHistorySheet.kt` 12…），另有 chat 层 21 处。

更明确的重叠证据：catalog 已有 `userAgentSaveButton = "保存"`、`resetTypographyDefaults = "恢复默认"`，而 `ui/ChatDialogueSettings.kt` 仍写死同名字面量。

影响：文案改动要改两处、易漂移；`localeFilters = ["zh"]` 只有中文，短期不影响显示，但机制形同半废弃。本次改动新增的「模型选择」「管理 API 配置」「对话记录」「对话设置」也属于此类，应一并收敛到 `S`。

## 三、配置与资源层问题

**[P2] 备份配置自相矛盾**：`AndroidManifest.xml:12` `allowBackup="false"`，但同处 `:13-14` 仍引用 `fullBackupContent="@xml/backup_rules"` 与 `dataExtractionRules="@xml/data_extraction_rules"`。备份已关闭，这两条规则是死配置；要么开启备份并保留规则，要么删掉引用与 `res/xml` 两个文件。

**[P2] 深色模式缺少主题覆盖**：`res/values-night` 只有 `colors.xml`，没有 `themes.xml`；`Theme.Lucent` 父主题固定为 `Theme.Material.Light.NoActionBar`。窗口背景走夜间色，但系统级基础主题仍是 Light，Compose 接管前的系统控件/过渡存在浅色残留风险。

**[P3] 字符串资源几乎为空**：`res/values/strings.xml` 只有 `app_name`，全部文案在 Kotlin 侧，与第二节 i18n 现状一致（不是新问题，但决定了 i18n 迁移成本）。

## 四、反向合规：规则 ↔ 检查手段覆盖矩阵

| AGENTS.md 强制规则 | 是否有自动检查 | 说明 |
|---|---|---|
| 禁止注释、括号平衡、文件大小 | 有（阻断） | `code_hygiene_check.py`，CI source-hygiene 执行 |
| 颜色/触摸目标设计规范 | 有（参考，不阻断） | `design_audit.py --strict` 仅本地；CI 中为参考项 |
| 文件 500–800 行必须审查拆分 | **无** | 卫生检查上限是 3000 行，规则要求 1200 行上限；两者不一致，等于没有检查 |
| 页面不得混 DAO/Repository/网络/Prompt/手势 | **无** | 仅靠人工台账；本次即在 6 处发现违反 |
| Route/Screen 职责分工 | **无** | 无任何工具或契约断言 |
| 功能删除/迁移后残留审计 | **无** | 最高优先级规范，但完全依赖人工；无旧符号扫描脚本 |
| 文案必须走 i18n（隐含于 i18n 机制） | **无** | 生成器只校验"生成结果与目录一致"，不校验"UI 是否用了 S" |
| 单测/编译/Lint 必须跑 | 有（阻断） | `check.yml` 的 android-jvm-check、android-device-check |
| 不在本地打包 APK | 部分 | `verify.py` 用正则拦截 assemble/package/bundle，但可绕过 |
| 依赖/版本更新 | **无** | 仅依赖 GitHub 的过期提示 |

结论：**规则里真正被机器强制的，只有"格式卫生 + 测试/编译 + 版本一致 + 缓存回写"这几类；职责边界、文案、残留审计这些最核心的架构规则，全部靠人工。**

## 五、本次实测的自动化基线

| 检查 | 结果 |
|---|---|
| `python -m unittest discover -s tools/tests` | 63 项通过 |
| `design_audit.py --strict` | 100/100，0 警告 |
| `code_hygiene_check.py` | 199 文件无问题 |
| `verify.py quick` | 6 项通过（注释/版本/缓存/嵌套滚动/扫描/设计） |
| `gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileReleaseKotlin :app:lintDebug` | BUILD SUCCESSFUL |
| 测试资产 | 66 个 JVM 单测文件 + 6 个插桩测试文件 |

## 六、建议优先级

1. **P1｜把页面里的 DAO 调用上移**（R01/R10 收尾）：先做 `NotebookChatTab:301` 与 `NotebooksScreen` 增删改，页面只调 Session/状态所有者；每步保留现有行为测试。
2. **P1｜新增"硬编码文案"检查并分期迁移**：先让检查只统计、不阻断（基线 155 处），再按文件清零；目录里已有的键优先替换（保存/恢复默认）。
3. **P2｜对齐检查强度**：`MAX_LINES` 从 3000 降到 1200（与规则一致），并对 500–800 行的 3 个文件输出"需审查"提示而非直接失败。
4. **P2｜补两条反向检查**：①禁止 ui 层出现 `AppDatabase`/`Dao()`；②旧符号残留扫描（把这次删掉的 `HandleOnlyDrawerProperties`/`brightHandle` 这类做成通用清单）。
5. **P2｜清理配置矛盾**：备份规则与 allowBackup 二选一；补 `values-night/themes.xml`。
6. **P3｜设计审计白名单加约束**：`PALETTE_FILES` 目前可容纳任意颜色常量（本次状态色放在 `AppThemeColors` 即被豁免），建议限制白名单只允许调色板/主题语义色，并禁止在别处新增。

## 七、未覆盖 / 需设备或人工确认

- 真机动画手感、手势冲突、视觉对齐与配色观感
- 长文峰值内存、增量解析性能、滚动帧耗时、多会话内存
- 真实第三方中转与各模型端点协议兼容、IME 与厂商 ROM 行为
- 本报告未修改任何源码；所有结论基于静态阅读与本地可执行检查，不代表设备无缺陷
