# 弹窗输入法交互检查

## 范围和处理

按用户要求排除 SettingsComponents 中 API 配置页的可用模型选择 ApiModelPickerDialog，该文件和该弹窗逻辑未改。

- 思考强度、笔记更多 DropdownMenu：采用不抢焦点的 PopupProperties(focusable=false)，保留底层输入框的输入法焦点；宿主 BackHandler 负责菜单返回处理。不调用 hide/show 反复补救。
- 对话模型选择、对话记录、笔记删除确认、字体删除确认：只在实际 DialogWindowProvider 对应的窗口设置 FLAG_ALT_FOCUSABLE_IM，非输入窗口不接管 IME。退出恢复原 flag；不操作 Activity 窗口，不设置 FLAG_NOT_FOCUSABLE，保留对话框原生返回及抽屉拖拽。
- 对话设置、编辑消息、笔记命名、字体命名：不设置非输入窗口 flag。在外层记住打开前 IME 是否可见，进入弹窗内容后仅首次给真实输入框请求焦点；已开键盘继续用于弹窗输入，原先未开不主动开启，也不在用户手动隐藏后反复抢回。

## 技术依据与边界

Material3 1.4.0 的 ModalBottomSheetProperties 旧 isFocusable 参数已失效，不能靠它保持键盘；Compose 每次更新会覆盖 softInputMode，因此没有使用一次性 SOFT_INPUT_STATE_UNCHANGED 作为保证。新的 helper 只修改一个 window flag。

非输入浮层保持键盘，不表示模态期间允许编辑底层输入框。ALT_FOCUSABLE_IM 涉及 IME 层级；不同系统/输入法可能有遮挡或窗口切换时序差异，须真机验证。非焦点菜单的 Back/Escape/方向键行为与默认菜单不同，已有宿主 BackHandler；输入法可能消费首次返回，外部点击也可能继续交给底层原有处理。用户主动发送、切来源页或点击对话空白收键盘逻辑未删除。

本轮不新建替代弹窗、不改样式或既有 API 配置往返/手动关闭路径，不改变 Manifest、依赖、数据库。没有声称彻底消除所有厂商 ROM 的视觉闪烁。

## 验证与残留审计

盘点全部2处 DropdownMenu、4处 ModalBottomSheet、笔记/字体命名 Dialog、消息编辑/删除 AlertDialog。排除 API 可用模型弹窗，仅给非文本弹窗加窗口 helper；所有有输入框窗口使用正常 IME 焦点。公共 helper 无 Activity fallback，无延迟显示键盘循环，退出恢复 flag。

本地仅执行相关 UI 包 JVM 单测与必要增量编译：93项，0失败/错误/跳过；21项 Python 工具和源码契约测试通过，源码卫生、嵌套滚动、diff检查通过。未全量重跑 Release/Lint、未打包 APK、未做真机输入法视觉验收。建议重点真机检查键盘打开/关闭两种初态，弹窗打开、选择、返回、遮罩、拖动、弹窗文本输入与 API 配置跳转。
