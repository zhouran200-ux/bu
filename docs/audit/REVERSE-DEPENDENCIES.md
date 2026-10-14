# 反向依赖与已删除功能残留审计

基线：9c3df7b8002c429c487529b8195139c13bfe9c03。完整读取 [AGENTS.md](<../../AGENTS.md>) 与关键配置。本轮只读产品代码，仅新增本报告；未删除、修改产品代码，未本地打包，未提交推送。按“枚举→多标识搜索→上下文及消费者核实→分类→复查→报告”执行。清理步骤只给建议，不冒充已完成。

## 方法与边界

glob 枚举主Kotlin源码、Java、全部10个主资源、36个JVM测试、1个androidTest、docs、Manifest；生成BuildConfig与构建缓存不作手写入口。grep功能族 agent/agentTrace/cloud_agent_mode/harness/assistant、task/tasks/task_versions/subtasks/repeatRule/reminderEnabled/KIND_TASK、attachment/attachments/attachmentMime/attachmentData/attachmentName/attachmentList/attachment-preview、checklist/isChecklist、doodle/isDoodle、encrypt/_enc/SQLCipher/EncryptedSharedPreferences、backup/auto_backup_state/cloud_auto_backup，以及中文智能体/任务/附件/清单/涂鸦/加密/备份。另搜入口、Intent、状态、Worker、日志/FeatureFlag、TODO/FIXME等机制族；对字段、任务/FTS对象再定向搜索并read上下文。User-Agent、Gradle task、onTaskRemoved、dialogue不作为旧智能体/待办误报。

这是反向依赖审查，不把全局搜索称为全部源码逐行业务阅读。完整读取配置、实体、DAO、数据库迁移、SettingsRepository、AppNavigation、HomeTabs、ChatSessions、通知服务、LegacyPurge、TrashCleanup、NotebookPurge、ReadingPositionMemory、LucentIcons、I18n、全部文本资源；MainActivity读取90–479，来源/对话页面读取相关导入、偏好及持久化上下文，迁移测试读取fixture与退役字段相关测试，docs命中后读上下文。没有重新执行产品编译/设备测试，不用历史构建输出声称已验收。

## 结论

入口、Manifest、导航、资源、直接依赖未发现能重新进入已删功能的生产入口；但不能宣称全项目无残留。历史数据库任务/FTS对象缺少末端清理，当前实体仍有无业务消费者字段。旧迁移SQL、旧版本测试fixture与清理key名单具有兼容用途，不能为了零命中删除。

## AGENTS范围审计矩阵

| 范围 | 证据与反向消费者 | 结论/最低风险建议 |
|---|---|---|
| Kotlin / Java | glob主源码与Java；[Entities](<../../app/src/main/java/com/lucent/app/data/Entities.kt>)、[AppDatabase](<../../app/src/main/java/com/lucent/app/data/AppDatabase.kt>)及旧类名/字段族复查 | Java只见生成BuildConfig，无旧功能独立源码入口；实体与历史表见R1/R2。 |
| Activity / Fragment / Composable / ViewModel | [MainActivity:94–145](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L94-L145>)、[HomeTabs:48–68](<../../app/src/main/java/com/lucent/app/HomeTabs.kt#L48-L68>)、[页面分派:157–174](<../../app/src/main/java/com/lucent/app/HomeTabs.kt#L157-L174>)、[SettingsRoute](<../../app/src/main/java/com/lucent/app/ui/SettingsRoute.kt>) | 主页仅Notebooks/Settings，设置仅Root/Theme/Background/Font/Api，无旧页面路由；不要求为架构整齐全面ViewModel化。 |
| Repository / DataSource / Service / Manager | [SettingsRepository](<../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt>)、[ChatSessions](<../../app/src/main/java/com/lucent/app/chat/ChatSessions.kt>)、[ChatGenerationService](<../../app/src/main/java/com/lucent/app/chat/ChatGenerationService.kt>)、[TrashCleanup](<../../app/src/main/java/com/lucent/app/data/TrashCleanup.kt>) | 现有服务与仓库有消费者；ClipboardManager/VibratorManager不是旧管理器；启动清理兼容路径需保留。 |
| Navigation / Deep Link / Intent | [AppNavigation](<../../app/src/main/java/com/lucent/app/AppNavigation.kt>)、[Manifest](<../../app/src/main/AndroidManifest.xml>)、[通知Intent:98–115](<../../app/src/main/java/com/lucent/app/chat/ChatGenerationService.kt#L98-L115>)、[文档导入:68–94](<../../app/src/main/java/com/lucent/app/ui/NotebookSourcesTab.kt#L68-L94>) | 仅MAIN/LAUNCHER，无旧deep link/分享入口；通知打开主Activity/停止生成。OpenDocument仍用于TXT/DOC/DOCX和字体，不是聊天附件残留。 |
| State / Flow / LiveData / Event / Callback | [AppNavigation:19–89](<../../app/src/main/java/com/lucent/app/AppNavigation.kt#L19-L89>)、[ChatSessions:13–55](<../../app/src/main/java/com/lucent/app/chat/ChatSessions.kt#L13-L55>)、[设置Flow:43–103](<../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt#L43-L103>) | 未发现旧agent事件、任务提醒状态/回调；activeCount对应当前生成。生命周期详见[网络报告](<NETWORK-CHAT.md>)，不重复。 |
| Room / SQLite | [Entities](<../../app/src/main/java/com/lucent/app/data/Entities.kt>)、[Daos](<../../app/src/main/java/com/lucent/app/data/Daos.kt>)、[迁移末端:298–359](<../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L298-L359>)、[schema30](<../../app/schemas/com.lucent.app.data.AppDatabase/30.json>) | 历史task/FTS见R1，当前无消费者列见R2。当前四表导出不能证明历史安装也只有四表。 |
| DataStore / SharedPreferences | [移除名单:106–192](<../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt#L106-L192>)、[启动调用:134–137](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L134-L137>)、[对话偏好:236–279](<../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L236-L279>)、[阅读记忆:33–51](<../../app/src/main/java/com/lucent/app/ui/ReadingPositionMemory.kt#L33-L51>) | _enc/cloud/agent/backup名称是主动移除名单，不是启用设置。两个SharedPreferences用于当前对话/阅读。未知历史设备key未作清零保证。 |
| XML / Drawable / Vector / String / Color / Style / Theme | [strings](<../../app/src/main/res/values/strings.xml>)、[themes](<../../app/src/main/res/values/themes.xml>)、[日间颜色](<../../app/src/main/res/values/colors.xml>)、[夜间颜色](<../../app/src/main/res/values-night/colors.xml>)、[launcher](<../../app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml>)、[round launcher](<../../app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml>)、[LucentIcons](<../../app/src/main/java/com/lucent/app/ui/LucentIcons.kt>)、[I18n](<../../app/src/main/java/com/lucent/app/i18n/I18n.kt>) | 资源只含启动图/主题/名称/禁备份规则，无旧功能命名字串；两个PNG由launcher引用，未评价美术内容。round备用同图无实质运行问题，不凑数。 |
| Manifest | [app](<../../app/src/main/AndroidManifest.xml>)、[baselineprofile](<../../baselineprofile/src/main/AndroidManifest.xml>) | 仅MainActivity与ChatGenerationService；权限为当前网络/震动/前台生成/通知，测试Manifest为空。禁备份规则应保留。 |
| Gradle / Dependencies | [settings](<../../settings.gradle.kts>)、[root build](<../../build.gradle.kts>)、[app build](<../../app/build.gradle.kts>)、[versions](<../../gradle/libs.versions.toml>)、[properties](<../../gradle.properties>)、[baselineprofile build](<../../baselineprofile/build.gradle.kts>) | 未见SQLCipher/security-crypto/WorkManager或云盘备份/agent专属直接依赖。Room/DataStore/OkHttp/Compose/Haze有当前消费者；不能无证据删debug serialization-core。baselineprofile产物链见[视觉报告](<VISUAL-FOUNDATION.md>)。 |
| ProGuard / R8 | [全部规则](<../../app/proguard-rules.pro>)与app release配置 | 无旧功能类keep；Room/网络/Haze关联现存依赖。dontwarn收敛需实际R8验证，不能盲删TLS可选实现规则。 |
| Notification / Worker / Background Task | [服务](<../../app/src/main/java/com/lucent/app/chat/ChatGenerationService.kt>)、[消费者:41–53](<../../app/src/main/java/com/lucent/app/chat/ChatSessions.kt#L41-L53>)、Manifest与Worker依赖复查 | chat_generation/STOP_ALL是当前功能，未见旧任务提醒/备份Worker。无旧代码不保证已安装系统历史channel/work已清除；未知历史ID不能猜测删除。 |
| Analytics / Logging / Feature Flag | 日志命中核对[AppScope](<../../app/src/main/java/com/lucent/app/AppScope.kt>)、[启动失败](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L139-L144>)、[FTS警告](<../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L236-L238>)、[FGS失败](<../../app/src/main/java/com/lucent/app/chat/ChatGenerationService.kt#L31-L45>) | 未见旧agent/task埋点、Firebase/Crashlytics或旧功能开关；startup_logging_enabled仅移除名单。当前错误诊断日志不应误删。 |
| Unit / UI Test / Mock / Fake | glob测试后搜索；[迁移fixture](<../../app/src/androidTest/kotlin/com/lucent/app/data/AppDatabaseMigrationTest.kt#L31-L69>)、[agentTrace退场测试](<../../app/src/androidTest/kotlin/com/lucent/app/data/AppDatabaseMigrationTest.kt#L654-L669>)、[旧JSON兼容](<../../app/src/test/java/com/lucent/app/data/ApiProfileTest.kt#L49-L71>) | 未见引用已删页面/类的测试。旧字段/表测试验证升级，不是复活功能，应保留；缺历史额外对象全链清理断言见R1。 |
| Comment / TODO / FIXME / Documentation | docs与根Markdown多词搜索、tools/i18n字典与CI workflow复查，命中读[迁移说明](<../database-migration-tests.md>)、[中文说明](<../README.zh-CN.md>) | 未见要求使用旧附件/备份/加密入口说明；明确失效政策见R3。历史记录不能作为当前验收承诺。 |
| Unused import / dead code | 旧类名/字段/getter/资源/依赖反向核实，实体与DAO SELECT *对照 | 无旧专属类import命中；Room自动映射不等于字段有业务消费者，见R2。未新跑全量Lint，故不保证所有unused import为零。 |

## 实质发现

### R1 · P2：旧任务表与FTS对象缺少末端清理

**源码确认：** [11→12](<../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L115-L144>)创建task_versions；[17→18](<../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L196-L239>)创建notes_fts/tasks_fts及六个trigger。后续至30仅删除note_versions/note_embeddings/chat_conversations等，没有DROP tasks/task_versions/FTS。当前实体及DAO无任务/FTS消费者。

**触发及影响：** 旧安装含任务表，或设备支持FTS5且走过17→18时，可能继续保留旧任务内容、版本、索引与触发器。notes写入仍可能经过无消费者FTS trigger。外部内容列content与notes.body/tasks.notes不匹配，且迁移catch不回滚对象，可能成为当前文档插删故障点；[既有说明:10–12](<../database-migration-tests.md#L10-L12>)也记录这一历史风险。不声称每台设备均创建成功或必然失败。

**最低风险建议：** 新数据库版本末端按trigger→FTS虚表→已退役任务/版本表清理；保留早期迁移。核实旧itemKind历史取值后处理notebook_items的失效链接，避免误删NOTE。补含真实历史额外对象与对象缺失两类fixture，升级后断言旧对象消失、核心四表数据不变、notes插删正常。当前schema创建的fixture不能覆盖其未包含的历史额外对象。数据库主审应统一定级，勿重复计数。

### R2 · P3：当前实体仍携带无业务消费者附件及其他旧字段

[Note:20–32](<../../app/src/main/java/com/lucent/app/data/Entities.kt#L20-L32>)保留attachments及标签/归档/草稿/富文本等旧列；[ChatMessage:75–87](<../../app/src/main/java/com/lucent/app/data/Entities.kt#L75-L87>)保留四个附件字段和replyToId/reasoningBlocks/quotedRole/quotedText。字段全名二次搜索仅命中实体、迁移、迁移测试；[当前聊天load/save](<../../app/src/main/java/com/lucent/app/chat/ChatSessions.kt#L25-L39>)只映射正文/reasoningText/统计，[当前文档导入](<../../app/src/main/java/com/lucent/app/ui/NotebookSourcesTab.kt#L78-L87>)只写title/body。

**分类：** 这些列对现有版本30 schema兼容确有作用，直接删属性会破坏schema；但没有业务消费者，不能把Room SELECT *映射当成功能需求。旧附件若含大数据体，会随SELECT *加载并继续留存；体积未实测。反之，[附件目录删除](<../../app/src/main/java/com/lucent/app/data/TrashCleanup.kt#L8-L25>)与[移除key名单](<../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt#L106-L192>)被启动调用，是有效兼容清理，不是死代码。

**最低风险建议：** 不急于单改字段；下次数据库升级明确保留/退役列，采用低版本SQLite兼容的建新表→明确列复制→重建索引，保全正文/聊天/统计，不能篡改历史导出JSON或删测试。reasoningText、tokens、cachedTokens、answerSeconds、Notebook.manualOrder有当前消费者，不误删。

### R3 · P3：迁移文档残留旧验证政策

[迁移说明:14](<../database-migration-tests.md#L14>)称Android编译/执行只能GitHub Actions而非本地Java/Gradle，与[AGENTS:5–9](<../../AGENTS.md#L5-L9>)允许本地编译/单测/Lint、仅禁本地打包及依赖打包设备测试矛盾。后续开发照此执行会无谓跳过允许的针对性验证，非产品运行缺陷。最低建议只更新该句；保留有效历史schema缺失和FTS风险说明。

## 高风险交叉项（与数据库主审合并）

- [25→26/26→27:298–319](<../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L298-L319>)用ALTER TABLE DROP COLUMN并吞异常；minSdk28不等于所有系统SQLite支持该语法。旧SQLite可能保留checklist/doodle/agentTrace导致Room schema不匹配。最低建议兼容建表迁移并在旧API验证；本报告未执行设备，不把catch视为兼容成功。[26→27测试](<../../app/src/androidTest/kotlin/com/lucent/app/data/AppDatabaseMigrationTest.kt#L654-L669>)不等于25→最终全链验证。
- [打开前检查:378–410](<../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L378-L410>)把非明文头数据库删除，再删除wal/shm、加密中间与锁定文件。这是加密退场的破坏性策略，不是仍启用加密库；旧加密/异常短头库可能直接丢失，没有隔离/确认/恢复。建议先保全隔离并明确提示，而非把用户旧数据作为无用残留清掉。由数据库风险项统一说明。

## 明确保留与不列为问题

[backup_rules](<../../app/src/main/res/xml/backup_rules.xml>)与[data_extraction_rules](<../../app/src/main/res/xml/data_extraction_rules.xml>)全域exclude，配合[allowBackup=false](<../../app/src/main/AndroidManifest.xml#L12-L14>)是当前禁备份/迁移策略，删掉反可能放宽数据迁移。历史task/checklist/doodle/agentTrace迁移及测试需保留，修末端清理而非砍升级链。User-Agent与当前生成任务不能误判。公共依赖、日志与短工具函数不因名称相似而删除；无证据不要求企业式重构。

## 最小后续验收

1. 新装及旧版升级：主页/设置/通知没有旧入口，文档与字体导入仍正常。
2. 带tasks/task_versions/FTS/triggers及缺少这些对象的数据库分别升级：退役对象被清理，核心数据完整，notes插删正常。
3. 最低API上的25→最终与26→最终：不因DROP COLUMN留下多余列/Room失败。
4. 非空附件/旧元数据fixture收敛列：正文、聊天与统计不丢，历史schema不篡改。
5. 旧加密/异常头文件启动：原文件保全，恢复/重建由明确策略决定，不静默删除。
6. 旧DataStore key类型不同及重复清理：现用设置保留，清理可复跑。

上述真实升级场景未完成前，结论是“入口收敛，但数据库清理/兼容仍有风险”，不是“功能删除全部完成”。
