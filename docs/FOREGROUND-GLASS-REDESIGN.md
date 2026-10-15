# 前景磨砂玻璃材质重构

基线82d9dfe。只改前景设计，不改色板、主题选择规则、渐变/几何背景、系统动画策略和数据/网络业务。

## 材质分层
- ForegroundGlass.kt统一局部磨砂：背景专用HazeState，24dp模糊（阅读层28dp）、低幅噪声、轻薄tint、0.75dp非均匀高光边缘、3dp轻阴影。阅读层比普通卡片更厚，提升大段正文/输入内容可读性。
- 同一页面内卡片/导航/输入栏使用真正背景采样，仅背景本身注册hazeSource。不是对文字/整个卡片使用Modifier.blur。旧设置页/笔记本列表前景source与inactive dummy状态删除，避免将文字采入材质或重复source。
- 小按钮/选中行采用薄control wash，重点发送/确认仍保留主题强调色，不把所有元素都透明化。按压缩放减到0.985。
- Popup/Dialog是独立窗口，glassPanel采用现有主题底色、细渐变高光和8dp轻阴影；不声称能采样跨窗口背景，禁止把默认容器清透明却无底色。
- PureWhite分支明确不走haze，保留不透明白底、灰边和轻影。主题色算法lucentScheme/pureWhiteScheme保持原逻辑。旧frostedGlass API转发新实现，避免逐页重复材质。

## 覆盖
笔记本/设置卡、根设置导航卡、消息卡、撤销条、输入栏、方案胶囊和工具按钮、底部导航、来源章节标签、对话设置、记录抽屉、模型选择/获取模型抽屉、编辑/删除/字体确认弹窗、自定义命名Dialog及菜单。原列表key、回调、横杠手势、IME、历史入口不变。颜色预览/强调按钮不机械玻璃化。来源页正文底色保留以长文阅读，顶部浮签接新材质。

## 不变证明
检查FluidGlassBackground/ThemeModes/BackgroundPaletteMotion/PlatformDiffuseBackground/SettingsRepository无diff；Glass.kt色板与主题函数不修改。MainActivity只调整source/provider与底部导航前景画层。无Room/SharedPreferences/Manifest/Proguard/权限/后台服务变更。

## 清理审计
按AGENTS依赖→多标识搜索→专属/公共分类→删除旧source与样式→复搜→编译。LocalHazeState、CARD_FILL、BLURRED_FILL、HazeContainer、旧HazeMaterials调用、无消费者的haze-materials依赖/版本目录alias与旧厚rim路径已退场；公共frostedGlass/pressableGlass保留调用兼容，主题及动画公共函数不删。去掉旧不透明输入栏覆盖和旁路双border，旧弹层容器统一。图标/业务/通知/测试数据不改，不动历史迁移。

## 验证边界
41类321项JVM测试零失败；Release Kotlin编译通过；36项Python契约通过，123个Kotlin文件源码卫生、嵌套滚动扫描通过。材质测试只验证参数与路由，不等于视觉截图验收。Android lintDebug通过（0错误、8警告、1提示；baseline另过滤28警告5提示），告警包括已有scroll rail频繁状态读取、KTX与版本目录建议，不将其说成零警告。移除旧preset依赖后JVM与Release另行集中验证，不重复设备测试。未本地打包APK。真机需检查浅/深/纯白/动态色下长文可读性、菜单/键盘/抽屉、列表帧率与多卡blur性能；低平台Haze fallback使用更厚tint，不承诺所有Android有相同模糊表现。

上轮82d9dfe的check与APK均成功；这不是当前提交云端结果。

## 弹窗容器交回 Material 管理（后续修正）

上一轮把 glassPanel 套在 DropdownMenu/AlertDialog/ModalBottomSheet 上，导致三类实际故障：

1. DropdownMenu 的 modifier 作用于 Material Surface 内部的 Column，自绘阴影会被外层面板裁掉，描边与高光被切。
2. containerColor=Transparent 让菜单失去 Material 自己的容器，尺寸与边缘由自绘层决定，长文本项在 280dp 上限下更易被截断。
3. 底部弹窗内容只有硬编码高度（如 520dp）且没有可用高度上限；键盘弹出时下半截直接超出面板被裁。

现改为：菜单/对话框/弹窗统一使用 Material 参数 shape、containerColor=glassPanelColor()、tonalElevation、shadowElevation、border=glassPopupBorder()，不再传自绘 modifier；底部弹窗用 sheetContentHeightLimit() 依据屏幕高度限制内容，对话设置弹窗内容可滚动；笔记 ⋯ 菜单在列表滚动时主动关闭，避免弹窗停留在旧位置。自绘面板仅保留给自定义命名 Dialog（glassPopupPanel，不带自绘阴影）。glassPanel 已无调用方，按死代码清理。

