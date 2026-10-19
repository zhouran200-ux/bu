# 悬浮聊天输入框变更与残留审计

## 交互范围

- 保留既有思考强度、历史、对话设置、模型选择、发送/停止入口及各自回调，不合并模型与强度。
- 默认两行输入区和一行按钮；输入区按自然折行/换行增长到五行，即整体最多六行，之后在输入区内滚动。清空后恢复默认高度。
- 极小可用高度（横屏/分屏/键盘）优先保留按钮行，输入区在实际剩余空间内滚动，不强撑五行导致按钮被裁掉。

## 布局与状态所有权

- [FloatingChatLayout.kt](../../app/src/main/java/com/lucent/app/ui/FloatingChatLayout.kt) 只负责三层布局：正文、浮层输入框、悬浮控件。先测量输入框，再在同一次布局中传入实际底部占位，不使用 onSizeChanged 延后一帧维护副本状态。
- [ChatComposerGlass.kt](../../app/src/main/java/com/lucent/app/ui/ChatComposerGlass.kt) 使用独立 HazeState 采样正文层，输入框与控件不进入采样源。纯白主题也使用半透明材质，其他页面的玻璃样式不变；不支持模糊时使用可读性优先的半透明降级。
- [NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt) 保留 Session/Coordinator/列表状态所有权。列表铺满键盘调整后的区域，仅使用 contentPadding 提供尾部滚动空间，不把正文区域裁在输入框上缘。
- 键盘 padding 统一由外层消耗，导航栏 padding 由输入框布局消耗。返回底部/撤销提示使用实际占位，不再各自重复计算键盘与导航栏偏移。
- 视口锚点、快速滚动和重新生成高度预留扣除遮挡区域；原有用户滚动取消跟随、历史阅读不被强制拉到底、流式跟随生命周期保持不变。

## 残留审计

- 已删除本页旧的上下分区布局、列表底部圆角裁剪、24–130dp 固定输入高度限制及 112/118dp 悬浮控件固定偏移。
- 已全局核对 BottomGlassComposer、ChatDeleteUndoLayer、ChatScrollToBottomButton 调用及旧高度/偏移表达式，更新对应的源码契约测试边界；未保留双套输入框实现。
- 共用 ForegroundGlass 和全局背景采样仍由其他界面使用，保留不删；聊天新增采样仅供输入框使用，消息卡片仍禁用逐卡实时模糊。
- 清理受影响 Kotlin import；静态残留扫描覆盖源码、测试与配置。未新增资源、依赖、权限、持久化字段、导航、网络请求、后台任务、日志或分析埋点；这些领域没有本次迁移产生的废弃路径。
- 本轮开始时本地三处已有修改与远端 c31bee84 的内容完全一致，先保留安全 stash，再 fast-forward 保留远端三提交，未重写历史。

## 验证记录

- 主代码 Debug Kotlin 编译通过。
- 定向 JVM：ChatViewportAnchorTest、ChatRailGeometryTest、ChatScrollMathTest，共 12 项通过，0 跳过/失败。
- Python 工具/源码契约：121 项，115 项执行通过；6 项签名脚本测试因当前环境无可用 Bash 而跳过，不能视为执行通过。
- 新增 9 项 Android 设备测试：5 项输入区高度/自然折行/实际双向滚动/短视口，4 项悬浮布局几何/动态避让/触摸不穿透。保留既有 5 项输入框交互测试；设备测试不在本地执行，交既有 GitHub Actions API 33/35 矩阵。
- 最终 compileDebugAndroidTestKotlin 与 lintDebug 通过；现有测试规则 API 有弃用警告，Lint 仍有项目已有警告/提示，未宣称零警告。
- quick 10 项静态检查通过；最后新增布局测试后补跑代码卫生与 source audit 通过（248 个 Kotlin 文件、267 个源码/配置文件，0 错误/候选）。git diff --check 通过。
- 验证对应本报告与本轮提交中的完整源码范围；上游基线为 c31bee84，不复用上游绿灯冒充本次验证。
- 不本地打包 APK/AAB。未执行真机视觉验收，不把源码审查或测试编译称为设备运行通过；云端本轮结果推送后不等待、不轮询。
