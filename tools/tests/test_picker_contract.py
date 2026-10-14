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

    def test_handle_delegates_dragging_to_entire_native_sheet(self):
        text = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertIn("sheetGesturesEnabled = true", text)
        self.assertIn("BottomSheetDefaults.DragHandle()", text)
        self.assertNotIn("LucentDragHandle", text)
        self.assertNotIn("translationY", text)


if __name__ == "__main__":
    unittest.main()
