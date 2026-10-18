# 源码契约 → Kotlin 行为覆盖审计（2026-10-18）

## 范围与判定

- 目标是准确映射、保留结构护栏，不把源码字符串断言当行为测试，不建议为减少数量删除护栏。逐函数列出当前 [tools/tests](<../../tools/tests/>) 的纯源码/配置/资产契约。已读测试函数体与实际断言；没有证据的写“缺口”，辅助函数单测与真实 UI 接线区分为“部分”。
- “已有”以主代理指定总基线 `0a5d13c` 为界；“本轮新增”包括本地 `b476c46` 的 UI 扩充，以及审计时已落盘的共享抽屉、composer/window、slider、selection、reorder 与文本派生测试。前两种已有 picker UI 用例已改名/加强，在表中明确旧版仅覆盖的范围，不伪造旧名为当前名。
- 这里只审查测试定义，不运行 Gradle、不运行工具测试、不提交；**覆盖存在不等于已编译/执行通过**。并行代理仍在编辑其他文件，本文是读取时快照。本子任务仅新增本文档。
- 排除实际调用审计/校验工具或 shell 的行为测试：[test_source_audit.py](<../../tools/tests/test_source_audit.py>) 全部；[test_design_whitelist.py](<../../tools/tests/test_design_whitelist.py>) 全部（含 repository strict audit）；[test_cloud_validation.py](<../../tools/tests/test_cloud_validation.py>) 的 ReportValidation/InventoryBehavior/LocalGuard 类；[test_signing_contract.py](<../../tools/tests/test_signing_contract.py>) 前5个执行 Bash 的用例。后两文件的纯文本契约仍逐个列入。

## 可核验 Kotlin 行为证据索引

以下短编号在表格里指向实际 test 方法，不把类名本身视作覆盖。

- **P1** [ProfileModelPickerSheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ProfileModelPickerSheetTest.kt>)：`pickingAModelReportsTheSelectionBeforeClosingTheSheet；clickingTheCurrentModelStillReportsItAndCloses`。点击模型/当前模型，selected语义，精确 select→dismiss 事件序列。
- **P2** [ProfileModelPickerSheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ProfileModelPickerSheetTest.kt>)：`expandingAndCollapsingGroupsNeverSelectsAModelOrDismisses；managingApiDoesNotSelectOrDismissAndRestoresTheExpandedGroup`。真实组切换无业务回调；管理只发manage；visible重开保留展开。
- **P3** [ProfileModelPickerSheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ProfileModelPickerSheetTest.kt>)：`emptyProfileCannotExpandButManagementRemainsAvailable；noProfilesShowsEmptyStateAndManagementStillDoesNotDismiss`。空方案不可展开/空列表提示，管理仍可用且不dismiss。
- **A1** [ApiModelPickerDialogTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ApiModelPickerDialogTest.kt>)：`selectingAllThenConfirmingReportsEveryNameBeforeDismissal`。全选前不提交、确认集合与done→dismiss顺序。
- **A2** [ApiModelPickerDialogTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ApiModelPickerDialogTest.kt>)：`individualRowsToggleDraftAndOnlyConfirmationSaves；clearingAllSavesAnEmptySelectionOnlyAfterConfirmation`。逐行切换/清空仅改草稿，明确确认后才提交。
- **A3** [ApiModelPickerDialogTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ApiModelPickerDialogTest.kt>)：`cancellingEditedSelectionDiscardsDraftWhenReopened；emptyModelListCanBeConfirmedWithoutInventingSelections`。真实Back取消无提交、重开原选择；空列表可确认空集。
- **H1** [ChatHistorySheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ChatHistorySheetTest.kt>)：`emptyHistoryShowsExplanationWithoutJumpActionsOrCallbacks`。空态/计数，无top/bottom与任何事件。
- **H2** [ChatHistorySheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ChatHistorySheetTest.kt>)：`jumpAndMessageClicksReportCorrectCallbacksWithoutDismissing`。top、bottom、重复当前项点击与ID序列，selected动态变化，不dismiss。
- **H3** [ChatHistorySheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ChatHistorySheetTest.kt>)：`summariesExposeBodyReasoningStreamingAndNoBodyWithCorrectMessageIds；openingHistoryPositionsTheCurrentMessageInView`。正文优先/推理/streaming/noBody与ID；初始当前消息20可见，1不在树中。
- **S1** [DialogueSettingsSheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/DialogueSettingsSheetTest.kt>)：`editingPromptAndRoundsDoesNotSaveUntilSaveIsClicked`。提示编辑/计数、增减轮次；保存trim，保存不dismiss。
- **S2** [DialogueSettingsSheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/DialogueSettingsSheetTest.kt>)：`restoringDefaultsOnlyChangesDraftUntilExplicitSave`。恢复默认仅草稿；保存后空prompt与10轮，不dismiss。
- **S3** [DialogueSettingsSheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/DialogueSettingsSheetTest.kt>)：`cancellingEditedDraftOrRestoredDefaultsNeverSavesAndReopensOriginalValues`。编辑/恢复默认后Back取消均不保存，重开原值；测试显式clearFocus。
- **S4** [DialogueSettingsSheetTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/DialogueSettingsSheetTest.kt>)：`historyRoundButtonsRespectBothBoundsAndSaveTheChosenLimit`。1下界禁减、20上界禁增，保存20轮。
- **D** [SharedDrawerInteractionTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/SharedDrawerInteractionTest.kt>)（本轮新落盘10个）：`profileSystemBackDismissesOnceWithoutSelecting`、`apiSystemBackDismissesOnceWithoutSaving`、`profileOutsideScrimDismissesOnceWithoutSelecting`、`apiOutsideScrimDismissesOnceWithoutSaving`、`profileTitleDragBeyondThresholdDismisses`、`apiTitleDragBeyondThresholdDismisses`、`profileSmallSlowHandleDragReboundsWithoutClosing`、`apiSmallSlowHandleDragReboundsWithoutClosing`、`profileListScrollAndTopOverscrollNeverDismiss`、`apiListScrollAndTopOverscrollNeverDismiss`。已读共用helper：真实window外点/Back、标题120dp慢拖关闭且仅一次dismiss/零业务回调；handle40dp先验证真实位移再回弹；列表上下滚动量改变与顶部overscroll不动drawer。**仅两picker入口，未测history/dialogue，未测IME；主代理告知D已编译，未本地设备执行，最终验证以主代理结果为准。**
- **M** [AppDatabaseMigrationTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/data/AppDatabaseMigrationTest.kt#L779-L874>)：`fullChain25To35_preservesNotesAndChatAndValidatesCurrentRoomSchema` 实际创建25数据库、插入两notes/两chat、26中间核对、Room验证35、保留列内容/统计默认/旧对象清除与索引。

### 后续已落盘行为证据（仅更新映射）

- **C** [BottomGlassComposerInteractionTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/BottomGlassComposerInteractionTest.kt>)：`reasoningButtonTogglesMenuWithoutSelectingPreset；everyPresetReportsTypedSelectionBeforeClosingPopup；inputRetainsFocusAcrossNonFocusablePopupOpenAndTouchSelection；historySettingsAndModelEntrypointsDispatchOnlyTheirOwnCallbacks；blankDraftDisablesSendAndStreamingAlwaysDispatchesStopInstead`。5例：真实anchor开关/全部preset回调后关popup、触摸打开/选择前后输入仍focused且能继续编辑、3入口回调隔离、空白禁发/streaming走stop。未挂载NotebookChatTab的外点observer，未断言厂商IME可见性。
- **W** [PopupImeWindowTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/PopupImeWindowTest.kt>)：`helperOnlyFlagsDialogAndClearsFlagWhenRemovedWhileDialogStaysOpen；helperPreservesPreExistingDialogFlagOnDisposal`。2例：实际Compose Dialog window与Activity window不是同一对象；helper添加ALT_FOCUSABLE_IM，移除helper后恢复原有flag状态且Dialog仍开，Activity flags不变。不是厂商键盘可见性测试。
- **T** [StepperSliderInteractionTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/StepperSliderInteractionTest.kt>)：`pressingWithoutDirectionDoesNotChangeValueAndTapCommitsOneSnappedValue；verticalMovementScrollsParentWithoutChangingSlider；horizontalDragCommitsAndCancelledDragRestoresOriginalWithoutFinish`。3例：真实down不commit、tap一次量化值/finish；纵滑父滚动无值改变；横拖commit且不父滚动，cancel回原值不多finish。
- **F** [SelectionFeedbackBehaviorTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/SelectionFeedbackBehaviorTest.kt>)：`darkWashAnimatesIntoAndOutOfSelection；lightWashAnimatesIntoAndOutOfSelection；disabledMotionSnapsBothDirectionsInDarkGlass；disabledMotionSnapsBothDirectionsInLightGlass；rowSemanticsAndRadioTouchEachDispatchExactlyOneSelectionAndLockedRowDispatchesNone`。5例：冻结时钟/像素采样校验暗亮wash双向单调中间帧与最终sRGB端点；禁动画双向snap；生产ThemeModeRow role/selected、radio无click语义、行/实际radio触摸各一次回调，locked无回调。不证明Repository实际只写一次。
- **R** [ApiProfileReorderInteractionTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ApiProfileReorderInteractionTest.kt>)：`unchangedDropRestoresSaveWithoutPersisting；changedDropStopsEdgeScrollAndRestoresSaveAfterSuccess；failedReorderAlsoRestoresSaveAndStopsEdgeScroll；pointerCancelDiscardsReorderRestoresSaveAndStopsEdgeScroll；deactivationCancelsHeldGestureRestoresSaveAndStopsEdgeScroll；lazyListEdgeEffectStopsAfterRealGestureFinish；lazyListEdgeEffectStopsAfterRealGestureCancel`。读取时实际7个@Test：生产ApiProfileListSection+ApiProfileSaveButton共用drag，真实长按及静止边缘触发滚动；保存禁用至模拟持久化completion成功/失败，再恢复；无变更/取消/失活不提交；实际reorderableList+ReorderAutoScrollEffect在finish/cancel后推进240ms不再滚。持久化回调为fixture，不是真DAO。
- **X** [ChatTextDerivationsComposeTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ChatTextDerivationsComposeTest.kt>)：`unrelatedStreamingUpdatesReuseSettledTextDerivationsAndEditsInvalidateThem；streamingTokensKeepPreviousExcerptUntilNextThrottledResult`。2例：稳定正文派生State在无关流式更新时复用，正文改动新State与新结果；streaming输入变更保持同一State，非null旧结果过渡后得到新excerpt。不是API modelChoices/prettyBody缓存测试。
- **XP** [ChatTextDerivationsTest.kt](<../../app/src/test/java/com/lucent/app/ui/ChatTextDerivationsTest.kt>)：`bodyWinsOverReasoningAndUsesExistingMarkdownPreviewRules`、`emptyStreamingBodyHidesReasoningUntilGenerationFinishes`、`excerptRetainsSeventyCharacterAndInputPrefixLimits`，3个纯函数用例实际断言正文优先、streaming隐藏推理/结束后回退、noBody、70字符与输入前缀限制。
- **G** [ChatGenerationLifecycleTest.kt](<../../app/src/test/java/com/lucent/app/chat/ChatGenerationLifecycleTest.kt>)：`stopKeepsGenerationActiveUntilFinalSaveCompletes`、`failedFinalSaveSettlesGenerationWithoutWaitingForRetry`、`stopAllRemainsActiveUntilLastSessionsSaveSettles`，3例以QueueDispatcher/CompletableDeferred门控保存，断言stop后保持running/streaming直到最终save结算，失败留pending，最后session结算才activeCount归零。**并未调用重生成入口或检查API key/stats复位，不标为regeneration契约覆盖**。
- 本增补未运行Gradle或设备测试；测试定义不等于通过，除D已编译的主代理通知外，编译/执行状态以主代理最终验证为准。

## 逐函数契约矩阵

“无”表示未找到足以映射的直接行为证据，而非断言仓库绝对不存在任何相关测试。“不适用”表示本质为结构/配置护栏，不强行要求Kotlin重复检查。

### [test_picker_contract.py](<../../tools/tests/test_picker_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_parent_saves_selection_without_interrupting_close_animation` | 父回调不直接移除 picker | P1 的旧版已有选择/关闭；未覆盖父组件 | P1：选择事件严格先于 dismiss | 部分：父组件持久化与动画完成先后没有集成断言 |
| `test_picker_keeps_selection_feedback_and_fixed_header_structure` | 选中色/粗体，标题与管理按钮非 Lazy item | 无同等视觉断言 | P1/P2：selected 语义、展开/折叠/管理实际点击 | 部分：颜色、字重、滚动时固定头缺口 |
| `test_initial_position_is_constructed_without_scroll_animation` | 初始 LazyListState，无滚动动画调用 | ModelPickerPositionTest.singleModelSelectionStillExpandsItsProfile；selectsActualModelAfterCollapsedPrecedingProfiles；selectedProfileAndModelChangesRecalculatePosition；missingSelectionAndEmptyProfilesDoNotExpandUnrelatedProfile；emptyProfileTargetsItsHeaderWithoutInventingAModel；normalizedModelsAndLegacyPrimaryUseSameOrderAsRenderedRows | 无 | 部分：只有位置计算；真实 picker 首帧定位/无跳动缺口 |
| `test_all_sheets_share_bright_handle_and_fixed_header` | 4 类 sheet 共用属性、handle，无旧 handle | 无 | D：仅 Profile/API 两入口真实抽屉交互 | 部分：History/Dialogue 手势及四类固定头/亮色视觉缺口 |
| `test_drawers_dismiss_from_backdrop_and_handle` | 返回/外点属性、40%白 handle、共享 dragModifier | ModelPickerDrawerTest.shortOrUpwardDragDoesNotDismiss；downwardDistanceOrFlingDismisses（纯阈值） | D：两入口 Back/scrim/标题拖拽/小拖回弹 | 部分：History/Dialogue 与真实高速 fling、handle 外观缺口 |
| `test_history_and_settings_callbacks_do_not_dismiss` | 父 history/settings/API picker 回调不直接移除 | 无 | H2：跳转/点行不 dismiss；S1/S2：保存不 dismiss；A1/A2/A3：确认才保存并关闭 | 部分：组件回调已测，NotebookChatTab/ApiSettingsPage 父接线未集成 |
| `test_extra_sheet_close_buttons_are_removed` | History/API/Dialogue 无额外 onClick=onDismiss 按钮 | 无 | 无 | 结构护栏；没有遍历 UI 证明额外关闭按钮不存在 |
| `test_manage_api_uses_existing_settings_without_overlay_or_dismiss` | 复用 Settings、隐藏而不销毁 picker 状态，无嵌套 Dialog | HomeNavigationTest.apiManagementUsesExistingSettingsAndReturnsToOwningNotebook；ordinaryApiSettingsReturnDoesNotReopenAModelPicker；staleNotebookAndOtherSettingsRoutesClearReturnOrigin | P2：管理回调不选择/关闭；visible false→true 保持展开 | 部分：状态函数+孤立组件，不是完整导航往返/滚动位置恢复 |
| `test_handle_moves_drawer_and_confirmation_hides_before_removal` | 共享 hide/translation/draggable；picker 无自建位移 | ModelPickerDrawerTest.shortOrUpwardDragDoesNotDismiss；downwardDistanceOrFlingDismisses | D：实际移动/回弹/标题关闭；A1：done→dismiss | 部分：未冻结时钟证明 hide 完成后移除，未测真实高速 fling |

### [test_history_entry_contract.py](<../../tools/tests/test_history_entry_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_history_action_is_between_reasoning_and_settings` | composer history 图标、按钮次序和入口接线 | 无 | C.historySettingsAndModelEntrypointsDispatchOnlyTheirOwnCallbacks：实际composer点入口，各回调隔离 | 部分：composer入口已测；NotebookChatTab打开sheet接线与排列/语义顺序仍缺 |
| `test_composer_controls_are_one_third_shorter_without_losing_width` | 3 个 36×24 控件与发送按钮/内边距 | 无 | 无 | 缺口：实际测量与触控命中范围 |
| `test_history_jump_buttons_preserve_glass_alpha` | 跳转 chip alpha 乘原始 alpha | 无 | H2：标签/提示与 top/bottom 回调 | 部分：无渲染 alpha 断言 |
| `test_regeneration_preflight_precedes_destructive_changes_and_resets_stats` | blank key 检查在 askModel 前；不就地改 messages/删消息（函数名提到 reset stats，但这里并未断言 stats） | 无直接对应证据 | 无 | 缺口：无 key 重生成无副作用、有效重生成统计复位；不可按函数名视作已测 |
| `test_rail_has_no_hidden_history_navigation` | rail 只有纵向拖动，坐标转换/命中区域；sources 使用 capsule | 无直接事件证据 | 无 | 缺口：拖动 setProgress、禁止点击打开历史、sources active 接线 |

### [test_popup_ime_contract.py](<../../tools/tests/test_popup_ime_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_reasoning_menu_preserves_editor_focus_and_owner_handles_back` | 非 focusable popup；owner BackHandler；不 clearFocus/控制键盘 | 无 | C.inputRetainsFocusAcrossNonFocusablePopupOpenAndTouchSelection：真实输入focused且继续编辑 | 部分：焦点保持已有；厂商IME可见性及owner Back优先级仍缺 |
| `test_reasoning_anchor_toggles_and_outside_press_does_not_reopen` | anchor toggle；Initial down 外点关闭不 consume；选择 preset 关闭 | 无 | C.reasoningButtonTogglesMenuWithoutSelectingPreset；everyPresetReportsTypedSelectionBeforeClosingPopup | 部分：anchor toggle/选择回调已测；NotebookChatTab外点observer透传、不重开仍缺 |
| `test_non_editing_sheets_use_only_their_dialog_window` | History/Profile 调 dialog helper；ALT_FOCUSABLE_IM，dispose 清 flag，不碰 Activity | 无 | W两例：实际Dialog flag增设/清理/保留已有flag，Activity flags不变 | 部分：helper窗口生命周期已有；实际History/Profile入口接线及厂商IME保持未集成 |
| `test_editors_remain_ime_eligible_and_do_not_force_show_keyboard` | Dialogue 保留可编辑焦点，仅原已显示时 requestFocus；不强 show | 无 | S1/S3：文本可编辑；S3 返回前主动 clearFocus | 部分：编辑可用不等于键盘保持；已显/未显 IME 两分支均缺 |
| `test_api_available_models_dialog_is_excluded` | API models 不采用保留 IME helpers/properties | 无 | A/D 未断言键盘 | 结构护栏；IME 隔离行为仍缺 |

### [test_count_slider_reorder_contract.py](<../../tools/tests/test_count_slider_reorder_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_zero_source_count_and_stable_reorder_owner` | 零 source 不伪造1；list级拖拽 owner、overlay/autoscroll | EdgeAutoScrollTest.emptyNotebookNeverInventsASource；ReorderAutoScrollTest.offscreenDragDropsIntoWholeListRatherThanVisibleSubset；ReorderSlotsTest.dragStateWithoutMovementClearsCompletelyOnRelease | R.lazyListEdgeEffectStopsAfterRealGestureFinish；lazyListEdgeEffectStopsAfterRealGestureCancel：实际生产list owner/effect | 部分：共享生产list拖拽/终止已测；真实Notebooks跨视口落盘与source计数显示仍缺 |
| `test_slider_defers_value_change_until_direction_or_tap` | 判方向后 commit；纵向退出；取消 restore；不用 Initial | StepperSliderGestureTest.downAndSubSlopJitterKeepWaiting；clearHorizontalMovementInEitherDirectionClaimsSlider；verticalMovementInEitherDirectionRemainsAvailableForScrolling；ambiguousDiagonalPastSlopFavorsScrolling；displacementIsMeasuredFromDownRatherThanIndividualSmallDeltas | T三例真实down/tap/纵滑/横拖/cancel；S1/S4按钮 | 行为已有直接触摸断言；复杂多指/快速换方向不在本用例范围，结构Pass约束仍保留 |
| `test_typography_card_compacts_spacing_without_shrinking_hit_targets` | Font 卡片间距/compact 次数；slider 命中高36dp | 无 | 无 | 缺口：实际布局尺寸、命中范围、字体设置手势 |
| `test_profile_save_and_list_share_the_reorder_guard` | page/list 同一 dragState；settled 控制保存 enabled | ApiProfileLogicTest.steppedMovesHeldProfileAndSelectionTogether；steppedIgnoresOutOfBoundsMoves（仅排序算法） | R.unchangedDropRestoresSaveWithoutPersisting；changedDropStopsEdgeScrollAndRestoresSaveAfterSuccess；failedReorderAlsoRestoresSaveAndStopsEdgeScroll；pointerCancelDiscardsReorderRestoresSaveAndStopsEdgeScroll；deactivationCancelsHeldGestureRestoresSaveAndStopsEdgeScroll | 部分：生产列表/保存按钮共享状态、禁用与恢复已有真实交互；整个ApiSettingsPage父接线及真实DAO落盘仍未集成 |
| `test_edge_loops_use_consumed_scroll_and_stop_with_drag` | 消费量补偿、token终止；API cancel；viewport modifier次序 | ReorderAutoScrollTest.actualConsumedScrollKeepsGrabbedItemUnderStationaryFinger；clampedScrollDoesNotMoveOverlayOrInventCompensation；stoppedOrCancelledDragRejectsLateScrollAndPointerEvents；newGrabResetsTravelAndCapturesFreshGeometry；EdgeAutoScrollTest.edgeVelocityIsDirectionalProgressiveAndCapped；invalidOrTinyViewportDoesNotExplode | R：静止边缘实际滚动，API drop/cancel/失活与Lazy finish/cancel后240ms不再滚 | 部分：真实生产edge-loop终止已有；精确consumed几何由已有纯状态测试补足；新旧token竞争及完整SettingsScreen坐标布局仍未端到端 |

### [test_chat_selection_contract.py](<../../tools/tests/test_chat_selection_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_each_bubble_body_uses_framework_selection` | 用户/AI 各一个 SelectionContainer 包正文；无自造长按/toolbar | 无 | [ChatMessageCardSelectionTest.kt](<../../app/src/androidTest/kotlin/com/lucent/app/ui/ChatMessageCardSelectionTest.kt>)：`userLongPressCopiesSelectedWordAndActionsRemainIndependent`、`aiMarkdownLongPressCopiesSelectedWordAndActionsRemainIndependent`，实际长按字词并等待两个SelectionHandleInfo句柄；Key.Copy后系统剪贴板为选中词，长按/复制不发业务回调 | 部分：用户/AI Markdown真实选词+系统复制已有定义；未拖句柄跨段，未点击系统toolbar菜单，未本地设备执行 |
| `test_existing_actions_stay_outside_selection_scope` | BubbleAction 与 copyText 不在 selection scope 内 | 无 | 同上两例每次重新长按后真实触摸全文复制/编辑/删除及AI重生成，断言回调顺序与剪贴板副作用隔离；同文件 `disabledUserActionsIgnoreTouchesWhileFullCopyRemainsAvailable`、`disabledAiActionsIgnoreTouchesWhileFullCopyRemainsAvailable` 断言disabled按钮触摸零回调/不改剪贴板，全文复制仍可用 | 部分：4个实际@Test覆盖选择时按钮独立/禁用行为与系统剪贴板；没有断言点击按钮后选区仍保留，也未验证真实页面编辑/重生成端到端；未本地设备执行 |

### [test_selection_feedback_contract.py](<../../tools/tests/test_selection_feedback_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_selection_fades_both_directions_in_draw_phase` | selectedCard 双向透明过渡、drawBehind、motionEnabled | 无 | F.darkWashAnimatesIntoAndOutOfSelection；lightWashAnimatesIntoAndOutOfSelection；disabledMotionSnapsBothDirectionsInDarkGlass；disabledMotionSnapsBothDirectionsInLightGlass | 行为已有时钟+像素双向/禁动画直接断言；draw阶段职责仍保留源码护栏 |
| `test_theme_row_owns_selection_and_radio_is_display_only` | Theme row selectedCard/Radio role；radio onClick null | 无 | F.rowSemanticsAndRadioTouchEachDispatchExactlyOneSelectionAndLockedRowDispatchesNone | 部分：生产row/radio语义与触摸单回调/locked已有；实际设置Repository写入次数未集成 |
| `test_disclosure_keeps_single_icon_and_rotation_in_layer` | picker 单箭头 graphicsLayer rotation，model selectedCard | 无 | P2：展开/折叠；P1：selected；F：通用selectedCard像素动画 | 部分：通用淡入淡出已有；picker箭头旋转角度/单图标实际呈现仍缺 |

### [test_deletable_items_contract.py](<../../tools/tests/test_deletable_items_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_notebooks_are_not_recreated_when_the_list_is_empty` | 无创建空本/LaunchedEffect 查询补种；空提示存在 | 无直接 UI 证据 | 无 | 缺口：删完→重进/重启后仍空；有 purge 数据测试也不等于页面不补种 |
| `test_sensenova_preset_is_not_forced_back_after_deletion` | Settings owners 无强制商汤；parseStored/preferences 接线 | ApiProfileTest.storedParsingDistinguishesNeverSavedFromDeliberatelyEmptied；deletingEveryStoredProfileStaysDeleted | 无 | 部分：存储解释函数已测；Settings 重建/启动不补种缺口 |
| `test_chat_picker_respects_an_empty_profile_list` | NotebookChatTab 使用 parseStored（只断言符号存在） | ApiProfileTest.storedParsingDistinguishesNeverSavedFromDeliberatelyEmptied；testResolveSelectionHandlesEmptyProfilesAndModels | P3：empty profiles 显示空态、管理不 dismiss | 部分：真实聊天页从已存 [] 到 picker 的链路未集成 |
| `test_empty_state_string_exists_in_the_catalog` | catalog 包含 notebooksEmptyHint | 无 | 无 | 结构护栏；不证明每语言非空或实际 notebooks 空态可见 |

### [test_performance_contract.py](<../../tools/tests/test_performance_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_settings_uses_no_per_card_live_blur` | Settings 禁live blur；轻材质分支无 haze/shadow | 无 | 无 | 结构护栏；没有帧耗/截图/渲染层行为测量 |
| `test_single_pager_animation_owner_and_layer_only_route_effect` | pager 动画唯一 owner；Settings layer alpha，无 AnimatedContent | 无 | 无 | 结构护栏；快速切换/动画竞争与重组成本仍缺 |
| `test_startup_does_not_block_main_thread_for_datastore` | MainActivity 无 runBlocking；IO读 prefs、snapshot、db ready gate | SettingsCacheSeedTest.seedCopiesEveryDisplayFieldFromStartupPrefs；seedIsIdempotentForTheSameStartupPrefs（只测缓存内容） | 无 | 部分：无主线程启动阻塞/首帧/database-ready 集成测试 |
| `test_expensive_api_derived_values_are_cached` | modelChoices 与 prettyBody 使用 remember | ApiProfileLogicTest.modelChoicesTrimsDeduplicatesAndKeepsFetchedBeforeSortedSaved（只测输出） | 无 | 部分：重组是否复用/解析调用次数缺口 |

### [test_foreground_glass_contract.py](<../../tools/tests/test_foreground_glass_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_cards_have_real_background_sampling_and_pure_white_bypass` | haze/backdrop/purewhite bypass/fallback 接线 | ForegroundMaterialTest.readingLayerIsDenserThanOrdinaryCardButNotOpaque（仅材质 spec 数值） | 无 | 部分：实际背景采样/纯白 bypass 像素未测 |
| `test_source_is_not_registered_on_foreground_pages` | AppShell 唯一 backdrop source，前景页无 source | 无 | 无 | 结构护栏；重复采样/真实层次无运行期证据 |
| `test_popups_let_material_own_position_and_size` | 无旧 glassPanel；popup containerColor/border 统一材质 | 无 | D 挂载两类真实 sheet，但不测材质 | 结构护栏；不可把挂载成功算材质/尺寸全部覆盖 |
| `test_tall_sheets_stay_within_the_available_height` | 高度limit；picker handle策略；notebook 滚动关菜单 | 无 | D：两 picker 长列表实滚动且保持打开 | 部分：屏幕高度/insets/字体缩放约束、Dialogue/菜单关闭缺口 |

### [test_androidify_reuse.py](<../../tools/tests/test_androidify_reuse.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_port_retains_attribution_and_real_interaction_algorithm` | 版权/NOTICE存在；node/collectLatest/cancel/draw scale 源码 | ScaleIndicationTest.equivalentFactoriesDoNotRestartTheIndication；factoriesCreateIndependentNodesAndReuseResetsScale | 无 | 部分：factory相等/节点隔离/reset已测；实际 press/release/cancel 动画缺；许可证属结构 |
| `test_port_is_used_and_old_parallel_press_scale_is_removed` | UiComponents/Glass 使用共用 indication；无旧动画label | 无 | 无 | 结构护栏；多组件真实按压/复用动画缺口 |

### [test_approved_ux_contract.py](<../../tools/tests/test_approved_ux_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_user_scroll_pauses_before_list_consumes_delta` | onPreScroll UserInput 先关闭 follow；nestedScroll 接线 | StreamFollowSchedulerTest.manualScrollWhileWaitingPreventsPendingFollow（手动改 allowed，不是真滑动） | 无 | 部分：调度防迟到 follow 已测；真实 pre-scroll 次序缺口 |
| `test_notebook_tab_container_is_centered_and_twenty_percent_narrower` | 容器居中宽80%、等权chat/source tab、玻璃 | NotebookDetailTabsTest.detailTabsKeepChatFirstSoSwipeOrderIsStable；detailTabIndexMatchesPagerPage（只测枚举/索引） | 无 | 部分：实际点击/布局宽度/居中/视觉缺口 |
| `test_return_to_bottom_is_borderless_circle_with_clear_arrow` | 44dp无框圆按钮、箭头/阴影；点击恢复follow并animate | 无直接点击证据 | 无 | 缺口：真实按钮点击→列表到底且后续恢复跟随；视觉仍仅结构 |
| `test_error_details_remain_below_summary` | API summary/details 先后、pretty body、copy raw接线 | 无直接 UI 证据 | 无 | 缺口：错误内容/折叠详情/复制原文 UI 行为与排列 |

### [test_legacy_symbols.py](<../../tools/tests/test_legacy_symbols.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_retired_symbols_never_come_back` | 遍历源码禁止 manifest 退役符号；清单非空 | 不适用 | 无 | 结构护栏；不是功能行为覆盖，保留 |
| `test_manifest_stays_sorted_and_unique` | 实际仅比较 sorted(set) 与 sorted：保证唯一，不保证原清单顺序 | 不适用 | 无 | 结构护栏；勿按函数名宣称排序已被约束 |

### [test_ui_layer_boundary.py](<../../tools/tests/test_ui_layer_boundary.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_ui_layer_holds_no_persistence_or_network_owners` | UI forbidden tokens offenders 等于 baseline | 不适用 | 无 | 结构护栏；词法扫描不是运行期职责隔离证明 |
| `test_baseline_only_shrinks` | baseline唯一且必须空；并未对历史版本作集合比较 | 不适用 | 无 | 结构护栏；保留零offender约束 |

### [test_unused_entity_columns.py](<../../tools/tests/test_unused_entity_columns.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_only_known_unused_columns_remain` | 实体字段在data外词法使用情况等于baseline | 不适用 | 无 | 结构护栏；词法出现不证明读写语义 |
| `test_baseline_is_sorted_and_unique` | 实际比较 sorted(set) 与 sorted：保证唯一，不保证输入顺序 | 不适用 | 无 | 结构护栏；保留已知列预算 |

### [test_signing_contract.py](<../../tools/tests/test_signing_contract.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_formal_workflow_requires_release_signing` | release workflow 强制签名；build workflow preview条件 | 不适用（workflow非Kotlin行为） | 无 | 结构护栏；同文件其他5个实际执行shell的用例不在本表范围 |

### [test_cloud_validation.py](<../../tools/tests/test_cloud_validation.py>)

| 当前纯源码契约函数 | 结构护栏（实际断言） | 已有真实 Kotlin 行为覆盖 | 本轮新增行为覆盖 | 仍缺行为覆盖 / 判定 |
|---|---|---|---|---|
| `test_both_sqlite_generations_run_the_complete_device_suite` | CI API33/35、完整connected suite、失败闭合、inventory与报告上传 | 不适用（CI策略） | 无 | 结构护栏；不代表两设备矩阵已运行通过 |
| `test_check_gate_requires_all_matrix_children_and_other_jobs` | check always+三类needs；所有结果success | 不适用 | 无 | 结构护栏；不是Kotlin行为 |
| `test_preview_wait_covers_device_matrix_and_fails_closed` | preview有超时/轮询/curl失败闭合；green check在下载前 | 不适用 | 无 | 结构护栏；真实远端发布闭合未执行 |
| `test_manual_v25_fixture_reaches_current_schema_through_v26` | 手工25fixture→26→35链顺序、保留行、旧索引/新schema字面断言 | 无同等旧全链证据 | M：fullChain25To35_preservesNotesAndChatAndValidatesCurrentRoomSchema | 行为已有真实SQLite/Room测试定义；设备执行结果本报告不背书；1200行限制仍纯结构 |

## 结论与优先缺口

1. **picker/history/settings：本轮有真实Compose行为定义，不应再笼统写“只有源码契约”**。P/A/H/S四类共19个当前UI方法（其中两项由旧用例加强）覆盖选择/草稿/确认/取消/空态/历史回调等；H3的初始定位只证明历史sheet，不证明profile picker首帧。D另增10个共享抽屉交互定义；均不能从存在测试推断本轮设备通过。
2. **IME需区分已补与仍缺**：C已真实测编辑焦点保持/继续编辑、anchor toggle及preset选择；W已测实际Dialog flag生命周期和Activity不受污染。厂商IME可见性、Dialogue已显/未显分支与Back优先级、NotebookChatTab外点透传/不重开仍缺。S3原本在编辑后按系统返回，因该编辑型弹窗保留键盘、真实输入法先消费返回键而在API33/35超时；现改为与本批共享抽屉测试相同的遮罩取消路径并断言遮罩可触，草稿不保存与重开还原原值断言不变。该用例因此不覆盖Back优先级，缺口如实保留。
3. **reorder已补生产组件真实交互，仍不夸大为全链路**：R的7例覆盖真实API长按、边缘实滚动、保存禁用到completion成功/失败、无变更/取消/失活恢复，以及生产Lazy edge-effect结束/取消后停止。消费量几何仍由纯状态单测补足；真实DAO落盘、整个SettingsScreen/Notebooks页面父接线及旧token竞争仍部分/缺口。不可把D的drawer拖拽当reorder覆盖。
4. **selection需分开**：picker/history selected语义已有；F新增selectedCard双向/禁动画像素断言与Theme行/radio单回调/locked行为。没有真实Repository写入次数证据；聊天文本选择已有矩阵两行所列4例真实长按/句柄出现、Key.Copy系统剪贴板与按钮副作用隔离定义；句柄拖动跨段、系统toolbar点击及操作后选区保持仍缺，不用selectedCard动画替代文本选择测试。
5. 颜色/材质/布局/唯一owner/许可证/退役符号/分层/CI配置本来就适合保留结构护栏。行为补测与护栏互补，不以行数或“有同名Kotlin类”替代证明。

## 其他 Kotlin 引用定位

- [ModelPickerPositionTest.kt](<../../app/src/test/java/com/lucent/app/ui/ModelPickerPositionTest.kt>)
- [ModelPickerDrawerTest.kt](<../../app/src/test/java/com/lucent/app/ui/ModelPickerDrawerTest.kt>)
- [ReorderAutoScrollTest.kt](<../../app/src/test/java/com/lucent/app/ui/ReorderAutoScrollTest.kt>)
- [ReorderSlotsTest.kt](<../../app/src/test/java/com/lucent/app/ui/ReorderSlotsTest.kt>)
- [EdgeAutoScrollTest.kt](<../../app/src/test/java/com/lucent/app/ui/EdgeAutoScrollTest.kt>)
- [StepperSliderGestureTest.kt](<../../app/src/test/java/com/lucent/app/ui/StepperSliderGestureTest.kt>)
- [ScaleIndicationTest.kt](<../../app/src/test/java/com/lucent/app/ui/ScaleIndicationTest.kt>)
- [ForegroundMaterialTest.kt](<../../app/src/test/java/com/lucent/app/ui/ForegroundMaterialTest.kt>)
- [HomeNavigationTest.kt](<../../app/src/test/java/com/lucent/app/ui/HomeNavigationTest.kt>)
- [NotebookDetailTabsTest.kt](<../../app/src/test/java/com/lucent/app/ui/NotebookDetailTabsTest.kt>)
- [StreamFollowSchedulerTest.kt](<../../app/src/test/java/com/lucent/app/ui/StreamFollowSchedulerTest.kt>)
- [ApiProfileLogicTest.kt](<../../app/src/test/java/com/lucent/app/ui/settings/ApiProfileLogicTest.kt>)
- [ApiProfileTest.kt](<../../app/src/test/java/com/lucent/app/data/ApiProfileTest.kt>)
- [SettingsCacheSeedTest.kt](<../../app/src/test/java/com/lucent/app/data/SettingsCacheSeedTest.kt>)

## 总数与非等价证据复核

- 当前矩阵仍为 **58条纯源码契约**，本次只补行为证据，不增删护栏。分文件数量：picker 9、history 5、IME 5、count/slider/reorder 5、chat selection 2、selection feedback 3、deletable 4、performance 4、foreground 4、androidify 2、approved UX 4、legacy 2、UI boundary 2、unused columns 2、signing 1、cloud 4；合计58。
- X/XP补充历史摘要、字符派生与重组复用证据，当前58条里没有对应专门派生契约，故不把它们填入API expensive-derived缓存行（API modelChoices/prettyBody重组复用仍缺）。G补stop/final-save生命周期，也不能代替regeneration key预检/统计复位。
- 新增计数按实际读取：C5、W2、T3、F5、R7、X2、XP3、G3；方法与helper均已读，不按委派中的预期数量登记8个R。
- 主代理最终集成验证：全部androidTest Kotlin编译通过；Debug/Release JVM各455/74实际通过。设备源码共102例/20类，57个UI用例定义；CI将要求真实执行下限102/20。新设备用例尚未本地运行，保留上述运行期/真机边界。
