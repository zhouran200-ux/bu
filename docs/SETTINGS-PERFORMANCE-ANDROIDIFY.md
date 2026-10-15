# 设置滚动性能优化与Androidify交互研究

基线dc1a5b6。本轮不承诺恒定120FPS；120Hz帧预算约8.33ms，必须Release-like真机测帧时间/温度/系统刷新率。未连接用户设备，没有实测帧率。

## 实现
- 设置页局部LocalLiveGlassEnabled=false；普通设置卡及嵌套方案行/响应框不再各挂HazeEffect和阴影，保留薄tint、圆角与高光边缘。其他页面默认live=true，纯白不变。设置前景不再实时局部模糊，视觉会比前版轻薄但不完全等价；不改变背景/色板，也不整屏加blur洗白缝隙。
- 根设置原5张卡+底栏的6次effect调用，变为设置卡0次、仅既有底栏1次；API N+3+R个设置卡effect调用变为0。这里是静态调用数量，不宣称实际GPU pass数或帧率收益。
- 首页按钮仅更新currentScreen，由HomeTabs唯一LaunchedEffect驱动pager；移除MainActivity重复animateScrollToPage协程，连点时旧effect由Compose取消。
- 设置路由仅新页面180ms轻微alpha进入（0.85→1），Animatable只在graphicsLayer读取，不用AnimatedContent同时组合新旧整屏，不重写导航栈。系统关闭动画时立即结束。
- API modelChoices按列表缓存，连接结果pretty JSON按res缓存，避免无关重组重复排序与格式化。

## 研究依据（固定Androidify提交931cfdd682279f15bdc63a15420f2bda9a4ac712）
- [HomeScreenComponents](https://github.com/android/androidify/blob/931cfdd682279f15bdc63a15420f2bda9a4ac712/feature/home/src/main/java/com/android/developers/androidify/home/HomeScreenComponents.kt)：旋转State仅layer读取。
- [MainNavigation](https://github.com/android/androidify/blob/931cfdd682279f15bdc63a15420f2bda9a4ac712/app/src/main/java/com/android/developers/androidify/navigation/MainNavigation.kt)：单NavDisplay负责导航与Material淡入淡出/返回缩放。不移植整套Nav3，不复制强0.7缩放。
- [ScaleIndication](https://github.com/android/androidify/blob/931cfdd682279f15bdc63a15420f2bda9a4ac712/core/theme/src/main/java/com/android/developers/androidify/theme/components/ScaleIndication.kt)：DrawModifierNode处理Press/Release/Cancel，draw读Animatable，原实现按压放大1.04。当前Lucent已有layer读缩放，不重复搬同类动画。
- [EditScreenCompact](https://github.com/android/androidify/blob/931cfdd682279f15bdc63a15420f2bda9a4ac712/feature/creation/src/main/java/com/android/developers/androidify/creation/EditScreenCompact.kt)：hide完成再移除弹层。Lucent保留已有同策略，不照抄delay400。
- [PromptBenchmark](https://github.com/android/androidify/blob/931cfdd682279f15bdc63a15420f2bda9a4ac712/benchmark/src/main/kotlin/com/android/developers/androidify/benchmark/PromptBenchmark.kt)：FrameTiming/Memory等测量，不构成官方App恒120FPS保证。
- [LICENSE](https://github.com/android/androidify/blob/931cfdd682279f15bdc63a15420f2bda9a4ac712/LICENSE)为Apache2；本轮只借鉴模式，自行实现，没有复制源码/美术资产；若后续直接复用必须保留适用版权与许可。

## 测量/后续
同一设备设置系统120Hz，关闭省电与避免发热，用release包固定相同主题/背景/模型数，对根设置、API长列表、主题/背景长页各上下滚5次，快速切tab/进入返回/展开弹层，Perfetto FrameTimeline+Compose tracing检查UI/RenderThread/GPU及p95/p99 frame overrun。不能拿debug模拟器FPS判断真机120。

未全面lazy化长页，API模型列表无上限仍需后续分页/行级lazy拆分；未关背景动画、未改CYCLE动态主题更新，没用preferredDisplayMode强制锁屏幕。基线profile现有仅冷启动，不声称已覆盖所有交互。

## 清理与验证
去掉重复pager动画owner及tabScope；新Local仅设置树内有效，无新设置键/存储/Manifest/依赖/权限。保留旧frostedGlass调用兼容，无双effect与隐藏临时开关。现有全量JVM、Release编译及新增performance源码契约集中验证；未本地打包APK，未设备测量。

上轮dc1a5b6 check#41与APK#104均成功，不代表本轮。
