from pathlib import Path
import unittest

UI = Path(__file__).resolve().parents[2] / "app/src/main/java/com/lucent/app/ui"

class CountSliderReorderContractTest(unittest.TestCase):
    def test_zero_source_count_and_stable_reorder_owner(self):
        text = (UI / "NotebooksScreen.kt").read_text(encoding="utf-8")
        self.assertNotIn("coerceAtLeast(1)", text)
        self.assertIn("notebookSourceCount", text)
        self.assertIn(".reorderableList(", text)
        self.assertNotIn(".reorderableItem(", text)
        self.assertIn("ReorderAutoScrollEffect", text)
        self.assertIn("reorderState.overlayTop", text)

    def test_slider_defers_value_change_until_direction_or_tap(self):
        text = (UI / "StepperSlider.kt").read_text(encoding="utf-8")
        gesture = text.split("awaitEachGesture {", 1)[1]
        self.assertLess(gesture.index("stepperGestureDirection("), gesture.index("commit.value("))
        self.assertIn("StepperGestureDirection.Vertical) break", gesture)
        self.assertIn("restoreValue.value(initialValue)", gesture)
        self.assertNotIn("PointerEventPass.Initial", gesture)

    def test_edge_loops_use_consumed_scroll_and_stop_with_drag(self):
        text = (UI / "ReorderDrag.kt").read_text(encoding="utf-8")
        self.assertIn("state.compensateScroll(consumed)", text)
        self.assertIn("state.dragToken == token", text)
        api = (UI / "settings/ApiSettingsPage.kt").read_text(encoding="utf-8")
        self.assertIn("moveDrag(consumed)", api)
        self.assertIn("onDragCancel = { cancelDrag() }", api)
        self.assertIn("edgeScrollVelocity", api)
        host = (UI / "SettingsScreen.kt").read_text(encoding="utf-8")
        self.assertLess(host.index(".onGloballyPositioned { viewportBounds"), host.index(".verticalScroll(rootScroll)"))
