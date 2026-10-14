from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class PickerContractTest(unittest.TestCase):
    def test_model_selection_does_not_close_parent(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        callback = text.split("onSelect = { profile, modelName ->", 1)[1].split("onDismiss =", 1)[0]
        self.assertNotIn("showProfileModelPickerSheet = false", callback)

    def test_picker_has_no_automatic_hide_path(self):
        text = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertNotIn("sheetState.hide", text)
        self.assertNotIn("closeSheet", text)
        self.assertIn("onSelect(profile, model)", text)

    def test_initial_position_is_constructed_without_scroll_animation(self):
        text = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertIn("LazyListState(initialPosition.firstVisibleItemIndex, 0)", text)
        self.assertNotIn("scrollToItem", text)
        self.assertNotIn("animateScrollToItem", text)
        self.assertNotIn("remember(position)", text)

    def test_model_sheets_restrict_gestures_to_handle(self):
        sheets = []
        for path in UI.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            count = text.count("    ModalBottomSheet(")
            if count:
                sheets.append(path.name)
                if path.name in ("SettingsComponents.kt", "ProfileModelPickerSheet.kt"):
                    self.assertEqual(count, text.count("sheetGesturesEnabled = false"), path.name)
                    self.assertIn("dragHandle = drawer.handle", text)
                else:
                    self.assertEqual(count, text.count("sheetGesturesEnabled = true"), path.name)
                    self.assertEqual(count, text.count("dragHandle = { BottomSheetDefaults.DragHandle() }"), path.name)
            self.assertNotIn("LucentDragHandle", text, path.name)
        self.assertEqual(4, len(sheets))

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
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8").split("private fun DialogueSettingsSheet(", 1)[1]
        self.assertNotIn("onClick = onDismiss", text)

    def test_manage_api_uses_existing_settings_without_overlay_or_dismiss(self):
        picker = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertIn('Text("管理API配置"', picker)
        self.assertIn("onManageApi()", picker)
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
        self.assertIn("dragHandle = drawer.handle", text)
        drawer = (UI / "ModelPickerDrawer.kt").read_text(encoding="utf-8")
        self.assertIn("sheetState.hide()", drawer)
        self.assertIn("translationY = travel", drawer)
        self.assertIn("Modifier.width(72.dp).height(40.dp).draggable", drawer)
        api = (UI / "SettingsComponents.kt").read_text(encoding="utf-8")
        self.assertIn("drawer.close { onDone(draft) }", api)
        self.assertNotIn("Icons.Default.Check", text)
        self.assertIn("HorizontalDivider", text)
        self.assertNotIn("LucentDragHandle", text)
        self.assertNotIn("translationY", text)


if __name__ == "__main__":
    unittest.main()
