# 对话页滚动与圆角修正

移除详情页顶部返回图标及占位，保留原BackHandler：来源返回对话，对话返回笔记本。

旧滚动条删除：可见消息平均高度估全局、滑块比例忽长忽短、随fraction重建pointerInput、每次移动launch滚动均移除。替换为固定40dp长/3dp宽短滑块，无整条轨道，列表滚动时显示，停止1.2秒淡出；28dp边缘触摸区域，直接纵拖不需长按/先展开，对话记录入口已迁移到输入栏思考强度与对话设置之间；滚动条不再有点按记录行为。滚动条限制在消息列表Box，不跨输入框。

测量高度按稳定message key保存，不同短消息/长回复互不混淆，删除时清失效key。拖动前冻结高度快照和起始fraction，按手指相对位移求位置，拖动过程中不更换几何/不重启pointerInput；使用requestScrollToItem合并布局请求，不launch竞争协程。长消息内部offset参与进度，顶底使用真实canScroll和footer。未测量消息以一屏高度估算，未宣称全量消息精确像素比例；拖动期间快照固定避免追着变化模型跑。

输入栏外层按24dp圆角clip，Surface浅色白底、深色跟随surface，背景和内容同shape，不画整页矩形底。保留输入法贴底逻辑与消息选择/引用。

完整JVM回归及Release编译通过；新4项geometry覆盖长短消息、长消息offset、抓取不跳、边界/极值；移除旧平均高度专用测试和死代码，保留文档分块/追底逻辑。Python契约/卫生/嵌套滚动通过。未本地打包、未真机验收；需实机长消息拖动、快速反向、删除重排、键盘收放、深浅/纯白背景检查。

残留审计：chatScrollMetrics/scrollChatToFraction/averageItemSize/railExpanded/railDragging旧路径及ArrowBack已清；无数据库、依赖、Manifest改变。历史审计/文档保留基线记录，不当成新实现验收承诺。
