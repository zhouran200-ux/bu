# 最近修改清理审计与对话记录入口迁移

基线1032a28，回查6a1530e数据库保护、c0e27cd输入法锚定、350808f统计迁移及1032a28滚动条重构。按AGENTS顺序：识别依赖→多标识全局搜索→区分公共/旧专属→删旧回调/import→复搜→编译测试→本报告。

## 本轮入口
输入栏顺序：思考强度→对话记录→对话设置→方案。记录采用与设置一致36dp圆形浅底，19dp History线性图标、主题文字色及无障碍“对话记录”；复用原ChatHistorySheet和current message锚点，不新建历史副本。方案胶囊用剩余宽度省略长名称，左侧按钮组限宽，保留发送键空间。空会话也能打开原空态，键盘处理复用原非编辑弹窗策略。

滚动条删除onOpenHistory参数、latestHistory、点按pointerInput、onClick语义及旧文案。只保留纵拖与setProgress，不再隐藏导航入口。移除composer失效widthIn import，更新滚动条说明。

## 近期缺口修复
1. NotebookPurge prepare成功后切回IO之前取消的窗口：整个prepare/delete纳入guard finally，prepared在目标线程内置位，仅成功取得资格才finish；回滚用NonCancellable。不误解除另一调用的锁。3项确定调度测试覆盖该窗口、拒绝prepare和成功/异常完成。
2. regenerateAnswer先校验方案/model/key；无效时保留旧答案与统计。有效重生成清firstTokenMillis/generationMillis/cachedTokensKnown，避免无模型错误继承旧统计。补源码契约，非端到端UI测试。网络失败后保留旧版本答案机制不在本轮实现，不宣称重生成所有破坏性风险已消除。

## 全范围矩阵
|范围|检查与结果|
|---|---|
|Kotlin/Java/Composable|入口、父子回调、滚动手势、图标/import多标识复搜；无Java手写入口新增。旧averageItemSize/railExpanded/railDragging/chatScrollMetrics/scrollChatToFraction无生产残留。|
|Repository/Service/Manager|检查ChatSession/ChatSessions、删除保护、会话load/save；历史仍复用同一会话，不新建服务。|
|Navigation/Intent/State|历史入口仅composer，showHistorySheet/current anchor仍必需；系统返回与模型管理导航不改，无新Intent/Deep Link。|
|Room/SQLite/DataStore/SharedPreferences|schema31新增统计列/默认值/30→31注册和load/save一致，旧answerSeconds兼容字段保留；删除事务保留；不删历史迁移、不改schema。|
|XML/Drawable/Vector/String/Theme|History来自已有material-icons-extended，文字内联遵循现有中文；主题色复用，无新增XML/PNG/字体资源；输入栏圆角/顶部无箭头保留。|
|Manifest/Gradle/Proguard|检查现有服务声明、权限、Room测试资产、图标依赖、R8规则；本轮无变更，不为入口移动新增依赖/权限。|
|通知/Worker/后台|当前生成服务/停止回调保留；历史入口无后台任务；不删除onTaskRemoved等无关消费者。|
|日志/分析/FeatureFlag|未新增埋点/flag/日志；无旧历史入口flag。|
|测试/Mock/Fake|新增3项history/重生成源码契约和3项删除guard调度测试；保留rail/IME/stats测试、新数据库迁移设备测试；不将字符串测试称真机验收。|
|文档/TODO/FIXME|旧rail“点按打开记录”更新；历史audit按原SHA保留，不能当当前缺陷。无待删除专属资源/配置。|
|死代码/import|移除rail历史callback、tap detector、semantics onClick和未使用widthIn；原历史sheet、稳定key/阅读记忆是公共有效实现。|

## 验证
相关JVM测试：NotebookDeletionGuardTest3、ChatSessionTest12、ChatRailGeometryTest4、ChatViewportAnchorTest4、ChatStatsTest5，共28项通过；Debug及Release Kotlin编译通过。33项Python工具/契约通过，121个Kotlin文件卫生通过，嵌套滚动扫描0。没有全量重跑、没有clean、没有本地打包或设备测试。真机窄屏/大字下按钮布局、键盘打开历史、滚动手势仍待设备验收。

## 不误删与保留风险
未知消息高度估算、精确原文区间、旧SQLite DROP COLUMN兼容与历史FTS末端清理仍是已记录问题，不在本轮入口迁移里大改。数据库文件guard和完整样例摘要已检查，旧破坏性函数/前缀常量/fallback无生产命中；旧timing字段保留以展示历史数据。审计不保证所有设备手势完美，不称全部历史问题已清零。

## 上轮云端
1032a28 APK#101成功并上传lucent-release-apk，https://github.com/zhouran200-ux/bu/actions/runs/38726585088 。Checks公开页校验步骤成功。未下载验包，使用debug.keystore非正式分发签名。本轮云端待push后另轮复核。
