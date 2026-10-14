# Settings / App / Build 审查

## 基线、方法和验收口径

- 审查基线：`9c3df7b8002c429c487529b8195139c13bfe9c03`，仓库 HEAD 与之相同。仅新增本报告，不修改产品源码、不提交推送、不在本地打包 APK/AAB。
- 指定范围全部逐行完整读取；长文件按区间继续读取。RootSettings 相关包括根页面、路由、面包屑、SettingsNav 与主页挂载关系，不包含生成的 build 输出。
- 代入个人 App 验收：结果可预期、身份不串线、不误删、失败可理解、取消可继续、状态合理恢复、手势不冲突。只列实质问题；依赖不是最新、命名/行数、评分、纯架构偏好不构成缺陷。
- 下列六项是**源码确认缺陷**，未声称已在真机复现。性能与设备相关项目另列风险，不编造帧率、启动耗时或 ANR 测量。

## 源码确认缺陷（六项）

### S1 · P2：删除当前方案之前的非当前 API，当前选择跳到其他方案

- 位置：[SettingsScreen.kt:93-101](<../../app/src/main/java/com/lucent/app/ui/SettingsScreen.kt#L93-L101>)。
- 触发：列表 A、B、C，当前 B（index=1），确认删除 A（index=0）。
- 证据：删元素后仍用旧 `selectedProfileIdx.coerceIn`；新列表 B、C 中 index=1 已是 C；随后把 C 的 URL/密钥/模型加载并持久化为选择。删除前置元素应保持 B 身份，却切到 C。当前草稿也被覆盖。
- 建议：按当前 profile.id 查新索引；若删的就是当前方案，才选择明确的邻项。最小回归覆盖删前/删后/删当前/删最后一个，无需改整体架构。

### S2 · P2：模型获取/连接测试异步结果没有绑定方案身份，可能跨方案显示或应用

- 位置：[ApiSettingsPage.kt:509-518](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L509-L518>)、[ApiSettingsPage.kt:572-577](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L572-L577>)、[ApiSettingsPage.kt:805-840](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L805-L840>)、[ApiSettingsPage.kt:1043-1079](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L1043-L1079>)、[ApiSettingsPage.kt:1161-1173](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L1161-L1173>)。
- 触发：在 A 点击获取模型，慢响应期间切 B；A 返回后弹出选择器，在 B 上确认并保存。或 A 测试期间切 B，A 测试完成。
- 证据：请求协程不因 selectedProfileIdx 变化取消；返回直接写共享 fetchedModels/pickerOpen/lastTestResult。清状态的 LaunchedEffect 只清现有结果，不拒绝迟到结果，且不清 fetchedModels。选择器完成时用当前 models 与当前回调更新 B。测试也可把 A 的成功状态显示在 B 下。获取按钮没有 enabled=!loading 或同步防重复门，连续点击时旧结果还能覆盖新请求。
- 影响：错误模型被当作另一服务的模型保存，或用户误判当前配置连接成功。
- 建议：请求捕获 profile.id + 协议/URL/密钥等配置快照 + 请求序号，返回只接受身份与序号仍匹配的结果；切方案取消/清除相应请求、选择器与 fetchedModels。禁重复获取；测试允许切配置时同样校验目标快照。无需引入新的网络框架。

### S3 · P2：获取模型时离开 API 路由，重进后加载状态残留

- 位置：[SettingsScreen.kt:72-73](<../../app/src/main/java/com/lucent/app/ui/SettingsScreen.kt#L72-L73>)、[SettingsScreen.kt:190-225](<../../app/src/main/java/com/lucent/app/ui/SettingsScreen.kt#L190-L225>)；[ApiSettingsPage.kt:508-508](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L508-L508>)、[ApiSettingsPage.kt:811-828](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L811-L828>)。
- 触发：获取请求未完成时点“设置”面包屑/返回根设置，再进入 API。
- 证据：loading 放在持续保留的 SettingsScreen；请求放在 API 子页 rememberCoroutineScope，子页离开 composition 后取消。唯一 false 回写在挂起调用成功返回之后，没有 finally；网络 withContext(IO) 的取消返回会跳过回写。
- 影响：返回页面无请求却持续显示转圈；用户无法区分取消与仍在工作。并非按钮被永久禁用，重新获取可能恢复。
- 建议：让 loading 与请求同生命周期，或 try/finally 复位；若允许并发需按请求序号复位，避免旧请求误清新请求状态。验收“取消后重进”和“请求失败后重试”。

### S4 · P3：空笔记本被显示为“1 个来源”

- 位置：[NotebooksScreen.kt:146-154](<../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt#L146-L154>)、[NotebooksScreen.kt:217-220](<../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt#L217-L220>)、[NotebooksScreen.kt:289-295](<../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt#L289-L295>)；核对 [Daos.kt:59-63](<../../app/src/main/java/com/lucent/app/data/Daos.kt#L59-L63>) 与 [NotebookDetailScreen.kt:102-109](<../../app/src/main/java/com/lucent/app/ui/NotebookDetailScreen.kt#L102-L109>)。
- 触发：新建空笔记本后返回列表，或移除最后一个来源。
- 证据：创建只插入 Notebook，计数无行回退为0后却 coerceAtLeast(1)。详情实际来源为0，列表稳定显示1，非加载占位偶发现象。
- 建议：最低值改为0；计数尚未到达可用占位，不应伪造来源。回归0/1/多来源与删最后来源。

### S5 · P2：配置变化重建 Activity，未保存的 API 编辑无提示丢失

- 位置：[SettingsScreen.kt:65-71](<../../app/src/main/java/com/lucent/app/ui/SettingsScreen.kt#L65-L71>)，[AndroidManifest.xml:19-23](<../../app/src/main/AndroidManifest.xml#L19-L23>)。
- 触发：修改名称、地址、密钥或模型尚未保存，旋转屏幕或系统配置变化导致 Activity 重建。
- 证据：草稿仅 remember(activeApiProfile)，重建后重新取持久化 profile；没有 rememberSaveable/其他草稿恢复。Manifest 未拦截重建。这不是主动点切换方案而丢弃草稿的产品选择，而是系统打断编辑。
- 建议：草稿按 profile.id 保存在可跨配置重建的最小状态容器；对密钥是否写 SavedState 作明确权衡，可仅使用 Activity 保留状态承载密钥。无需全面 ViewModel 化。保存成功才更新基线；验收重建后能继续编辑、不误保存其他方案。

### S6 · P3：正式发布可把旧 tag 与当前提交构建的 APK 配成一套

- 位置：[release.yml:44-64](<../../.github/workflows/release.yml#L44-L64>)、[release.yml:80-85](<../../.github/workflows/release.yml#L80-L85>)、[release.yml:153-164](<../../.github/workflows/release.yml#L153-L164>)。
- 触发：在新提交手动运行 Release，输入已经存在且指向旧提交的 tag；版本校验只验证数字格式，未校验 tag 指向。
- 证据：可复用构建 workflow 在当前工作流提交构建；更新 existing release 分支仅刷新 notes/覆盖 APK，不校验或移动 tag；即使只存在 tag 而无 release，--target 也不会改变已有 tag。源码归档对应旧代码，APK/Build info 对应新代码。
- 建议：构建前确认已有 tag 解析到本次 SHA，不一致就失败提示；或者明确 checkout tag 进行构建并用实际 SHA 写发布说明。不要自动强推 tag。个人自用也应能按 tag 找回真实构建源码。

## 长文件细节与最小拆分边界（非按行数扣分）

1. **API 页面**：[ApiSettingsPage.kt:475-578](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L475-L578>) 把远端请求、方案切换、模型选择草稿、测试结果和拖拽混在同一组合生命周期；实际可验证后果是 S2/S3。可只抽出“方案身份 + 草稿 + 请求快照/序号 + 提交/取消”的状态持有器，页面保留布局。拖拽块 [ApiSettingsPage.kt:520-746](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L520-L746>) 可作为独立纯展示子组件；已有 GlobalUserAgentSection 已是合理边界，不需要再拆。
2. **保存一致性**：[SettingsScreen.kt:75-132](<../../app/src/main/java/com/lucent/app/ui/SettingsScreen.kt#L75-L132>) 中切换/新增/删除/重排/保存全部以当前 profiles 快照做全量覆盖。连续事件若发生在 DataStore 新值回流前，会形成旧快照覆盖后操作的风险；目前未做 Compose 调度/慢存储真机复现，**不额外算源码确认 bug**。建议把一次 UI 事件的 profile.id 与 draft 快照一次性提交，串行化或乐观更新本地列表再持久化，失败保留草稿并反馈；保存按钮可仅在提交期间防重复。[ApiSettingsPage.kt:855-873](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L855-L873>) 的 onModelsChange/onSelectedModelChange 后即 onSaveProfile，当前父级写的是同一 Snapshot state，不能仅因 callback 连续就断言读取旧值；显式传最终快照更易验证。
3. **主程序**：[MainActivity.kt:100-145](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L100-L145>) 的启动读取与清理、[MainActivity.kt:147-252](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L147-L252>) 的主题/密度、[MainActivity.kt:272-479](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L272-L479>) 的导航 shell 各有真实职责边界。可按这三块抽函数/Composable，不必改导航框架。按钮同时改 currentScreen 并启动 pager 动画，KeepAliveTabs 又在 activeTab 改变时启动动画（[MainActivity.kt:449-460](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L449-L460>)、[HomeTabs.kt:144-154](<../../app/src/main/java/com/lucent/app/HomeTabs.kt#L144-L154>)）；建议只保留一个主动动画入口，连续点击是否抖动/反跳仍需真机。

## 需真机复测风险（不当作已测 bug）

- **冷启动主线程等待**：[MainActivity.kt:103-111](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L103-L111>) runBlocking 等 DataStore 首读，在首帧前阻塞 UI；磁盘慢、损坏恢复/低空间时需要量测。可异步加载并用现有默认背景过渡，但没有测到具体卡顿/ANR，不能标性能数值。
- **长连接响应主线程计算**：[ApiSettingsPage.kt:1122-1138](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L1122-L1138>) 每次重组重新 prettyResponseBody，JSON 解析及美化实际在组合线程；网络原始正文不截断。可把美化放 Default 并按结果缓存，预览分页/限显示而复制保留全文。需测大 JSON + 滚动/点击，未量测帧率。
- **字体导入/删除**：[FontSettingsPage.kt:82-119](<../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L82-L119>) 的 provider query 在回调主线程；IO 导入期间仍可关闭命名框/切路由；普通文件 IO 不随协程取消立即停止。[FontSettingsPage.kt:544-551](<../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L544-L551>) 删除及索引写入同步发生于点击线程，导入与删除并发还需复测。验收慢 provider、取消、切页、磁盘失败，不以“调用同步IO”直接声称已卡顿。
- **状态恢复**：[NotebooksScreen.kt:76-79](<../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt#L76-L79>) openNotebookId 仅 remember，而全局 AppNavigation.activeNotebookId 可能跨 Activity 存留到下一次 effect；重建会回列表。[AppNavigation.kt:24-43](<../../app/src/main/java/com/lucent/app/AppNavigation.kt#L24-L43>) API 返回来源仅进程内。需重建/进程回收实测确认 header/inset/返回行为。
- **手势、键盘与触控**：ApiModelPickerDialog 的手柄关闭、内部列表滑动、字体命名框、笔记本重排/菜单返回、大字号/窄屏布局需要真机。静态 design 分数不是验收结论。
- **字体预览交叉项**：独立核对 [MainActivity.kt:217-235](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L217-L235>) 对 Density 乘 savedFontScale，而 [FontSettingsPage.kt:189-201](<../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L189-L201>) 预览字号再乘 activeFontScale，松手保存后的预览会双重乘字号。此项由视觉审查统一计数，此处不重复第七项；以以上两处有效源码链接交叉定位，最终总报告合并去重。

## 验收场景清单

|旅程|动作与边界|期望/源码结论|
|---|---|---|
|首次启动|全新安装、首次进入设置、无 API 密钥|默认方案可编辑、空模型可理解；已审初始化，启动等待需量测|
|空配置|删除所有 API，退回再进、重启|保留空数组，不复活默认方案；parseStored 与初始化判空逻辑正确|
|方案删改|A/B/C 当前 B，删 A、删 C、删 B；重排后再保存|保持身份；删 A 违反 S1|
|异步跨方案|A 慢获取/测试，切 B；修改 URL/协议；连续获取|旧响应不能应用新配置；S2|
|取消/失败|获取期间返回根设置、重进；401/429/空数组/超时后重试|取消停止loading，失败反馈后可重试；S3，其余错误展示已审|
|编辑恢复|编辑 API 草稿→旋转/改系统深浅色/后台回收|配置重建不无提示丢草稿；S5；进程回收需真机|
|空笔记本|创建→返回列表；移除最后来源|显示0来源，列表详情一致；S4|
|笔记本导航|来源页返回、滑动中返回、模型管理→API→返回|先回对话再离开；导航纯函数已有测试，实际手势需真机|
|字体|合法/伪字体、12槽上限、慢文件导入→取消/切页，删除当前字体|失败解释；取消不留下不可见工作/误改，需真机|
|排版/主题|最大字号、最大行距、动态色/留白互斥、背景动画关开|不遮按钮，锁定状态能解释；静态逻辑通过，布局/预览交叉项复测|
|长文响应|大 JSON、长异常正文、滚动、复制全文|不因每次重组重复美化卡 UI；风险未量测|
|发布|新 tag、已有同 SHA tag、已有不同 SHA tag|构建源码与 tag/说明一致；S6|

## 标准检查实际结果

- 使用 bundled Python 执行 `python -m unittest discover -s tools/tests -v`：**30 tests，全通过**。
- `python tools/verify.py quick`：代码卫生、版本一致、缓存扫描、滚动自检/扫描五项硬检查通过；扫描111 Kotlin文件、0嵌套滚动问题。design 79/100、5条参考 warning，不升级为本报告缺陷。
- 首次命令因 Python Windows 路径转义导致 CommandNotFound，修正为正斜线后完整运行通过；不是产品缺陷。
- 未执行 Gradle 编译/JVM/Lint、未运行 Android UI/设备测试、未检查 Actions 成功状态、未打包；现有 build 缓存不能当作本轮验证。i18n 生成脚本完整读取但未执行，避免改产品文件。

## 完整读取覆盖清单

以下“已审未发现重要问题”仅表示本次范围的静态结论，不表示真机所有场景通过。

|文件|结论|
|---|---|
|[SettingsComponents.kt](<../../app/src/main/java/com/lucent/app/ui/SettingsComponents.kt>)|已审未发现重要问题|
|[SettingsScreen.kt](<../../app/src/main/java/com/lucent/app/ui/SettingsScreen.kt>)|S1、S5；配合 S2/S3|
|[PlatformSettings.kt](<../../app/src/main/java/com/lucent/app/ui/PlatformSettings.kt>)|已审未发现重要问题|
|[SettingsScrollMemory.kt](<../../app/src/main/java/com/lucent/app/ui/SettingsScrollMemory.kt>)|已审未发现重要问题|
|[LastScreen.kt](<../../app/src/main/java/com/lucent/app/ui/LastScreen.kt>)|已审未发现重要问题|
|[SettingsRoute.kt](<../../app/src/main/java/com/lucent/app/ui/SettingsRoute.kt>)|已审未发现重要问题|
|[NotebooksScreen.kt](<../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt>)|S4；恢复风险|
|[NotebookDetailScreen.kt](<../../app/src/main/java/com/lucent/app/ui/NotebookDetailScreen.kt>)|已审未发现重要问题|
|[RootSettingsPage.kt](<../../app/src/main/java/com/lucent/app/ui/settings/RootSettingsPage.kt>)|已审未发现重要问题|
|[ApiSettingsPage.kt](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt>)|S2、S3；长响应风险|
|[BackgroundSettingsPage.kt](<../../app/src/main/java/com/lucent/app/ui/settings/BackgroundSettingsPage.kt>)|已审未发现重要问题|
|[FontSettingsPage.kt](<../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt>)|导入边界风险；字体预览交叉项|
|[ThemeSettingsPage.kt](<../../app/src/main/java/com/lucent/app/ui/settings/ThemeSettingsPage.kt>)|已审未发现重要问题|
|[MainActivity.kt](<../../app/src/main/java/com/lucent/app/MainActivity.kt>)|启动阻塞、导航恢复风险；字体预览交叉项|
|[AppNavigation.kt](<../../app/src/main/java/com/lucent/app/AppNavigation.kt>)|已审未发现重要问题|
|[HomeTabs.kt](<../../app/src/main/java/com/lucent/app/HomeTabs.kt>)|已审未发现重要问题|
|[Utils.kt](<../../app/src/main/java/com/lucent/app/Utils.kt>)|已审未发现重要问题|
|[LucentBuild.kt](<../../app/src/main/java/com/lucent/app/LucentBuild.kt>)|已审未发现重要问题|
|[build.gradle.kts](<../../app/build.gradle.kts>)|已审未发现重要问题|
|[build.gradle.kts](<../../build.gradle.kts>)|已审未发现重要问题|
|[settings.gradle.kts](<../../settings.gradle.kts>)|已审未发现重要问题|
|[gradle.properties](<../../gradle.properties>)|已审未发现重要问题|
|[libs.versions.toml](<../../gradle/libs.versions.toml>)|已审未发现重要问题|
|[windows-test-classpath.gradle.kts](<../../gradle/windows-test-classpath.gradle.kts>)|已审未发现重要问题|
|[gradle-wrapper.properties](<../../gradle/wrapper/gradle-wrapper.properties>)|已审未发现重要问题|
|[gradlew](<../../gradlew>)|已审未发现重要问题|
|[gradlew.bat](<../../gradlew.bat>)|已审未发现重要问题|
|[build.gradle.kts](<../../baselineprofile/build.gradle.kts>)|已审未发现重要问题|
|[AndroidManifest.xml](<../../baselineprofile/src/main/AndroidManifest.xml>)|已审未发现重要问题|
|[BaselineProfileGenerator.kt](<../../baselineprofile/src/main/kotlin/com/lucent/app/baselineprofile/BaselineProfileGenerator.kt>)|已审未发现重要问题|
|[proguard-rules.pro](<../../app/proguard-rules.pro>)|已审未发现重要问题|
|[lint-baseline.xml](<../../app/lint-baseline.xml>)|已审未发现重要问题|
|[AndroidManifest.xml](<../../app/src/main/AndroidManifest.xml>)|已审未发现重要问题|
|[backup_rules.xml](<../../app/src/main/res/xml/backup_rules.xml>)|已审未发现重要问题|
|[data_extraction_rules.xml](<../../app/src/main/res/xml/data_extraction_rules.xml>)|已审未发现重要问题|
|[colors.xml](<../../app/src/main/res/values/colors.xml>)|已审未发现重要问题|
|[colors.xml](<../../app/src/main/res/values-night/colors.xml>)|已审未发现重要问题|
|[strings.xml](<../../app/src/main/res/values/strings.xml>)|已审未发现重要问题|
|[themes.xml](<../../app/src/main/res/values/themes.xml>)|已审未发现重要问题|
|[ic_launcher.xml](<../../app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml>)|已审未发现重要问题|
|[ic_launcher_round.xml](<../../app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml>)|已审未发现重要问题|
|[check.yml](<../../.github/workflows/check.yml>)|已审未发现重要问题|
|[build-apk.yml](<../../.github/workflows/build-apk.yml>)|已审未发现重要问题|
|[release.yml](<../../.github/workflows/release.yml>)|S6|
|[verify.py](<../../tools/verify.py>)|已审未发现重要问题|
|[code_hygiene_check.py](<../../tools/code_hygiene_check.py>)|已审未发现重要问题|
|[compose_scroll_check.py](<../../tools/compose_scroll_check.py>)|已审未发现重要问题|
|[design_audit.py](<../../tools/design_audit.py>)|已审未发现重要问题|
|[settings_cache_check.py](<../../tools/settings_cache_check.py>)|已审未发现重要问题|
|[version_check.py](<../../tools/version_check.py>)|已审未发现重要问题|
|[release_notes_check.py](<../../tools/release_notes_check.py>)|已审未发现重要问题|
|[test_inventory.py](<../../tools/test_inventory.py>)|已审未发现重要问题|
|[test_report_summary.py](<../../tools/test_report_summary.py>)|已审未发现重要问题|
|[gen_i18n.py](<../../tools/i18n/gen_i18n.py>)|已审未发现重要问题|
|[catalog.py](<../../tools/i18n/catalog.py>)|已审未发现重要问题|
|[test_approved_ux_contract.py](<../../tools/tests/test_approved_ux_contract.py>)|已审未发现重要问题|
|[test_chat_selection_contract.py](<../../tools/tests/test_chat_selection_contract.py>)|已审未发现重要问题|
|[test_cloud_validation.py](<../../tools/tests/test_cloud_validation.py>)|已审未发现重要问题|
|[test_deletable_items_contract.py](<../../tools/tests/test_deletable_items_contract.py>)|已审未发现重要问题|
|[test_picker_contract.py](<../../tools/tests/test_picker_contract.py>)|已审未发现重要问题|
|[test_popup_ime_contract.py](<../../tools/tests/test_popup_ime_contract.py>)|已审未发现重要问题|

### 为核对问题补读的依赖/回归测试

- [SettingsRepository.kt](<../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt>)：完整读取，作为上述身份、IO/密度、计数证据。
- [ApiProfile.kt](<../../app/src/main/java/com/lucent/app/data/ApiProfile.kt>)：完整读取，作为上述身份、IO/密度、计数证据。
- [FontStore.kt](<../../app/src/main/java/com/lucent/app/data/FontStore.kt>)：完整读取，作为上述身份、IO/密度、计数证据。
- [LucentFonts.kt](<../../app/src/main/java/com/lucent/app/ui/LucentFonts.kt>)：完整读取，作为上述身份、IO/密度、计数证据。
- [ModelPickerDrawer.kt](<../../app/src/main/java/com/lucent/app/ui/ModelPickerDrawer.kt>)：完整读取，作为上述身份、IO/密度、计数证据。
- [Daos.kt](<../../app/src/main/java/com/lucent/app/data/Daos.kt>)：完整读取，作为上述身份、IO/密度、计数证据。
- [LlmClient.kt:1-390](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L1-L390>)：补读1–390行，核对模型获取/测试调用与格式化；不宣称本报告完整审查网络长文件。
- [HomeNavigationTest.kt](<../../app/src/test/java/com/lucent/app/ui/HomeNavigationTest.kt>)：完整读取，未在本轮执行 Kotlin 测试。
- [NotebookDetailTabsTest.kt](<../../app/src/test/java/com/lucent/app/ui/NotebookDetailTabsTest.kt>)：完整读取，未在本轮执行 Kotlin 测试。
- [HomeTabTest.kt](<../../app/src/test/kotlin/com/lucent/app/HomeTabTest.kt>)：完整读取，未在本轮执行 Kotlin 测试。
- [TextSanitizationTest.kt](<../../app/src/test/kotlin/com/lucent/app/TextSanitizationTest.kt>)：完整读取，未在本轮执行 Kotlin 测试。

## 本范围正向与反向残留审计

### 正向：功能 → 实现 → 失败路径

|功能|实现链（有效定位）|失败/取消核对|
|---|---|---|
|API 配置|[根入口](<../../app/src/main/java/com/lucent/app/ui/settings/RootSettingsPage.kt#L18>) → [草稿/提交](<../../app/src/main/java/com/lucent/app/ui/SettingsScreen.kt#L65-L132>) → [DataStore](<../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt#L239-L250>)|S1/S5；存储失败恢复仍需验收|
|获取模型/测试|[按钮与回调](<../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L805-L840>) → [网络实现](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L203-L272>) → [选择器](<../../app/src/main/java/com/lucent/app/ui/SettingsComponents.kt#L215-L407>)|网络错误有正文/提示，身份切换和取消 S2/S3|
|主题/背景/排版|[动态色互斥](<../../app/src/main/java/com/lucent/app/ui/PlatformSettings.kt#L31-L86>) → [设置页](<../../app/src/main/java/com/lucent/app/ui/settings/ThemeSettingsPage.kt#L38-L87>) → [启动/运行渲染](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L147-L252>)|不支持版本禁用；纯白/动态色锁说明存在；大字号需验收|
|字体导入/删除|[文件选择/导入](<../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L82-L119>) → [字体文件与索引](<../../app/src/main/java/com/lucent/app/data/FontStore.kt#L99-L140>) → [解析缓存](<../../app/src/main/java/com/lucent/app/ui/LucentFonts.kt#L16-L38>)|非字体/槽满/通用失败有反馈；取消与并发风险前述|
|笔记本 CRUD/导航|[列表创建删除](<../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt#L113-L175>) → [详情/返回](<../../app/src/main/java/com/lucent/app/ui/NotebookDetailScreen.kt#L51-L109>) → [应用返回协调](<../../app/src/main/java/com/lucent/app/AppNavigation.kt#L19-L99>)|删除有确认与不可恢复说明；列表计数 S4；数据库异常真机失败注入未执行|
|发布/验证|[CI 检查](<../../.github/workflows/check.yml>) → [构建签名](<../../.github/workflows/build-apk.yml>) → [正式发布](<../../.github/workflows/release.yml>)|构建失败退出且上传报告；签名缺失明确警告；tag一致性 S6|

### 反向：字段/键/依赖/资源/旧标识 → 消费者

- [设置键与 Flow](<../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt#L16-L100>) 回溯到设置页、MainActivity 和网络 UA；标准缓存检查11读/11写一致。该检查仅正则存在性，不证明所有错误路径恢复、异步顺序或持久化成功。
- 对 RootSettings、SettingsNav、SettingsScrollMemory、LastScreen、LucentBuild 执行全源码反查：根设置被路由消费，面包屑透过 SettingsNav 调用，滚动记录由 SettingsScreen读写，LastScreen由主页初始化/更新及导航测试消费。LucentBuild.VERSION 未见产品运行时消费者，但有 [版本检查](<../../tools/version_check.py#L19-L43>)，属于构建一致性标识；仅“运行时未用”不报实质残留。
- 旧 api_key_enc、api_profiles_json_enc、app_lock_enabled、last_screen 反查当前源码只出现在 [历史键清理集合](<../../app/src/main/java/com/lucent/app/data/SettingsRepository.kt#L106-L191>)，由 [启动清理](<../../app/src/main/java/com/lucent/app/MainActivity.kt#L134-L137>) 消费；这是有用途的兼容清理，不视为必须删除的废弃垃圾。本报告不代替跨模块历史迁移审计。
- 从 [依赖声明](<../../app/build.gradle.kts#L116-L145>) 回溯：Compose/Activity为界面和宿主、Room为DAO、DataStore为设置、OkHttp为模型/测试、Haze为玻璃层；测试依赖由测试/云端流程使用。未因“未升级最新”要求更换依赖。ProfileInstaller/baseline profile是否实际安装优化配置需要构建产物验证；不能仅凭模块存在宣称启动变快。
- 从 [Manifest](<../../app/src/main/AndroidManifest.xml>) 回溯 Activity/Service与权限声明；从 [主题XML](<../../app/src/main/res/values/themes.xml>) 回溯 windowBackground两种明暗资源，启动继续以设置值覆盖。备份XML被Manifest引用且数据库/偏好/文件域全排除，不擅自要求个人app恢复云备份。图标 round XML 未被Manifest引用属低影响资源，不升级为实质问题。
- 从 [Python契约测试](<../../tools/tests/test_cloud_validation.py>) 与 [验证入口](<../../tools/verify.py>) 回溯 workflow消费者；已运行检查结果与未运行项目明确分开。纯字符串源码契约不能覆盖 S1–S5 的运行时身份与生命周期边界。

