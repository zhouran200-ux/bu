# 数据层审查报告（基线 9c3df7b8002c429c487529b8195139c13bfe9c03）

> 完整读取 data 目录 20 个 Kotlin 文件及相关数据/引用测试，并沿首次启动、导入、长文、删除、后台恢复与数据库升级调用路径审查。未修改源码。

## 结论

源码确认 5 个实质问题：数据库启动误删（P0）、样例文档误删（P0）、笔记本删除非原子（P1）、长文主线程同步扫描（P1）、旧设备迁移失败风险（P1）。另列标签/阅读范围实现缺陷，及已有文档声明的引用缺口。未报告未经测量的耗时数字。

## 源码确认问题

### P0：启动删除未知格式数据库及伴随文件

- 文件：[AppDatabase.kt](../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L378-L410)
- 触发：首次 getInstance 时 lucent.db 存在，但前 16 字节不是 SQLite format 3（损坏、加密库、加密中间态或未知格式）。
- 证据与影响：discardUnreadableDatabase 直接删除主库、-wal、-shm、.encrypting、.pre-encrypt 和 locked-*；无备份、失败反馈或返回值检查。个人历史笔记、聊天、笔记本可能永久丢失。
- 最小修复：未知格式先原子改名隔离并提示恢复；仅由明确的加密/恢复流程决定清理。

### P0：启动清理会误删用户改过的预置文档

- 文件：[LegacyPurge.kt](../../app/src/main/java/com/lucent/app/data/LegacyPurge.kt#L7-L20)、[TrashCleanup.kt](../../app/src/main/java/com/lucent/app/data/TrashCleanup.kt#L11-L20)
- 触发：标题仍为“用户预置文档”，正文前 16 字符仍匹配内置 SHA-256；用户只改后半段或追加内容即可触发。MainActivity 每次启动调用清理。
- 证据与影响：isSampleDocument 只摘要前缀，release 删除关联并永久删除 note，无回收站/确认/撤销。
- 最小修复：使用一次性系统记录/安装标识，只清理从未编辑的原始行；至少校验完整正文摘要并保留恢复副本。

### P1：笔记本删除步骤非事务，异常时半删

- 文件：[NotebookPurge.kt](../../app/src/main/java/com/lucent/app/data/NotebookPurge.kt#L31-L48)
- 触发：删除独占笔记成功后，清理聊天、关联项或 notebook 行时异常/进程中断。
- 证据与影响：逐个永久删除独占 note，然后才清理 chat、items、notebook；无 Room transaction/删除日志，失败会丢内容但残留笔记本或关联。
- 最小修复：数据库操作用 withTransaction 一次提交，或先标记待删、全部成功后硬删；失败保留可恢复状态。聊天会话删除前置竞态由其他范围审查。

### P1：长文提示词与引用定位同步扫描正文

- 文件：[NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L355-L370)、[SourceTagging.kt](../../app/src/main/java/com/lucent/app/data/SourceTagging.kt#L32-L55)、[SourceLocator.kt](../../app/src/main/java/com/lucent/app/ui/SourceLocator.kt#L16-L18)
- 触发：资料正文较长，发送问题或点击引用。
- 证据与影响：askModel 在 Compose 事件路径同步 buildString，并对每份 note 调用 tag；点击又重新 tag。tag 同步 split/章节识别/分段扫描全文，无 IO/Default 切换或缓存。代码确认主线程同步全文路径；不声称具体耗时。
- 最小修复：按 noteId+revision 在 Default/IO 预构建缓存索引；提示词构建后台化；点击只读索引。

### P1：25→26/26→27 DROP COLUMN 在 API 28/29 设备上可能升级失败

- 文件：[AppDatabase.kt](../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L298-L318)、[app/build.gradle.kts](../../app/build.gradle.kts#L20-L27)
- 触发：已有 v25 数据库在 Android API 28/29 等 SQLite 低于 3.35（DROP COLUMN 于 SQLite 3.35 引入）设备上升级。
- 证据与影响：两段 DROP COLUMN 被 try/catch 吞掉后仍宣称迁移完成；旧列可能保留，随后 Room schema 校验可能失败。minSdk=28，是实际支持设备路径。fallbackToDestructiveMigration 不能修复已执行但 schema 不符的迁移。
- 最小修复：重建表迁移（create/copy/drop/rename），保留数据；不吞异常；增加 API28/29 插桩迁移。

## 标签/阅读范围缺陷（源码确认）

- **代码围栏未识别：** [SourceTagging.kt](../../app/src/main/java/com/lucent/app/data/SourceTagging.kt#L67-L100) 对每行直接 titleOf；围栏内“第一章/## 标题”会生成章节并改变 S 编号。应维护 fence 状态。
- **MAX 900 实际可到约 1800：** [SourceTagging.kt](../../app/src/main/java/com/lucent/app/data/SourceTagging.kt#L137-L186) 的 splitLongParagraph 以 MAX*2 为硬切断，超出名义上限。应统一硬上限或升级规则/索引版本。
- **trim/CRLF 导致 offset 漂移：** [SourceTagging.kt](../../app/src/main/java/com/lucent/app/data/SourceTagging.kt#L103-L185) 用 trim 后文本 cursor 加原始 start；CRLF 和首尾空白使后半片不再对应 body。应同时维护原文区间。
- **人工标签未校验复用：** [SourceTagging.kt](../../app/src/main/java/com/lucent/app/data/SourceTagging.kt#L32-L55) 无条件添加 CH/S，已有标签可能重复/歧义。应校验、复用合法标签，转义非法标签。
- **定位只取起点块：** [SourceLocator.kt](../../app/src/main/java/com/lucent/app/ui/SourceLocator.kt#L7-L18) 只找最后一个 startOffset≤slice.startOffset，不检查 endOffset；跨块引用只高亮一个块，漂移时可能高亮相邻块。应按两个半开区间求交集。

## 已知引用缺口（已有文档声明，非本轮新增）

- [SOURCE-CITATION-RELIABILITY.md](../../docs/SOURCE-CITATION-RELIABILITY.md#L15-L32) 已明确阶段二/三待做：统一范围扫描、跨块完整高亮、缓存索引、代码围栏 token 解析、无效态外观与无障碍。版本摘要与过期拒跳已修，不重复报告旧 hash 问题。

## 逐文件覆盖清单

以下 data 文件均已逐文件完整读取：

- [ApiProfile.kt](../../app/src/main/java/com/lucent/app/data/ApiProfile.kt)：已审，未发现重要问题。
- [AppDatabase.kt](../../app/src/main/java/com/lucent/app/data/AppDatabase.kt)：P0 启动未知库删除；P1 旧设备迁移。
- [CompoundFile.kt](../../app/src/main/java/com/lucent/app/data/CompoundFile.kt)：已审，坏链有边界保护。
- [Daos.kt](../../app/src/main/java/com/lucent/app/data/Daos.kt)：已审，未发现重要问题。
- [DocTextExtractor.kt](../../app/src/main/java/com/lucent/app/data/DocTextExtractor.kt)：已审，坏 DOC 受控失败。
- [DocumentHeadings.kt](../../app/src/main/java/com/lucent/app/data/DocumentHeadings.kt)：已审，未发现重要问题。
- [Entities.kt](../../app/src/main/java/com/lucent/app/data/Entities.kt)：已审，未发现重要问题。
- [FontStore.kt](../../app/src/main/java/com/lucent/app/data/FontStore.kt)：已审，后台导入、坏字体有反馈。
- [LegacyPurge.kt](../../app/src/main/java/com/lucent/app/data/LegacyPurge.kt)：P0 样例文档误删。
- [MarkdownDocumentParser.kt](../../app/src/main/java/com/lucent/app/data/MarkdownDocumentParser.kt)：已审，导入失败返回 Result。
- [NotebookPurge.kt](../../app/src/main/java/com/lucent/app/data/NotebookPurge.kt)：P1 非事务删除。
- [PlainTextMarkdown.kt](../../app/src/main/java/com/lucent/app/data/PlainTextMarkdown.kt)：已审，换行/章节有测试。
- [PlatformFont.kt](../../app/src/main/java/com/lucent/app/data/PlatformFont.kt)：已审，未发现重要问题。
- [ReasoningThreeLayer.kt](../../app/src/main/java/com/lucent/app/data/ReasoningThreeLayer.kt)：已审，AUTO、fallback、provider/model 推断有测试，未发现重要问题。
- [SettingsCache.kt](../../app/src/main/java/com/lucent/app/data/SettingsCache.kt)：已审，未发现重要问题。
- [SettingsRepository.kt](../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt)：已审，默认值、API profile 上限有测试。
- [SourceCitations.kt](../../app/src/main/java/com/lucent/app/data/SourceCitations.kt)：已审，非法/过期引用拒跳有测试。
- [SourceTagging.kt](../../app/src/main/java/com/lucent/app/data/SourceTagging.kt)：见范围缺陷。
- [TrashCleanup.kt](../../app/src/main/java/com/lucent/app/data/TrashCleanup.kt)：P0 启动清理调用链。

### 相关测试覆盖

- [AppDatabaseMigrationTest.kt](../../app/src/androidTest/kotlin/com/lucent/app/data/AppDatabaseMigrationTest.kt)：逐文件读取，覆盖 2→30 迁移；未覆盖 API28/29 原生 SQLite。
- [LegacyPurgeTest.kt](../../app/src/test/java/com/lucent/app/data/LegacyPurgeTest.kt)：覆盖前缀变化，但未覆盖后缀编辑仍匹配。
- [NotebookPurgeTest.kt](../../app/src/test/java/com/lucent/app/data/NotebookPurgeTest.kt)：覆盖独占/共享，未覆盖事务异常。
- [SourceTaggingTest.kt](../../app/src/test/java/com/lucent/app/data/SourceTaggingTest.kt)：覆盖章节、切片、长句、偏移、revision，但缺围栏/CRLF/人工标签。
- [SourceCitationsTest.kt](../../app/src/test/java/com/lucent/app/data/SourceCitationsTest.kt)：覆盖完整、简写、过期、非法引用。
- [ApiProfileTest.kt](../../app/src/test/java/com/lucent/app/data/ApiProfileTest.kt)、[CompoundFileRobustnessTest.kt](../../app/src/test/java/com/lucent/app/data/CompoundFileRobustnessTest.kt)、[DocTextExtractorTest.kt](../../app/src/test/java/com/lucent/app/data/DocTextExtractorTest.kt)、[MarkdownDocumentParserTest.kt](../../app/src/test/java/com/lucent/app/data/MarkdownDocumentParserTest.kt)、[PlainTextMarkdownTest.kt](../../app/src/test/java/com/lucent/app/data/PlainTextMarkdownTest.kt)、[ReasoningThreeLayerTest.kt](../../app/src/test/java/com/lucent/app/data/ReasoningThreeLayerTest.kt)、[SampleNovelConversionTest.kt](../../app/src/test/java/com/lucent/app/data/SampleNovelConversionTest.kt)、[SettingsCacheSeedTest.kt](../../app/src/test/java/com/lucent/app/data/SettingsCacheSeedTest.kt)、[NotebookIsolationTest.kt](../../app/src/test/java/com/lucent/app/data/NotebookIsolationTest.kt)：均已逐文件审阅，未发现额外重要问题。

## 范围内验收场景

1. 首次启动/空配置：DataStore 异常回退、空资料聊天、无模型配置均有预期提示。
2. 导入：TXT 编码、DOC/DOCX、坏文件、取消；字体坏输入/满槽/取消。
3. 长文：转换、阅读、发送、引用定位、切页和后台时 UI 可操作，取消不回写旧页面。
4. 连续点击：重复发送/重复引用/删除撤销不抢旧状态。
5. 失败恢复：网络失败、停止流式、数据库删除异常、进程杀死后数据可恢复且有提示。
6. 删除修改：编辑预置文档后重启不误删；共享 note 不误删；独占 note 与聊天删除原子完成。
7. 升级：API28/29 与新系统分别 v25→v30，数据完整且 Room 可打开。
8. 引用边界：代码围栏、人工标签、CRLF、超长单句、跨块切片、旧版本引用分别验证。

## 正向功能→实现→失败路径

- 数据库/笔记：Room AppDatabase + DAO；失败路径为迁移校验、未知库头处理、删除异常。
- 设置/API：SettingsRepository DataStore + SettingsCache + ApiProfiles；空/坏 JSON 回默认，未发现重要缺陷。
- 文档导入：MarkdownDocumentParser → PlainTextMarkdown/DocTextExtractor/CompoundFile；调用在 AppScope.io，失败 Result/onFailure 有反馈。
- 字体：PlatformFont/FontStore 临时文件和槽位上限；设置页 Dispatchers.IO，坏字体/满槽有提示。
- 引用：SourceTagging → NotebookChatTab prompt → SourceCitations/SourceLocator；版本过期拒跳已修，范围缺陷见上。
- 删除：NotebookPurge/TrashCleanup/LegacyPurge；独占/共享有测试，但永久删除与非事务造成风险。
- 推理：ReasoningThreeLayer 按 provider/model 映射；AUTO、fallback、代理模型有测试。

## 反向字段/键/依赖/资源/旧标识审计

- 实体字段与 Room schema 30、迁移 2→30、DAO 查询逐一核对；历史字段不因迁移保留而视为垃圾。
- SettingsKeys、DataStore consumers、SettingsCache、API profile JSON 字段逐一核对；未发现未消费键造成的个人风险。
- fonts.json、fonts 目录、临时 .tmp、font id 与 resolver/设置页调用核对；未发现重要残留。
- REF/S/CH、revisionOf、SourceTagging/SourceLocator/Markdown 调用与测试/文档交叉核对；旧 hash 仅兼容显示，过期拒跳已修。
- 迁移、DAO、Room、DataStore、JSON、XML/ZIP/OLE 依赖均有调用或测试；未因企业架构、命名或行数报错。

## 已排除疑点

- 未把无外键、无级联、无企业级架构直接当个人缺陷。
- 未编造性能测量，只报告主线程同步全文调用链。
- 未把已知引用交互缺口或已修复版本校验重复报告为新问题。
- 未重复 ChatSessions 删除前置竞态；该项由其他范围审查。
- 未发现 FontStore、DOC/DOCX、ApiProfiles、SettingsCache、ReasoningThreeLayer 中另有明确数据风险。