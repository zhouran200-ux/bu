# 审查修复第一轮：数据安全

基于9c3df7b，按审计优先级先修数据保护，不同时混入网络/UI重构。

## 实际修复
- DatabaseFileGuard验证完整16字节SQLite头；未知、短文件、旧加密格式不移动/不删除，不自动创建替代库，抛出明确错误。侧文件和旧备份保持原位。
- MainActivity后台打开数据库并实际触发Room打开/迁移，成功前不挂载数据库消费者；失败显示保留资料提示，不空白伪装成功。不提供自动清空/卸载建议。
- 移除fallbackToDestructiveMigration，缺迁移路径时不自动丢掉数据。已有头正常但内部损坏库的SQLite系统处理/恢复仍需专门设备验收，不承诺修复损坏文件。
- LegacyPurge由前16字符指纹改为已知样例fixture的完整SHA-256。未被完整指纹证明的历史样例宁可保留，不能从前缀猜测整篇后删除。追加或修改后半段不再匹配。没有重新引入小说正文。
- NotebookPurge对文档、聊天、关联、笔记本删除用Room withTransaction；失败回滚。
- ChatSession等待旧写入被取消时恢复此前ready/error；ChatSessions解除该调用取得的删除登记，不回滚其他调用的资格。
- 删除入口除业务错误外也反馈数据库异常，CancellationException继续传播。

## 验证
针对4类JVM测试23项，零失败：文件守卫3、样例5、所有权5、会话10。Debug及Release Kotlin编译通过；新增2项设备事务测试仅compileDebugAndroidTestKotlin编译通过，尚未执行设备测试。30项Python工具契约通过，114个Kotlin文件源码卫生通过。不本地打包APK。

设备测试包含末步DELETE触发器故障注入，期望原文/聊天/关联/笔记本均回滚且可再次删除；共享资料删除一个笔记本后仍保留。

## 残留审计
全局检索旧discardUnreadableDatabase、SAMPLE_BODY_PREFIX、fallbackToDestructiveMigration、prepareDelete、withTransaction。生产旧删库函数与前缀常量及自动毁库fallback已移除；历史迁移、字体/附件目录清理等公共/兼容代码保留。无schema、Manifest、依赖变动。审查文档仍记录原基线问题，不是当前所有问题都已修好。

## 未完成
旧SQLite DROP COLUMN兼容迁移、旧任务/FTS末端清理、重生成保护、SSE三协议、API请求竞态、字体/手势、原文范围与长文性能仍待后续逐轮处理。新恢复导出/诊断界面尚未实现，错误页面先保留原数据避免破坏。

## 上轮云端
9c3df7b：check成功，https://github.com/zhouran200-ux/bu/actions/runs/38598196213；APK成功，https://github.com/zhouran200-ux/bu/actions/runs/38598196230。本机代理正在监听且请求成功后读取，未凭MCP限制猜测权限。此为上轮，不代表本轮云端通过。
