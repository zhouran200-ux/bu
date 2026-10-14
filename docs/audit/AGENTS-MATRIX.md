# AGENTS.md全范围审查矩阵

基线9c3df7b。本轮审查不是删除/重构任务，不执行AGENTS中的删除步骤或提交产品代码；但按其全范围清单进行功能正向与消费者反向追踪。历史迁移与测试不能因旧名称直接删除。

|范围|正向审查|反向审查与结论入口|
|---|---|---|
|Kotlin/Java|全部主源码Kotlin逐文件分模块阅读；glob未发现Java源码|[盘点](SOURCE-INVENTORY.md)与模块清单；生成I18n分开判断|
|Activity/Composable/页面|首次启动、设置、笔记本、聊天、来源、弹窗|[设置](SETTINGS-APP.md)、[聊天UI](CHAT-UI.md)、[视觉](VISUAL-FOUNDATION.md)|
|Repository/DAO/Service/Manager|DataStore/Room保存、网络执行、服务观察|数据报告与[网络/生成](NETWORK-CHAT.md)|
|Navigation/DeepLink/Intent|启动入口、分页、模型管理到设置再返回、通知停止|Manifest只有MainActivity入口和ChatGenerationService；旧入口与Intent消费者由反向报告列证据|
|State/Flow/Event/Callback|草稿/方案切换/请求返回/列表恢复/重组|跨方案迟到回调、取消loading、reload缺失；不是仅看变量命名|
|Room/SQLite/DataStore/SharedPreferences|迁移链、删除所有权、引用版本、配置键和阅读记忆|历史兼容字段与无业务消费者字段/表分开；不让schema导出代替实际升级库检查|
|XML/Drawable/String/Color/Theme|Manifest及8个res XML逐个读；颜色与主题/图标消费者检查|备份XML是明确全域exclude，不当作旧备份功能；PNG为启动器静态资源，不做二进制逐行宣称|
|Gradle/Dependencies|根/模块/版本目录/Wrapper/Windows测试配置|版本较旧不等于bug；依赖是否有实际consumer、baselineprofile链路单独核实|
|Proguard/R8|现有规则与相关反射JSON/font/生成类关联|配置报告与反向报告；本轮不据静态阅读声称Release APK真机无混淆错误|
|Notification/Worker/Background|ChatGenerationService启动、停止、超时、销毁与会话收尾|实际无旧Worker业务入口；后台/OEM/进程回收需设备验收|
|Analytics/Logging/FeatureFlag|错误反馈/日志去向、主题动画开关|AppScope只日志可能不足，按实际操作列故障风险，不因日志存在而称已处理|
|Unit/UI/Mock/Test|危险路径检查现有测试是否覆盖；数学/解析/源码契约区别|相关测试文件完整阅读见模块报告；没有把数量或字符串匹配当UI手势验收|
|Comment/TODO/Docs|旧交互/构建约定与当前产品行为对照|已历史覆盖声明与仍失效文案区别；零注释规则本身不算运行错误|
|Unused import/dead code|用途搜索+上下文+入口/调用反查|旧attachment字段/旧表/备份策略/迁移SQL区别；不擅自删公共实现|

## 正反向例子
- 功能→配置页获取模型→协程网络→返回共享state→确认当前方案：发现结果可能跨身份应用。
- 反向从模型列表/草稿状态→迟到回调→方案消费者：证实不是单纯UI显示偏好。
- 功能→启动→清理旧数据→标题/前缀识别→永久删除：误删风险。
- 反向从SQLite表/旧字段→实体/DAO/UI/迁移测试→没有消费者或仅兼容使用：决定是保留历史迁移还是未来末端清理。
- 功能→点击脚注→版本校验→重建索引→阅读块→高亮：版本安全已修，范围/重复扫描未完。
- 反向从startOffset/endOffset消费者→只比较起点→代码围栏/换行处理不同：不能将可点击误称精准溯源。

## 证据限制
本轮报告只能证明覆盖清单与静态条件，不宣称全部功能正常。真机手势、前台服务系统限制、帧率/内存、未知第三方协议和最低Android SQLite行为，需要后续场景验收。未修改app源码，未删除任何依赖、资源或旧表。
