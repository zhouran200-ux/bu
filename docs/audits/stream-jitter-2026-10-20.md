# 流式输出与滑动胶囊闪跳排查

## 范围与结论边界

本轮基线为 829445c71973f87972231ba83e474fb9a300a61f。排查覆盖思维链、正文 Markdown、自动跟随、重新生成预留、触摸接管和胶囊拖动。没有修改网络协议、实际模型文本、数据库、主题配色或胶囊外观。

以下是可由代码和回归测试验证的风险路径，不声称已在用户设备录屏中逐一重现，也不把编译或纯算法测试视为真机视觉验收。

## 已修复路径

### 1. 收到文字与完成排版不是同一个时刻

旧逻辑根据正文/思维字数变化，等待固定 80ms 后 requestScrollToItem(footer)。正文由异步 Markdown 解析发布，固定延时无法保证实际高度已落地；强制锚定 footer 还会重置 LazyList 的 item 动画/缓存窗口。初次发送另有一个 100ms 后 animateScrollToItem 的滚动写入者。

替换为 [ChatTailFollow.kt](../../app/src/main/java/com/lucent/app/ui/ChatTailFollow.kt)：观察实际列表布局及滚动状态，帧前合并更新，执行前重新读取布局与跟随许可，按真实尾部溢出量向前 scrollBy。不再因字数不变漏掉迟到布局，也不为收缩主动反向定位。未测量到尾项时逐个可读视口向前，用户可以随时接管。

[NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt) 在正文按下时立即停止跟随，不等越过滑动阈值；胶囊按下同样停止。[ChatScrollOwnership.kt](../../app/src/main/java/com/lucent/app/ui/ChatScrollOwnership.kt) 只允许真实列表手势完成恢复跟随，胶囊接管会使旧 fling 的迟到结束无效。历史阅读不会因新 token 无条件拉回底部。

删除旧字数驱动调度器及其专属测试，删除初次发送的延时滚动和失效 CoroutineScope 注入。保留用户明确点返底/历史跳转、键盘视口锚点和重新生成高度预留等不同职责路径。

### 2. 长思维完成时突然限高

旧 ThinkingLane 在 streaming 时不限高，完成后立即改成 220dp 内滚动，并移除 pulse 占位。修复为当前已展开的实时思维在完成后保持高度与展开状态，不自动收缩。只有用户主动收起再展开才恢复历史 220dp 内滚动。该实时展开保留位使用 rememberSaveable，原有阅读状态的持久化仍由原组件负责。

### 3. Markdown 分块随网络切片变化

旧增量 parser 把末尾尚未终止的空行/代码围栏当作闭合点，同时把 CRLF 按一个字符计算，并可能丢失跨切片空行。修复按原始 LF/CRLF/CR 行结束扫描，在完整空白行序列起点封存；未终止尾行继续开放。保留闭合块对象复用。

回归使用逐字符、逐换行和每一种两段切分，与全量 parseBlocks 对照，覆盖重复空行、closing fence 继续追加、CRLF 拆开、替换和清空。

### 4. 长引用先显示原始元数据再缩成徽标

[StreamingMarkdownTail.kt](../../app/src/main/java/com/lucent/app/ui/StreamingMarkdownTail.kt) 仅对流式展示副本暂缓最后一个仍可能合法的未闭合 REF。完整标记一次进入徽标渲染；非法字符、溢出来源 ID、代码块/代码片段、过长候选不强行隐藏。Markdown 的解析 key 包含 streaming，停止、异常或正常完成时，即使 text 不变也会放回未闭合原文。

存储、复制与最终正文仍使用原始文本；不删除模型内容。普通 Markdown 格式闭合引起的合理排版变化仍可能存在，本轮不通过禁止 Markdown 格式化来掩盖它。

### 5. 胶囊拖动不一致

- 原先计算使用冻结行程、绘制使用实时行程，视口变化时按住的胶囊会移动；现在计算与绘制共用拖动快照，仅在视口边界必要时截断。
- 原先越过 slop 才暂停跟随，按住期间仍可能被自动跟随或淡出移走；现在按下即接管、停止旧滚动且保持显示，越过 slop 建立起点，后续真实位移才 seek。
- 原先把估算 fraction 反向转换成位置，首帧零位移也可能 seek 到另一处；现在从真实 firstVisible index/offset 加位移增量映射。
- 不再丢弃拖动期间可见项尺寸采样；采样持续更新而当前拖动映射仍冻结。列表 key 结构改变或失活时安全取消，finally 清理状态。
- 保留原 28×52 热区、22×46 外观、140ms 透明度动画、650ms 自动隐藏及无障碍首尾定位；未把整根轨道变成阻挡正文触摸的区域。

## 状态与生命周期

- ChatSession 继续拥有网络流、累计正文/思维和持久化；本轮不新增第二份网络状态。
- Coordinator 保持 followStream、重新生成预留及发送等业务状态；布局跟随协程属于页面，随 active/following 生命周期取消。
- 胶囊 session 仅在按下到释放/取消之间保存几何快照；手势 helper 负责 slop/释放，不混入数据库或网络。
- Markdown 展示尾部处理在既有 Default 解析协程中执行，不新增主线程长文本解析。

## 残留审计

已全项目搜索旧调度符号、旧字数 progress、旧冻结/实时行程变量、胶囊旧 gesture 标识、Coordinator scope 注入及相关测试/文档引用。更新混合 Python 结构契约并迁移已删除测试的文档链接；新 helper 调用完整，未保留双套跟随实现。

检查 Kotlin/Java、UI/state/callback、DAO/Repository、navigation、resources/theme、manifest、Gradle/dependencies、ProGuard、后台任务、日志/分析和文档：本轮未改持久化结构、权限、资源或依赖，无这些领域的专属旧功能残留需删除；共用 StreamUiScheduler 仍负责网络输出节流，不因删除旧跟随器而误删。

## 验证

最终稳定源码 Debug Kotlin 编译、compileDebugAndroidTestKotlin 和 lintDebug 通过；定向 JVM 共 9 套 58 项全部执行通过，0 跳过/失败/错误。Python 工具/契约共 125 项，119 项通过、6 项签名脚本测试因无可用 Bash 跳过。quick 10 项门禁通过，258 个 Kotlin 文件、277 个源码/配置文件的审计无错误/残留候选，git diff --check 通过。

新增 17 项 Compose 设备回归（7 跟随、2 思维完成、8 胶囊），本地只编译，设备执行交既有 API 33/35 云端矩阵。未做真机视觉验收，不宣称偶发问题在所有设备上已完全消失。

一次中途 lintAnalyzeDebug 因 IntelliJ AsyncExecutionService/PSI 内部异常中断，随后重跑遇到 classes.jar 的 Windows 文件占用；检查进程确认旧 Gradle daemon 已空闲后用 --stop 释放，再以 --no-daemon 执行最终检查成功。未删除缓存、未禁用检查、未修改 lint baseline；仍有项目原有弃用/静态警告，不宣称零警告。未本地打包 APK/AAB；不把本轮推送等同云端成功，推送后不等待或轮询。

## 上一轮云端复核

基线提交的 [check](https://github.com/zhouran200-ux/bu/actions/runs/39287638911) 和 [Build APK only](https://github.com/zhouran200-ux/bu/actions/runs/39287638954) 均成功，API 33/35 设备执行及实际执行校验步骤成功。完整日志下载403、报告下载401，已记录权限边界，未声称读取全部 XML/HTML 或确认逐用例数量。该结果不替代本轮行为验证。
