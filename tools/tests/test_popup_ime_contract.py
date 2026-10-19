from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class PopupImeContractTest(unittest.TestCase):
    def test_reasoning_menu_preserves_editor_focus_and_owner_handles_back(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        menu = (UI / "ChatOverlayComponents.kt").read_text(encoding="utf-8").split("internal fun BottomGlassComposer(", 1)[1]
        self.assertIn("properties = ReasoningPopupProperties", menu)
        self.assertIn("BackHandler(enabled = showReasoningMenu)", menu)
        self.assertNotIn("clearFocus()", menu)
        self.assertNotIn("keyboardController", menu)
        helper = (UI / "PopupIme.kt").read_text(encoding="utf-8")
        self.assertIn("PopupProperties(focusable = false, dismissOnClickOutside = false)", helper)

    def test_reasoning_anchor_toggles_and_outside_press_does_not_reopen(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        menu = (UI / "ChatOverlayComponents.kt").read_text(encoding="utf-8").split("internal fun BottomGlassComposer(", 1)[1]
        self.assertIn("onReasoningMenuChange(!showReasoningMenu)", menu)
        self.assertIn("onReasoningButtonBounds(it.boundsInWindow())", menu)
        self.assertIn("onDismissRequest = { onReasoningMenuChange(false) }", menu)
        self.assertIn("onSelectReasoning(preset)", menu)
        self.assertIn("onReasoningMenuChange(false)", menu)
        self.assertNotIn("showReasoningMenu = true", menu)
        observer = text.split(".pointerInput(showReasoningMenu)", 1)[1].split("FloatingChatLayout(", 1)[0]
        self.assertIn("PointerEventPass.Initial", observer)
        self.assertIn("it.pressed && !it.previousPressed", observer)
        self.assertIn("localToWindow(down.position)", observer)
        self.assertIn("if (!reasoningButtonBounds.contains(position)) showReasoningMenu = false", observer)
        self.assertNotIn("consume()", observer)

    def test_non_editing_sheets_use_only_their_dialog_window(self):
        for name in ("ChatHistorySheet.kt", "ProfileModelPickerSheet.kt"):
            text = (UI / name).read_text(encoding="utf-8")
            self.assertIn("KeepKeyboardForNonEditingDialog()", text)
        helper = (UI / "PopupIme.kt").read_text(encoding="utf-8")
        self.assertIn("DialogWindowProvider", helper)
        self.assertIn("FLAG_ALT_FOCUSABLE_IM", helper)
        self.assertNotIn("FLAG_NOT_FOCUSABLE", helper)
        self.assertNotIn("Activity", helper)
        self.assertIn("window?.clearFlags(flag)", helper)

    def test_editors_remain_ime_eligible_and_do_not_force_show_keyboard(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        sheet = (UI / "ChatDialogueSettings.kt").read_text(encoding="utf-8").split("internal fun DialogueSettingsSheet(", 1)[1]
        self.assertIn("PreserveEditingKeyboardOnOpen(keepEditing, inputFocus)", sheet)
        self.assertIn("focusRequester(inputFocus)", sheet)
        self.assertNotIn("KeepKeyboardForNonEditingDialog", sheet)
        helper = (UI / "PopupIme.kt").read_text(encoding="utf-8")
        self.assertIn("if (wasVisible) requester.requestFocus()", helper)
        self.assertNotIn("keyboardController", helper)

    def test_api_available_models_dialog_is_excluded(self):
        text = (UI / "SettingsComponents.kt").read_text(encoding="utf-8").split("internal fun ApiModelPickerDialog(", 1)[1]
        self.assertNotIn("KeepKeyboardForNonEditingDialog", text)
        self.assertNotIn("PreserveEditingKeyboardOnOpen", text)
        self.assertNotIn("NonEditingPopupProperties", text)


if __name__ == "__main__":
    unittest.main()
