# 全部底部弹窗的抽屉交互

## 盘点

项目共4处 ModalBottomSheet，无额外 BottomSheetScaffold 或其他底部抽屉实现：

1. ProfileModelPickerSheet：对话模型选择。
2. ChatHistorySheet：对话记录。
3. DialogueSettingsSheet：对话设置。
4. ApiModelPickerDialog：API 设置内模型多选。

## 统一行为

所有弹窗启用 sheetGesturesEnabled，使用 BottomSheetDefaults.DragHandle；横杠仅是本体拖拽把手，整体由 Material3 底部弹窗的锚点/拖拽/松手 settling 处理。删除原来只移动横杠并达到阈值就直接关闭的 LucentDragHandle 及其专属动画、手势、协程 imports。

除原生用户手动拖动、遮罩点击或系统返回的 onDismissRequest 外，不再保留其他关闭入口：移除对话记录及 API 多选右上角关闭图标、对话设置取消按钮。选择模型、定位记录、跳到顶部/底部、保存对话设置、确认模型多选均只执行业务动作，不移除弹窗。

对话设置按钮改名“保存”，保存后有反馈。记录定位后更新当前记录高亮。API 模型多选确认后保留面板并提示已应用；若当前模型已被取消勾选，选择同步到剩余首项或空，避免继续使用已取消项。

普通居中 AlertDialog/Dialog、DropdownMenu 与业务确认取消不在底部弹窗范围内，不误删。对话生成、模型请求、数据库、Manifest、资源与依赖未修改。

## 验证与审计

新增全项目底部弹窗源码约束：4处均启用本体手势与原生把手，无旧 LucentDragHandle、无程序 sheetState.hide，父级保存/选择/记录定位无自动关闭，额外关闭按钮已移除。源码契约与工具测试共16项通过；源码卫生、嵌套滚动、diff检查通过。

本地执行 testDebugUnitTest、compileReleaseKotlin、lintDebug，最终BUILD SUCCESSFUL；185项单测0失败/错误/跳过，Debug/Release编译通过，Lint0错误7警告，不打包APK。真机手势和动画观感未验收，源码约束不替代端到端手势测试。上轮云端仅确认77b3abf的check和APK成功；7a44054此前因网络故障尚未推送，本轮一起推送。
