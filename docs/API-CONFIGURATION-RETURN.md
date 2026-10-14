# 模型弹窗往返现有 API 配置页

## 原逻辑

管理入口只标记“手动关闭后打开设置”，弹窗关闭后才切设置页；设置页返回只到设置根页，没有模型弹窗来源信息。因此不符合“点击管理直接进入现有 API 配置，返回原弹窗”。

## 修正

- 文案统一为“管理API配置”，包括无密钥提示中的入口名称。
- 直接使用 AppNavigation 的 Screen.Settings + SettingsRoute.Api；不新增 Dialog、API 页面、覆盖层或 apiOnly 模式。中途试写的覆盖层已完全撤回。
- 导航记录来源笔记 ID。模型弹窗原组件保留，只有 ModalBottomSheet 内容在设置期间隐藏，因此初始列表状态、展开状态不销毁；返回来源笔记后显示原弹窗。
- API 页系统返回和已有设置面包屑返回上级均经原 rememberSettingsRoute 路径消耗来源，切回 Screen.Notebooks。普通设置入口没有来源，行为保持原样。来源笔记失效或转到其他设置路由时清理返回标记。
- 设置页在笔记仍打开时也显示现有设置顶栏及相应内容留白，修复原先 activeNotebookId 导致设置标题一并被隐藏的问题；未设计新顶栏。
- SettingsScreen 只在 active 时处理系统返回，避免隐藏的设置页抢占返回事件。

## 验证与残留审计

新增3项 Kotlin 导航测试覆盖来源往返、普通 API 返回及失效/其他路由来源清理；新增源码契约检查确保没有覆盖层、无延迟关闭导航、弹窗状态在可见性判断之前创建。Python工具/契约17项通过；源码卫生、嵌套滚动、diff检查通过。

审计入口文案、AppNavigation/SettingsNav、父子可见性、返回处理、KeepAliveTabs、顶部布局与测试。旧 openManagementAfterDismiss 和覆盖层相关 apiOnly/ApiConfigurationPage 已无运行代码残留；模型选择不关闭、原生手势不变；无数据库、依赖、Manifest修改。

本地testDebugUnitTest、compileReleaseKotlin、lintDebug均成功：188项单元测试0失败/错误/跳过，Debug/Release编译通过，Lint0错误7警告；不打包APK。真实Android页面往返和键盘返回优先级仍需真机验收，不将源码契约冒充UI端到端测试。推送后不等待CI。
