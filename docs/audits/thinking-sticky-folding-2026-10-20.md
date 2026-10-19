# 思考标题吸顶与用户消息折叠

## 行为范围

1. 所有 AI 消息均保留思考标题。生成结束且未收到非空 reasoning 时显示“已思考（不支持）”，没有空的等待占位或可展开箭头；判断依据为实际收到的内容，不按厂商/模型名称猜测。生成中保持“思考中”，收到思维链后沿用原思考标题与内容。
2. 思考区展开时，按聊天视口顶部像素实际落在哪个列表 item 上决定吸顶：仅当前 assistant 消息可拥有标题。顶部进入用户消息、消息间隙、折叠 AI、无可展开内容的完成消息时不吸顶；双向滚动使用相同规则，不借用前一条 AI。所属消息离开顶部时标题随其底边上推裁剪，避免覆盖下一条消息。
3. 用户消息按实际排版超过六行才出现“展开全文”；默认六行、自然折行计入，展开后按钮切为“收起”。完整文本没有截取，选词与复制全文继续使用完整原文。

## 状态所有者与生命周期

- [NotebookDetailScreen.kt](../../app/src/main/java/com/lucent/app/ui/NotebookDetailScreen.kt) 在 Pager 外 remember(notebookId) 创建本次访问的 NotebookMessageUiState，并通过 CompositionLocal 传给聊天。离开详情 composition 后状态释放；详情 active=false 也清空用户展开状态；chat/source 切换不清空。
- [ThinkingLaneState.kt](../../app/src/main/java/com/lucent/app/ui/ThinkingLaneState.kt) 为同一 readingKey 提供唯一思考展开/内滚动状态，原位置与吸顶标题共用对象，不使用两份异步回调状态。原有思考阅读偏好继续保存，用户消息展开 map 不落库、不进 SharedPreferences、不进 ChatSession、不使用 rememberSaveable。
- 空 AI slot 初次从非生成转生成时，若无保存或手动选择会自动展开；已有保存/手动收起则尊重。未交互且从不可展开的空 slot 不保存默认 collapsed，避免离开重进后把它误当用户选择。
- 原标题、吸顶标题与用户展开按钮的手动切换先暂停流式跟随、作废旧手势自动恢复，再更新展开状态，避免与自动滚动竞争。
- [ChatThinkingLane.kt](../../app/src/main/java/com/lucent/app/ui/ChatThinkingLane.kt) 持有原有状态保存协程，生命周期随 lane composition；定时写入和退出写入都使用同一持久化资格判断与最新可展开状态。

## 布局

- [StickyThinkingHeader.kt](../../app/src/main/java/com/lucent/app/ui/StickyThinkingHeader.kt) 观察 LazyListLayoutInfo 的实际可见 item，以半开区间包含 viewportStartOffset 决定所属项，再核验 message key/id/role 与共享展开状态。标题叠放于聊天区域顶部，不插入新列表项、不改变滚动 key 或 item 高度。
- [ThinkingHeader.kt](../../app/src/main/java/com/lucent/app/ui/ThinkingHeader.kt) 统一标题文案和按钮，普通与吸顶显示没有独立业务判断。
- [CollapsibleUserText.kt](../../app/src/main/java/com/lucent/app/ui/CollapsibleUserText.kt) 使用 SubcomposeLayout：在同一 measure pass 测正文、读取 TextLayoutResult、按需测按钮并一次发布总高度。onTextLayout 不写 snapshot state，避免长消息初次出现/回收重建时先短后高。按钮位于 SelectionContainer 外。

## 残留审计

- 删除 AI 思考区原有“仅 streaming/liveThought/reasoning 非空才显示”的条件，不保留另一套隐藏路径。
- 原 lane 内部 expanded/retainLiveHeight/ScrollState 已迁移到共享状态，原处不再维护重复状态；旧 rememberSaveable(readingKey) 路径已清理，对应结构契约已更新，实时完成后保持高度的行为继续测试。
- 用户正文原直出 Text 由受控折叠组件替代，SelectionContainer 责任完整迁移，按钮/全文复制/编辑/删除未混入选择范围。
- 全局核对 AiMessageCard/UserMessageCard/ThinkingLane 调用与新文案，清理失效 import，更新 i18n 源目录并重新生成输出，保留既有颜色/权限/依赖/网络/数据库/导航/后台任务，不新增持久化字段或分析日志。
- 静态残留检查覆盖源码、测试与配置；本轮没有资源、Manifest、Gradle、ProGuard、服务或 DAO 专属旧路径需要移除。

## 验证记录

稳定源码 compileDebugKotlin、compileDebugAndroidTestKotlin、lintDebug 均通过。5 套定向 JVM 共 20 项执行通过，0 跳过/失败/错误。Python 工具/契约共 129 项，123 项执行通过、6 项签名脚本测试因当前环境无可用 Bash 跳过。quick 10 项门禁通过，269 个 Kotlin 文件与 288 个源码/配置文件残留审计无错误/候选，git diff --check 通过。Lint 保留项目已有警告/提示，未改 baseline，未宣称零警告。

新增 24 项设备测试，覆盖无 reasoning 完成/历史、空 slot 开始生成、吸顶双向切换/用户与间隙/共享点击、六行边界/自然折行/宽度字号/选择复制、笔记本访问生命周期以及首帧高度。设备测试只本地编译，不本地运行或打包 APK/AAB，执行交现有 API 33/35 云端矩阵。未做真机视觉验收，不把测试代码编译通过说成设备行为已通过。

本轮基线 6677be236e3f0df763683ef54913914adff267cb 的 [check](https://github.com/zhouran200-ux/bu/actions/runs/39299729976) 与 [Preview 构建](https://github.com/zhouran200-ux/bu/actions/runs/39299729936) 均成功；日志403/报告401，未宣称逐 case 报告已审阅。上一轮绿灯不覆盖本轮改动；推送后不等待本轮云端结果。
