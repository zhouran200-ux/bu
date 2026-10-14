# 全功能测试与兼容性排查（阶段计划与覆盖矩阵）

日期：2026-10-14　基线提交：592453703453f3802617164c301fc3317892d3cb（main，CI check 与 Build APK only 均成功）

## 总目标

对全部功能、关联功能兼容性与界面/设备适配做一次系统排查：建立功能-风险-覆盖矩阵，补齐高价值测试缺口，执行不打包的本地验证与云端矩阵，修复有直接证据的缺陷，明确未验证边界，并推送结果。

## 阶段划分

- 阶段A（本轮）：功能清单与覆盖矩阵、本地基线（JVM 全量、Release 编译、Lint、androidTest 编译）。
- 阶段B：矩阵驱动的缺口测试补齐（纯 JVM 可测项），修复可复现缺陷，残留审计。
- 阶段C：适配与关联路径清单核对（低分辨率/大字体/系统深色浅色/输入法/返回手势），云端模拟器插桩结果复核；输出最终报告并推送。

## 功能域 → 覆盖现状

| 功能域 | 主要实现 | 已有 JVM 测试 | 未测但 JVM 可测 | 需设备验证 |
| --- | --- | --- | --- | --- |
| 笔记本列表/排序 | NotebooksScreen、ReorderDragState/ReorderSlots | ApiProfiles.moved/selectionAfterMove（方案重排共享逻辑） | ReorderSlots.landingIndex/reflowFor 纯几何逻辑 | 长按拖动手感、与边缘返回冲突 |
| 笔记本增删改 | NotebooksScreen、NotebookDao | NotebookIsolationTest、NotebookPurgeTest（纯逻辑部分） | — | 创建/重命名/删除 UI 流程 |
| 来源文档导入解析 | MarkdownDocumentParser、CompoundFile、DocTextExtractor、FontStore | MarkdownDocumentParserTest、DocTextExtractorTest | CompoundFile 边界（损坏头/超限链）、FontStore.classify | 真实文件选择器、大文档内存 |
| 对话生成/停止/重生成 | ChatSession(s)、LlmClient.chatStream、StreamUiScheduler | ChatSessionTest、LlmClientStreamTest、StreamCallHandleTest、StreamUiSchedulerAndParserTest、StreamUsageParserTest | ApiModels 模型目录一致性 | 真实供应商流式、通知、后台时限 |
| 前台服务保护 | ChatGenerationService | 无（JVM 无法模拟 Service） | — | FGS 启动拒绝、超时、任务移除 |
| 对话历史/撤销 | ChatHistorySheet、PendingChatDelete | ReadingPositionTest（视口恢复部分） | — | 长按弹窗、定位动画 |
| 阅读位置记忆 | ReadingPositionMemory、ModelPickerPosition | ReadingPositionTest、ModelPickerPositionTest | — | 字体缩放后行定位漂移 |
| API 方案/模型选择 | ApiProfile(s)、SettingsScreen、ApiSettingsPage | ApiProfileTest（11 项）、TestConnectionDisplayTest | ApiModels 目录与 ReasoningResolver 组合 | 真实连通性、模型获取 |
| 设置（主题/背景/字体） | SettingsRepository、ThemeModes、LucentFonts | PaletteLibraryTest、LucentTitlesTest | SettingsScrollMemory、SettingsCache.seed | 动态取色、字体缩放视觉 |
| 底部弹窗/输入法 | PopupIme、4 个 ModalBottomSheet | 源码契约（Python） | — | 键盘保持/返回层级（真机） |
| 气泡文本选区 | NotebookChatTab SelectionContainer | 源码契约（Python） | — | 长按选区、跨 Markdown 选择 |
| 导航/返回层级 | AppNavigation、BackClaim、notebookBackTarget | HomeNavigationTest、NotebookDetailTabsTest | — | 系统返回手势 |
| 数据库迁移 | AppDatabase MIGRATION_2_3..29_30 | AppDatabaseMigrationTest（androidTest，26 项，云端通过） | — | 旧版本升级实机 |
| i18n/版本/规范 | I18n、version_check 等 | tools/tests Python 24 项 + check.yml 静态脚本 | — | 多语言截断 |

## 风险最高的缺口（阶段B优先）

1. ~~ReorderSlots 落点/回流几何~~ 已补 `ReorderSlotsTest`（16 项）。
2. ~~CompoundFile/DocTextExtractor 异常输入~~ 已补 `CompoundFileRobustnessTest`（8 项），确认畸形输入只产生可捕获异常。
3. ~~ApiModels 与 ReasoningResolver 组合~~ **发现并修复真实缺陷**，见下节。
4. ~~SettingsCache.seed 与 SettingsRepository 键一致性~~ 已补 `SettingsCacheSeedTest`（3 项，含全字段 seed 与默认值一致性）。
5. ~~LlmClient 非流式 chat 的参数/错误路径~~ 更正：客户端**不存在**非流式 chat，只有 `chatStream`、`fetchModels`、`testConnection`。改为线路级验证 `chatStream` 请求体，已补 `LlmClientReasoningPayloadTest`（5 项）。
6. FontStore.classify 依赖 Context，未做 JVM 测试（真机/插桩项）。
7. ChatSessions.prepareDelete 与 UI 状态竞态已有部分测试。
8. MarkdownText 长文渲染块切分已测，跨块选择为设备项。

## 阶段C修复的缺陷（有代码证据）

阶段B 修复厂商识别顺序后暴露出一条新的连带问题：OpenAI 请求体分支只读取 `openAiEffort`，因此「OpenAI 兼容端点 + Claude 模型 + 思考强度」这一组合下用户选择会被**静默丢弃**（既不发 `reasoning_effort` 也不发厂商参数）。

修复：OpenAI 线路参数改用 `openAiEffort ?: claudeEffort` 作为 effort 兜底，其余分支不变；预算类模型继续只发 `max_completion_tokens`，两者互斥。

线路级回归（真实 Socket 捕获请求体）：Claude effort 组合发出 `reasoning_effort=high`；Claude 预算组合发出 `max_completion_tokens=16384` 且无 effort/reasoning；gpt-6 MAXIMUM 发出 `reasoning{mode=pro,effort=xhigh}`；AUTO 模式不发送任何推理参数；DeepSeek/Qwen 保持各自 effort 取值。

## 阶段B修复的缺陷（有代码证据）

`ModelCapabilityRegistry.findOrRegister` 的 `provider` 实参是协议 spec（openai/anthropic/google/custom），而推断链的第一个分支是 `prov.contains("openai")`，对所有 OpenAI 兼容端点恒为真，导致其后按模型名识别 Claude/Gemini/DeepSeek/Qwen 的分支不可达。

影响：用户通过 OpenAI 兼容代理或中转使用 `claude-3-5-sonnet`、`gemini-2.5-flash`、`deepseek-r1`、`qwen-max` 等模型时，会被错误推断为 OPENAI_EFFORT，向请求体写入 `reasoning_effort`/`reasoning`，而不会使用该厂商对应的参数形态（Claude budget/effort、Gemini thinkingLevel）。严格校验参数的服务端可能直接返回 400。

修复：把按模型名的厂商识别提到协议识别之前，协议分支保留为兜底（自定义/未知模型仍走 OPENAI_EFFORT，未注册的非推理模型仍走 NONE）。

回归：`openAiCompatibleProxyResolvesOtherVendorsByModelName`、`genericOpenAiModelsStillUseEffortMapping` 等 2 项新用例，原有 7 项推理测试不变。

## 兼容与适配清单（阶段C核对项，需设备/实机）

- 版本门槛：minSdk 28 / targetSdk 36；Lint `NewApi` 0 错误（本地 lintRelease 通过），后台服务 dataSync 在 Android 15+ 的时限处理已实现，实机时限行为未验证。
- 主题与配色：系统深/浅色、纯白主题、动态取色互斥逻辑有测试（PaletteLibraryTest）；实机取色与对比度未验证。
- 字体与排版：字号/行距/字距缩放设置已接线（settings_cache_check 通过）；大字号下阅读位置记忆的“同一行”精度未验证。
- 输入法：非编辑弹窗保持键盘、编辑弹窗保留焦点的源码契约已测；真机键盘遮挡与时序未验证。
- 手势：气泡长按选区、底部弹窗整体拖拽、列表长按拖动排序均为设备项。
- 存储：Room 迁移 2→30 在云端模拟器 26 项通过；旧版本实机升级未验证。
- 多语言：i18n 生成一致性由 check.yml 校验；长文案截断为设备项。

## 阶段D进展（可本地验证部分）

第二轮补齐（本轮）：

- **TXT 导入编码与 BOM**（`MarkdownDocumentParserTest`，7 项新增）：UTF-8/UTF-16LE/UTF-16BE BOM 正确剥离；非合法 UTF-8 的 GB18030 中文文本能正确还原（中文 TXT 常见的 GBK 场景）；空文件与纯空白返回空串。
- **DOCX 结构**（同文件）：缺少 `word/document.xml` 抛受控错误；损坏 XML 抛异常而非卡死；Heading1-6 与 `numPr` 列表转 Markdown；加粗/斜体/删除线组合；表格转 Markdown 表并转义单元格内的 `|`。
- **API 方案上限与命名**（`ApiProfileTest`，5 项新增）：序列化与解析都在 `MAX` 处截断；`nextDefaultName` 跳过已占用的编号；数组内非对象条目被跳过且结果名称非空且互不重复；模型列表去空白去重；空/非法 JSON 回退到默认方案。
- **输入清洗**（`TextSanitizationTest`，2 项）：粘贴产生的连续空行折叠为单个空行，其他空白与内容不变。

「获取模型」此前零覆盖，已补 `LlmClientFetchModelsTest`（10 项，真实 Socket 断言）：
- OpenAI/Anthropic 的 `data[]` 与 Google 的 `models[]` 两种响应形态，Google 前缀 `models/` 正确剥离；
- id 首尾空格、空 id、重复 id 的规范化与去重；
- 服务端广告的 `reasoning` 能力被注册进 `ModelCapabilityRegistry`（含 `supported/control/adaptive`）；
- 未知 control 字符串按端点协议兜底（OpenAI→EFFORT，Google→THINKING_LEVEL）；
- 无能力元数据的模型按名称/协议推断；
- HTTP 401 带上状态码与正文；非 JSON 响应返回失败而非空列表；空数组返回空列表；
- 端点路径：OpenAI 补 `/models`，raw 模式原样使用，Google 附加 `key=`。

已核实的实现细节（非缺陷，避免误判）：`ApiProfiles.parse` 对缺失名称使用**数组下标**生成「方案 N」，因此跳过非对象条目后编号会跳号（如「方案 4」），但不会产生重名，故按现状断言并加唯一性不变式。

### 残余风险（已记录，未改行为）

在 OpenAI 兼容端点上，**无能力元数据且名称无厂商线索**的模型会被自适应推断为 `OPENAI_EFFORT`（`isAdaptive=true`）。用户选择非 AUTO 档位时，请求体会带上 `reasoning_effort`；若该端点严格校验未知参数，可能返回 400。这是既有设计（非本轮引入），是否收紧需要真实端点证据，故只记录不改。

## 阶段完成情况

- **阶段A（完成）**：功能清单、覆盖矩阵、基线验证（Debug/Release 各 195 项→现 229 项，Release Lint 0 错误，androidTest 可编译，远端 CI 成功）。
- **阶段B（完成）**：修复代理场景推理参数误判；补重排几何 16 项、DOC 健壮性 8 项。
- **阶段C（完成）**：修正矩阵中对非流式请求的误判；修复 OpenAI 线路丢弃 Claude effort；补线路级请求体断言 5 项、配置种子一致性 3 项。
- **阶段D（本地部分完成）**：补「获取模型」线路级测试 10 项、文档导入编码与 DOCX 结构 7 项、方案上限与命名 5 项、输入清洗 2 项，并记录自适应推断的残余风险；设备项（真机键盘与长按手势、通知栏动作、前台服务时限、字体缩放下的阅读位置精度、旧库实机升级、厂商 ROM 差异）仍需实机验收。

最终本地结果：30 个测试类 / 253 项测试（Debug、Release 各一次）0 失败；Lint 0 错误 6 警告；Python 工具与契约测试 26 项通过；未打包 APK。

## 未验证边界（不做无证据声明）

- 真实供应商流式、通知栏动作、FGS 超时与任务移除、真机键盘/返回/长按手势、厂商 ROM 差异、旧数据库实机升级。
- 本地不打包 APK；设备矩阵由 GitHub Actions（API35 模拟器）与用户真机验收承担。
