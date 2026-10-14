# 开发标准与检查工具审查

基线9c3df7b。本轮只审查，不修改产品代码。完整读取code_hygiene_check、compose_scroll_check、design_audit、verify、test_inventory、test_report_summary、release_notes_check、version_check、settings_cache_check、i18n/gen_i18n和catalog，以及模型弹窗契约。

## 应保留的实用标准
- 不本地打包APK，相关单测/增量编译先行，云端矩阵单独报告。
- test_inventory验证XML存在且有实际非跳过测试，比“Gradle退出0”更可靠。
- 设置缓存回写扫描、嵌套滚动检查适合作低成本提醒，但不能证明全部状态和手势正确。
- 序号、版本一致检查适合个人工程，不要求引入企业级检查体系。

## 实质建议
### P2：手势测试大多证明字符串存在，不能证明手势效果
[模型契约](../../tools/tests/test_picker_contract.py#L74-L87)检查sheetGesturesEnabled=false、translationY、hide和72dp矩形，均只验证源码片段；没有真实触摸事件、取消、回弹与列表滚动测试。[绘图评分](../../tools/design_audit.py#L70-L84)也用附近四行及size字面值评分，不了解父布局和Compose触摸区域。
最小建议：保留契约用于防止误删配置；为确认关闭、取消关闭、横杠下拉、列表顶部下拉分别补少量Compose设备测试。100分/零嵌套告警不得称App验收通过。

### P3：零注释与3000行硬上限不等于代码质量
[卫生检查](../../tools/code_hygiene_check.py#L84-L85)禁止所有源码注释，最大行数阈值见[上限](../../tools/code_hygiene_check.py#L7-L9)。对个人工程，不应为了凑行数机械拆文件；但数据库迁移、协议例外、偏移区间和手势关闭顺序属于容易忘掉的“为什么”。现可继续把这些决策写在关联文档；以后若放宽规则，允许极少数理由注释，而不是强制大量注释。无注释本身不列运行缺陷。

### P3：摘要工具和判定工具需要分别命名，不混用
[test_report_summary](../../tools/test_report_summary.py#L19-L20)跳过损坏XML且最后返回0；适合作摘要，不适合作成功判据。实际失败判断交给[test_inventory](../../tools/test_inventory.py#L13-L34)及Gradle。建议明确文档和CI职责，避免用户读到空摘要却认为通过。

## 本轮静态检查
30项Python工具/契约测试通过；111个Kotlin文件卫生检查通过；设置缓存11读/11写检查通过；营销版本与常量同为3.1.2。没有为只读审查重跑整套Gradle或APK。结果说明工具所覆盖条件通过，不说明数据删除、网络异常、界面手势或后台生存已验收。

## 不列为待修问题
仅中文字符串、生成I18n类的大小、所有短函数没抽接口、没有全面ViewModel化、个人Preview用debug签名（明确非正式分发即可）。release_notes的死条件和旧输出文案影响有限，不值得本轮专门改动。
