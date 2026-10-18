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

    def test_typography_card_compacts_spacing_without_shrinking_hit_targets(self):
        text = (UI / "settings/FontTypographySection.kt").read_text(encoding="utf-8")
        card = text.split("compact = true,", 1)[0].rsplit("Column(", 1)[1]
        self.assertIn(".padding(horizontal = 16.dp, vertical = 12.dp)", card)
        self.assertEqual(3, text.count("compact = true,"))
        self.assertEqual(2, text.count("Modifier.padding(vertical = 4.dp)"))
        slider = (UI / "StepperSlider.kt").read_text(encoding="utf-8")
        self.assertIn("compact: Boolean = false", slider)
        self.assertIn("if (compact) 0.dp else 2.dp", slider)
        self.assertIn("if (compact) 2.dp else 4.dp", slider)
        self.assertEqual(2, slider.count("if (compact) 4.dp else 6.dp"))
        self.assertIn("SliderHitHeight = 36.dp", slider)
        self.assertIn(".size(width = 30.dp, height = 36.dp)", slider)

    def test_profile_save_and_list_share_the_reorder_guard(self):
        page = (UI / "settings/ApiSettingsPage.kt").read_text(encoding="utf-8")
        section = (UI / "settings/ApiProfileListSection.kt").read_text(encoding="utf-8")
        self.assertIn("val dragState = remember { mutableStateOf<ProfileDrag?>(null) }", page)
        save = (UI / "settings/ApiProfileSaveButton.kt").read_text(encoding="utf-8")
        self.assertIn("ApiProfileSaveButton(", page)
        self.assertIn("dragState = dragState", page)
        self.assertIn("var drag by dragState", section)
        self.assertIn("enabled = dragState.value == null", save)

    def test_edge_loops_use_consumed_scroll_and_stop_with_drag(self):
        text = (UI / "ReorderDrag.kt").read_text(encoding="utf-8")
        self.assertIn("state.compensateScroll(consumed)", text)
        self.assertIn("state.dragToken == token", text)
        api = (UI / "settings/ApiProfileListSection.kt").read_text(encoding="utf-8")
        self.assertIn("moveDrag(consumed)", api)
        self.assertIn("onDragCancel = { cancelDrag() }", api)
        self.assertIn("edgeScrollVelocity", api)
        host = (UI / "SettingsScreen.kt").read_text(encoding="utf-8")
        self.assertLess(host.index(".onGloballyPositioned { viewportBounds"), host.index(".verticalScroll(rootScroll)"))
