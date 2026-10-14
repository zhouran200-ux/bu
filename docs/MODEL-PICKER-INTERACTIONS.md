# 模型弹窗展开、定位和关闭修正

- 所有非空方案（包含只有一个模型的方案）点击标题只展开/折叠，不调用 onSelect。点击具体模型行才提交选择。
- 每次创建弹窗以当前方案 ID 和模型计算展开状态、列表初始索引，只有选中方案默认展开；索引由前面的折叠方案标题数量、标题行和当前模型在统一候选列表中的位置计算，不写死方案、模型名称或下标。每次重新打开重新读取当前选择；同一次打开期间换模型不会重建列表或自动跳动。
- LazyListState 使用初始索引直接定位到选中模型，不延时从顶部滚动。不存在方案或空候选安全回退。
- 旧逻辑禁用 sheetGesturesEnabled，自定义 LucentDragHandle 只移动把手并直接销毁弹窗。此弹窗改用 Material 底部弹窗原生把手、拖动与嵌套滚动，弹窗本体跟随手势。
- 选择模型只更新选择，父页面和弹窗都不自动关闭。移除所有业务 sheetState.hide/closeSheet 路径；只有用户返回、点击遮罩或拖动关闭由 ModalBottomSheet 原生 dismiss 回调处理。管理入口按后续要求改为直接进入现有设置/API配置页，保留弹窗状态，返回时恢复；不再要求先手动关闭。详见 API-CONFIGURATION-RETURN.md。
- 已核对项目实际 Material3 1.4.0 源码：dragHandle 是视觉标记，本体 Surface 的 anchoredDraggableState.draggableState 处理拖拽和松手 settle；启用 sheetGesturesEnabled 后把手事件交由整张弹窗，不再挂独立 travel/translationY。

## 验证与残留审计

已有6项 Kotlin 定位测试覆盖单模型、选中模型在列表末端、选中方案变化、重排、空/删除方案和候选归一化。按用户最新授权本地执行 testDebugUnitTest、compileReleaseKotlin、lintDebug：185项测试0失败/错误/跳过，编译通过，Lint 0错误7警告，BUILD SUCCESSFUL。没有执行 APK 打包。新增4项源码契约测试检查父页面不关闭、无程序hide、首帧初始索引、原生整体拖动配置；与工具测试合计13项 Python 测试通过。源码卫生、嵌套滚动、diff 检查通过；没有真机手势动画验收，源码契约测试不替代端到端手势测试。

已检查模型弹窗的所有关闭与选择路径，移除单模型直接选中分支及 models.size>1 的展开限制；旧公共 LucentDragHandle 在其他弹窗仍被使用，未误删。方案ID、请求解析、配置存储、资料页和其他弹窗行为不变；没有 Manifest、依赖、schema 变动。

上一轮113ff10云端结果：check和APK/Preview均成功；Debug179项、Release179项、Android26项均0失败；Lint两种配置各0错误6警告。这是上轮结果，不代表本次新交互已完成云端或真机验证。推送后结束，本轮不等待CI。
