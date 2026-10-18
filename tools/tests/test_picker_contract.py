from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class PickerContractTest(unittest.TestCase):
    def test_parent_saves_selection_without_interrupting_close_animation(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        callback = text.split("onSelect = { profile, modelName ->", 1)[1].split("onDismiss =", 1)[0]
        self.assertNotIn("showProfileModelPickerSheet = false", callback)

    # Selection, disclosure, current-model clicks and management callbacks are exercised
    # by ProfileModelPickerSheetTest; retain visual and fixed-header source guards here.
    def test_picker_keeps_selection_feedback_and_fixed_header_structure(self):
        text = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertIn("if (modelSelected) LucentStatusColors.Selected", text)
        self.assertIn("if (modelSelected) FontWeight.ExtraBold", text)
        self.assertNotIn('item(key = "title")', text)
        self.assertNotIn('item(key = "manage")', text)

    def test_initial_position_is_constructed_without_scroll_animation(self):
        text = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertIn("LazyListState(initialPosition.firstVisibleItemIndex, 0)", text)
        self.assertNotIn("scrollToItem", text)
        self.assertNotIn("animateScrollToItem", text)
        self.assertNotIn("remember(position)", text)

    def test_all_sheets_share_bright_handle_and_fixed_header(self):
        sheets = []
        for path in UI.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            count = text.count("    ModalBottomSheet(")
            if count:
                sheets.append(path.name)
                self.assertEqual(count, text.count("sheetGesturesEnabled = false"), path.name)
                self.assertEqual(count, text.count("properties = DrawerSheetProperties"), path.name)
                self.assertEqual(count, text.count("dragHandle = null"), path.name)
                self.assertEqual(count, text.count("drawer.handle()"), path.name)
            self.assertNotIn("LucentDragHandle", text, path.name)
        self.assertEqual(4, len(sheets))

    def test_drawers_dismiss_from_backdrop_and_handle(self):
        drawer = (UI / "ModelPickerDrawer.kt").read_text(encoding="utf-8")
        self.assertIn("internal val DrawerSheetProperties", drawer)
        self.assertIn("shouldDismissOnBackPress = true", drawer)
        self.assertIn("shouldDismissOnClickOutside = true", drawer)
        self.assertIn("Modifier.fillMaxWidth(0.4f).height(5.dp)", drawer)
        self.assertIn(".background(Color.White, CircleShape)", drawer)
        self.assertNotIn("HandleOnlyDrawerProperties", drawer)
        self.assertNotIn("BottomSheetDefaults", drawer)
        for name in ("ChatHistorySheet.kt", "ProfileModelPickerSheet.kt", "SettingsComponents.kt", "ChatDialogueSettings.kt"):
            text = (UI / name).read_text(encoding="utf-8")
            self.assertIn("rememberModelPickerDrawer(", text, name)
            self.assertIn(".then(drawer.dragModifier)", text, name)
            self.assertNotIn("HandleOnlyDrawerProperties", text, name)
        chat = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertNotIn("handleWidth", chat)
        self.assertNotIn("handleWidth", (UI / "ChatOverlayComponents.kt").read_text(encoding="utf-8"))

    def test_history_and_settings_callbacks_do_not_dismiss(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        history = text.split("        if (showHistorySheet) {", 1)[1].split("onDismiss =", 1)[0]
        settings = text.split("        if (showDialogueSettingsDialog) {", 1)[1].split("onDismiss =", 1)[0]
        self.assertNotIn("showHistorySheet = false", history)
        self.assertNotIn("showDialogueSettingsDialog = false", settings)
        api = (UI / "settings/ApiSettingsPage.kt").read_text(encoding="utf-8")
        callback = api.split("        ApiModelPickerDialog(", 1)[1].split("onDismiss =", 1)[0]
        self.assertNotIn("pickerOpen = false", callback)

    def test_extra_sheet_close_buttons_are_removed(self):
        for name in ("ChatHistorySheet.kt", "SettingsComponents.kt"):
            text = (UI / name).read_text(encoding="utf-8")
            self.assertNotIn("onClick = onDismiss", text)
        text = (UI / "ChatDialogueSettings.kt").read_text(encoding="utf-8").split("internal fun DialogueSettingsSheet(", 1)[1]
        self.assertNotIn("onClick = onDismiss", text)

    def test_manage_api_uses_existing_settings_without_overlay_or_dismiss(self):
        picker = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        # ProfileModelPickerSheetTest covers the management click and no-dismiss behavior.
        self.assertIn("if (!visible) return", picker)
        self.assertNotIn("openManagementAfterDismiss", picker)
        self.assertNotIn("ApiConfigurationPage", picker)
        self.assertNotRegex(picker, r"\bDialog\s*\(")
        self.assertLess(picker.index("val listState = remember"), picker.index("if (!visible) return"))
        chat = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertIn("AppNavigation.openApiFromModelPicker(notebookId)", chat)
        settings = (UI / "SettingsScreen.kt").read_text(encoding="utf-8")
        self.assertNotIn("apiOnly", settings)

    def test_handle_moves_drawer_and_confirmation_hides_before_removal(self):
        text = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertIn("sheetGesturesEnabled = false", text)
        self.assertIn("dragHandle = null", text)
        self.assertIn("drawer.handle()", text)
        drawer = (UI / "ModelPickerDrawer.kt").read_text(encoding="utf-8")
        self.assertIn("sheetState.hide()", drawer)
        self.assertIn("translationY = travel", drawer)
        self.assertIn("val dragModifier = Modifier.draggable(", drawer)
        self.assertIn(".then(dragModifier)", drawer)
        self.assertIn(".background(Color.White, CircleShape)", drawer)
        self.assertIn("handlePressed || dragged", drawer)
        self.assertNotIn("DragHandle(height =", drawer)
        # ApiModelPickerDialogTest checks confirmation callback order, draft contents,
        # clear-all and cancellation; shared drawer animation remains guarded above.
        self.assertNotIn("Icons.Default.Check", text)
        self.assertIn("HorizontalDivider", text)
        self.assertNotIn("LucentDragHandle", text)
        self.assertNotIn("translationY", text)


if __name__ == "__main__":
    unittest.main()
