# 视觉基础审计（个人使用验收）

基线：9c3df7b（本次读到的 HEAD 一致）。产品源码只读；只新增本报告。没有运行打包、设备测试或性能采样，以下“源码确定”指路径/条件可以从实现推出，不等于已经真机复现。优先级 P2 为应修的实际交互/状态问题，P3 为低频或体验项。所有源码链接相对本报告目录。

## 1. 完整阅读清单

以下指定文件均从首行读到末行，不以搜索片段代替完整阅读。

|文件|完整行范围|验收关注点|
|---|---:|---|
|[BackgroundPaletteMotion.kt](../../app/src/main/java/com/lucent/app/ui/BackgroundPaletteMotion.kt)|1–55|循环色板、生命周期、动画关闭|
|[LucentFonts.kt](../../app/src/main/java/com/lucent/app/ui/LucentFonts.kt)|1–88|字体解析、缓存、排版倍率|
|[PlatformDiffuseBackground.kt](../../app/src/main/java/com/lucent/app/ui/PlatformDiffuseBackground.kt)|1–69|系统动画观察、注销、生命周期|
|[Haptics.kt](../../app/src/main/java/com/lucent/app/ui/Haptics.kt)|1–40|系统触感偏好|
|[LucentTitles.kt](../../app/src/main/java/com/lucent/app/ui/LucentTitles.kt)|1–34|字号层级、透明度|
|[StepperSlider.kt](../../app/src/main/java/com/lucent/app/ui/StepperSlider.kt)|1–266|拖动与滚动冲突、取消、无障碍|
|[Toasts.kt](../../app/src/main/java/com/lucent/app/ui/Toasts.kt)|1–18|上下文/重复提示|
|[ReorderDrag.kt](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt)|1–482|长列表、取消/落位、缩减动画|
|[LucentMotion.kt](../../app/src/main/java/com/lucent/app/ui/LucentMotion.kt)|1–35|动画规格|
|[LucentIcons.kt](../../app/src/main/java/com/lucent/app/ui/LucentIcons.kt)|1–118|矢量/缓存|
|[FluidGlassBackground.kt](../../app/src/main/java/com/lucent/app/ui/FluidGlassBackground.kt)|1–130|全屏动画、生命周期|
|[UiComponents.kt](../../app/src/main/java/com/lucent/app/ui/UiComponents.kt)|1–118|按钮布局/点击|
|[ThemeModes.kt](../../app/src/main/java/com/lucent/app/ui/ThemeModes.kt)|1–242|主题色、亮暗、选择范围|
|[Glass.kt](../../app/src/main/java/com/lucent/app/ui/Glass.kt)|1–485|主题映射、对比、随机色板|
|[ChatStats.kt](../../app/src/main/java/com/lucent/app/ui/ChatStats.kt)|1–44|状态文本/计数|
|[PlatformFont.kt](../../app/src/main/java/com/lucent/app/data/PlatformFont.kt)|1–19|仅 UI 文件选择关联，未接管 data 审计|
|[I18n.kt](../../app/src/main/java/com/lucent/app/i18n/I18n.kt)|1–216|当前中文文案与可访问性|
|[themes.xml](../../app/src/main/res/values/themes.xml)、[日间 colors.xml](../../app/src/main/res/values/colors.xml)、[夜间 colors.xml](../../app/src/main/res/values-night/colors.xml)、[strings.xml](../../app/src/main/res/values/strings.xml)|1–6 / 1–5 / 1–4 / 1–3|启动背景/主题资源|
|[baselineprofile 构建配置](../../baselineprofile/build.gradle.kts)、[Manifest](../../baselineprofile/src/main/AndroidManifest.xml)、[生成器](../../baselineprofile/src/main/kotlin/com/lucent/app/baselineprofile/BaselineProfileGenerator.kt)|1–23 / 1–2 / 1–26|生成范围/安装联动|

调用链补读：[MainActivity.kt:147–249](../../app/src/main/java/com/lucent/app/MainActivity.kt#L147-L249)、[背景调用:324–335](../../app/src/main/java/com/lucent/app/MainActivity.kt#L324-L335)、[PlatformSettings.kt（完整）](../../app/src/main/java/com/lucent/app/ui/PlatformSettings.kt)、[字体页:55–294](../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L55-L294)、[字体删除:536–565](../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L536-L565)、[笔记本拖动/菜单:178–329](../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt#L178-L329)、[FontStore.kt（完整，仅缓存关联）](../../app/src/main/java/com/lucent/app/data/FontStore.kt)、[设置写入:210–237](../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt#L210-L237)。也完整阅读了 [ReorderSlotsTest.kt](../../app/src/test/java/com/lucent/app/ui/ReorderSlotsTest.kt)、[PaletteLibraryTest.kt](../../app/src/test/java/com/lucent/app/ui/PaletteLibraryTest.kt)、[ChatStatsTest.kt](../../app/src/test/java/com/lucent/app/ui/ChatStatsTest.kt)、[LucentTitlesTest.kt](../../app/src/test/java/com/lucent/app/ui/LucentTitlesTest.kt)、[app 构建配置](../../app/build.gradle.kts)、[模块配置](../../settings.gradle.kts)。只阅读既有测试，未声称本轮测试通过。

## 2. 源码确定的问题

### VF-01 / P2：纵向滚动从滑块开始会误改设置

证据：[StepperSlider.kt:137–161](../../app/src/main/java/com/lucent/app/ui/StepperSlider.kt#L137-L161)。Initial 阶段任一 pressed 即当作拖动，148 行直接提交点击横坐标，未等横向 touchSlop；156–157 行对后续任意方向移动改值并 consume。触发：字体设置页向下滚动，手指恰落在字号/行距/字距轨道上。即使最终父滚动接管，也已经改了草稿；如果走正常抬手，完成回调还会把误改值写入设置（[字体页:216–253](../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L216-L253)）。最小建议：使用有横向方向锁/触摸阈值的 slider 手势，父级纵向滚动获胜时不改值、不保存；单击与真正横拖明确分开。验收：轨道上开始纵向滚动十次，三个值均不变；横拖/点击仍正常。

### VF-02 / P2：字体预览二次乘字号倍率

证据：[字体页:189–204](../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L189-L204) 把 14sp 乘 activeFontScale；而 [MainActivity.kt:217–235](../../app/src/main/java/com/lucent/app/MainActivity.kt#L217-L235) 已向整个页面提供系统倍率 × savedFontScale 的 LocalDensity。触发：字号保存为非 1（例如 1.30）。预览实际为 14 × 1.30 × 1.30 × 系统倍率，普通 14sp 文本只有 14 × 1.30 × 系统倍率；拖动草稿时还乘上旧 saved 值，放手后额外跳一次。最小建议：预览局部 Density 去除已保存应用倍率后仅应用草稿一次，或用 active/saved 比值补偿；字号/行距分别验证。不是 LucentFonts 的 fontScale 参数未用导致“全局字号失效”。

### VF-03 / P2：长列表无法一次拖往屏外，且排序没有无障碍替代动作

证据：[ReorderDrag.kt:189–200](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L189-L200)、[拖动:275–305](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L275-L305)、[边界:360–367](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L360-L367)、[插槽:449–462](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L449-L462)。插槽仅 visibleItemsInfo，拖动消费移动，未向 listState 发起任何边缘滚动，视觉位置又限制在视口。触发：20 个笔记本，把首个长按拖到屏幕底部等待；只会在当前可见范围排序，不能持续滚至最后。另 [笔记本卡片/菜单:268–325](../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt#L268-L325) 只有打开/重命名/删除，未提供上移/下移或 CustomAccessibilityAction；[拖动修饰符](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L265-L307) 也只有 pointerInput。TalkBack/键盘用户没有等价的排序入口。

最小建议：边缘阈值驱动 scrollBy，累计滚动补偿并基于更新插槽计算落点；在卡片菜单或语义中增加上移/下移/移至首尾动作。验收：首到尾/尾到首、不同高度卡片、拖到边缘后取消、旋转、删除拖动项、TalkBack 排序后持久化。

### VF-04 / P2：自绘滑块没有可调范围语义

证据：[StepperSlider.kt:123–170](../../app/src/main/java/com/lucent/app/ui/StepperSlider.kt#L123-L170) 的轨道仅 contentDescription，不提供 progressBarRangeInfo/setProgress 或键盘方向键；[按钮:184–203](../../app/src/main/java/com/lucent/app/ui/StepperSlider.kt#L184-L203) 可点击，故不是“完全不能操作”，但 TalkBack 没有标准滑块调节动作，需要逐次点击两端。字体页大范围字距需要很多步；30×36dp 的布局也偏紧（Compose 实际最小触摸扩展及相邻冲突须设备确认）。最小建议：轨道补 progress semantics 和 setProgress（包含 snap/完成回调），按钮保留作为替代；扩大布局触达空间。

### VF-05 / P3：双真异常状态会锁死纯白与动态取色开关

正常逐个开启互斥有效，不把它报成一般用户必现。条件限定为 Android 12+、持久化 theme=pure_white 且 dynamicColor=true（快速两项开启在 Flow 更新前竞态，或已有异常设置）。[PlatformSettings.kt:50–80](../../app/src/main/java/com/lucent/app/ui/PlatformSettings.kt#L50-L80)：动态开关因 pureWhiteOn 被禁用且显示 false；纯白开关因 dynamicColorOn 被禁用。主题/背景也被锁，用户无可用开关退出。[MainActivity.kt:166–181](../../app/src/main/java/com/lucent/app/MainActivity.kt#L166-L181) 渲染以纯白优先，而 [动态状态 helper:89–94](../../app/src/main/java/com/lucent/app/ui/PlatformSettings.kt#L89-L94) 不排除纯白，属于确有行为分歧的重复主题逻辑；[动态持久化写入](../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt#L234-L237) 不归一化另一项。最小建议：开关关闭路径始终可用，开启用同一事务清另一项，并使“有效模式”判断一致。普通渐变亮暗推断与主题分组没有发现确定错配，不因多个 helper 本身提出重构。

### VF-06 / P3：触感绕过系统“触摸反馈”偏好

证据：[Haptics.kt:24–39](../../app/src/main/java/com/lucent/app/ui/Haptics.kt#L24-L39) 直接 Vibrator one-shot，无 HAPTIC_FEEDBACK_ENABLED 检查/无触感开关；[GlassButton 点击](../../app/src/main/java/com/lucent/app/ui/UiComponents.kt#L90-L97)、[滑块变更](../../app/src/main/java/com/lucent/app/ui/StepperSlider.kt#L89-L95) 等正常操作都会调用。系统关“触摸振动”并不等于禁止 Vibrator 服务，代码未请求按触摸反馈策略执行。最小建议：普通 UI 触感改用 View.performHapticFeedback/Compose HapticFeedback 或显式尊重系统偏好；设备上分别测试触摸振动关闭、静音/免打扰与无振动器。

## 3. 源码遗漏已确定，但实际设备效果/严重度待测

|项目|证据与边界|最小建议与设备验收|
|---|---|---|
|背景几何动画未接 lifecycle active|[FluidGlassBackground.kt:37–39](../../app/src/main/java/com/lucent/app/ui/FluidGlassBackground.kt#L37-L39) 读取 environment 却忽略 active；84–93 行保留无限 transition。色板循环/随机则都检查 active。Android/Compose 帧时钟可能在完全后台停帧，不能由此宣称后台持续耗电。|moving 加 active；前后台、熄屏、分屏覆盖、长对话静止各测 60s GPU/帧唤醒。随机 20s 保持段与循环量化后仍每帧等待（[随机:329–367](../../app/src/main/java/com/lucent/app/ui/Glass.kt#L329-L367)、[循环:33–47](../../app/src/main/java/com/lucent/app/ui/BackgroundPaletteMotion.kt#L33-L47)），单独关闭几何背景做对照，确认后再考虑 delay/低频 ticker，不把分配量当已测 jank。|
|缩减动画下仍选 spring|[ReorderDrag.kt:327–341](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L327-L341)、[JELLY_STILL:438](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L438)、[placement:410–421](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L410-L421)：motion=false 选阻尼1的 spring 而非 snap；placement 未读 motion。关闭无限 jelly 有效。Compose 系统 durationScale 可能把动画直接完成，因此不声称关系统动画必然肉眼回弹。|如产品承诺零动画，用 snap/禁 placement；系统“移除动画”开关、scale=0 以及动画运行中切换都测。|
|主题启动闪色|[themes.xml](../../app/src/main/res/values/themes.xml)、[日间背景](../../app/src/main/res/values/colors.xml)、[夜间背景](../../app/src/main/res/values-night/colors.xml) 只按系统资源限定符；[窗口补色:213–215](../../app/src/main/java/com/lucent/app/MainActivity.kt#L213-L215) 在 Composition 之后才更新。显式深色+系统浅色/反之可能冷启动短闪系统色。|录屏冷启动四色组合，不凭源码声称闪屏时长；若明显再同步启动窗口/保持 splash 至正确主题。|
|色调与玻璃叠加实际对比|[lucentAccent:75–93](../../app/src/main/java/com/lucent/app/ui/Glass.kt#L75-L93) 有明度守卫；既有测试只在固定浅/深 panel 验证 accent≥4.5，不覆盖每个主题/动态壁纸/渐变热点/低透明次要文本。|深色蓝莓/陶土、浅色蜂蜜/麦田、纯白、动态壁纸高亮和深暗，量测提示文字、边框、选中态、错误态。不把未采样对比直接定为缺陷。|
|缓存释放|字体 resolver 缓存 key→FontFamily，无 Activity 引用；[字体删除](../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L545-L551) 已 evict 且 [FontStore 最大12](../../app/src/main/java/com/lucent/app/data/FontStore.kt#L12)。[滚动记忆](../../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L209-L237) 当前调用为固定页面 key，不据 HashMap 声称无限增长。Toast 保留一个对象且 applicationContext。|反复导入/选中/删除大字体，观察 Compose 原生字体缓存/内存回收；应用 resolver evict 不等于已证明 native 立即释放。字体文件完整性/导入安全归 data agent，不重复定责。|

## 4. baselineprofile 与现有测试边界

[生成器:17–24](../../baselineprofile/src/main/kotlin/com/lucent/app/baselineprofile/BaselineProfileGenerator.kt#L17-L24) 仅 startActivityAndWait；目标包名和 [applicationId:25](../../app/build.gradle.kts#L25) 一致。没有主题切换、拖动、滚长聊天的 profile 场景。仓库 app/src 未发现 baseline-prof/startup-prof 文件；[app 插件/依赖](../../app/build.gradle.kts#L1-L5) 没有 baselineprofile 插件或模块依赖，只 [profileinstaller](../../app/build.gradle.kts#L133)。[CI:69](../../.github/workflows/check.yml#L69) 编译该测试模块，不是运行生成并导入 app。结论：当前源码不能证明这个生成器产物进入 Release；profileinstaller 依赖库自带 profile 不等于项目已经生成 app profile。这里记录验证/优化缺口，不据此认定启动性能差。最小建议：需要优化时先测 release 启动，再明确生成/导入链，并覆盖真正热路径。没有在本地运行设备或打包任务。

既有 slots 测试验证插槽数学和清理状态，不能替代自动滚动、实际 pointer 取消及 TalkBack；色板测试验证亮暗和固定 panel 可读性，不能替代真机所有背景对比。ChatStats 0 缓存显示“—”有明确测试约定，本轮不当作未知状态错误；I18n 只有 Zh 和固定 S，app 也是中文限定，未因无英语语言切换提出问题。

## 5. 验收场景与通过标准

1. **排版/滑块**：字号 85/100/130%，行距 1.0/1.2/1.8，字距 -0.5/0/2；系统字体100/最大；纵滚手指穿过轨道不改值；横拖放手保存一次；多指、系统取消、旋转后轨道继续可用。预览与普通同字号正文一致。
2. **主题状态**：system/light/dark 与系统昼夜交叉；18 色调代表深浅；纯白→退出、动态→退出、快速切换及双真异常恢复。选中态、菜单、对话框、状态栏/导航栏可辨，纯白无渐变渗透。
3. **长列表拖拽**：20/100 个笔记本，首尾互移、触边停留、拖时数据变动/取消/退后台；顺序持久化，页面重新打开一致；TalkBack 与键盘有等价移动入口。
4. **动画功耗**：静态/流动/循环/随机分别测相同页面及长对话；前台不无意义全树高频重组；后台/熄屏不新增帧唤醒；系统移除动画生效。只接受实测曲线，不用代码行数判断性能。
5. **字体与资源**：系统字体、自定义中文/拉丁/TTC、删除当前字体与非当前字体；主界面即时回退；大字体压力观察内存；系统主题与应用显式主题反向冷启动录屏。
6. **可访问性**：TalkBack 读标题/当前值/范围并能改值；按钮可命中且相邻目标不冲突；排序语义不只“拖拽以调整顺序”文案；关闭系统触摸反馈后 UI 不主动震动。

## 6. 未发现足够依据的问题

没有仅因命名、行数、无注释或“重复架构”开问题。没有发现固定 palette 内空色板造成除零的真实调用条件。平台字体 query 与 InputStream 使用 use 关闭；动画 ContentObserver 与 LifecycleEventObserver 在 onDispose 注销。字体倍率由 LocalDensity 全局实现，不重复要求 LucentFonts 再乘。纯白使用近白页面/纯白 surface 是已明确的视觉策略，不因不是全屏 #FFFFFF 提问题。矢量图标由 lazy 常驻少量对象，不构成无界缓存。产品代码未修改，报告仅文本与引用校验。

## 7. 本范围正向与反向审计（按 AGENTS.md）

本任务没有删除/替换产品功能，因而不执行删除清理。以下为本范围依赖与残留审计记录；跨领域旧功能残留由独立代理汇总，兼容旧持久化值所需字段不直接认定垃圾。

|正向功能→实现→状态/消费者|失败/退出路径核对|
|---|---|
|主题选择/纯白/动态→设置持久化→[MainActivity 主题分支](../../app/src/main/java/com/lucent/app/MainActivity.kt#L147-L211)→ColorScheme/LocalPureWhite/背景/系统栏|正常互斥成立；双真锁死见 VF-05；未知 mode fromKey 回 SYSTEM。显式 theme 与系统启动资源差异归设备风险。|
|背景静态/循环/随机→[色板状态](../../app/src/main/java/com/lucent/app/ui/Glass.kt#L303-L371)与[循环](../../app/src/main/java/com/lucent/app/ui/BackgroundPaletteMotion.kt)→根背景与 accent|色板 animation=false 或 inactive 停止；循环停时回首色板、随机保持当前色。几何背景漏 active 单列待测。不把这两种停止策略差异视为产品 bug。|
|导入/选择字体→PlatformFont URI 与 FontStore→resolver→[全局 typography/density](../../app/src/main/java/com/lucent/app/MainActivity.kt#L217-L240)|查询 cursor/输入流关闭；resolve 失败回系统字体；删除 evict 并重置当前选择。源文件完整性/并发导入交 data agent；预览二次倍率见 VF-02。|
|滑块→草稿→完成回调→持久化→全局排版|步进钳位存在；纵向起手抢改值见 VF-01，范围语义见 VF-04。取消必须设备验证，不以未实测取消卡死写确定结论。|
|笔记本长按→ReorderDragState→visible slots→drop→manualOrder→DAO 更新→落位清理|无移动释放/取消清理实现及测试存在；移动后 timeout/return 清理存在；屏外移动与无障碍缺口见 VF-03。DAO 失败反馈与跨域数据一致性由对应代理接手。|
|按钮/提示→Haptics/LucentToast|Toast 单实例取消旧提示、appContext；触感服务失败容错，但系统偏好缺失见 VF-06。|

反向检索实际执行于 app/src/main 及 Gradle/工作流（使用 grep，不以文件名猜消费者）：

- **字段/键**：themeMode/dynamicColorEnabled/fontScale/lineSpacing/letterSpacing 反查 MainActivity、PlatformSettings、字体页、SettingsRepository 与 SettingsCache；写入和读取均存在。theme/palette 保留未 featured 枚举是兼容历史已保存值的读取路径；[fromKey 与 pickerEntries](../../app/src/main/java/com/lucent/app/ui/ThemeModes.kt#L230-L237) 区分支持值和当前展示值，不能据不展示直接删历史值。
- **旧标识/常量**：PALETTE_CYCLE、PALETTE_RANDOM 反查 [根主题分支](../../app/src/main/java/com/lucent/app/MainActivity.kt#L194-L207) 与背景设置页的选择/动画开关约束；SYSTEM_FONT_KEY 反查 resolver、系统选项和删除回退。PlatformDiffuseBackground 文件名与内容职责不同不是失效功能的证据。
- **UI 状态**：LocalPureWhite 反查 Glass/UiComponents/来源卡片；LocalHazeState 反查主根、HomeTabs、笔记本/设置 hazeSource，仍是公共依赖，不认定废弃。rememberDynamicColorActive/rememberPureWhiteActive 反查主题页与背景页锁定消费者；已定位重复有效模式分歧而非仅建议统一架构。
- **资源**：lucent_window_background 反查 Theme.Lucent.windowBackground，Theme.Lucent 再反查 [应用 Manifest](../../app/src/main/AndroidManifest.xml#L11-L18)，日夜资源都有消费。I18n.a11yDragToReorder 也被 API 设置消费，不能因为笔记本缺少排序语义而删除该文本。
- **依赖/测试/配置**：baselineprofile、profileinstaller、baseline-prof/startup-prof 反查模块配置/app依赖/CI，生成导入缺口见第4节；Haze 属公共视觉功能。既有 slots/palette/titles/stats 测试已读，未删测试或 lint baseline，也未把 lint baseline 与运行时 baselineprofile 混为一谈。

本范围结论：未发现可安全直接删除的整条旧功能链；仅有名字、隐藏枚举或单处搜索未命中都不足以删除。没有修改 Kotlin/XML/Manifest/Gradle/缓存或持久化字段。
