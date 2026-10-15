from pathlib import Path
import unittest
UI = Path(__file__).resolve().parents[2] / "app/src/main/java/com/lucent/app/ui"

class SelectionFeedbackTest(unittest.TestCase):
    def test_selection_fades_both_directions_in_draw_phase(self):
        text = (UI / "Glass.kt").read_text(encoding="utf-8").split("fun Modifier.selectedCard", 1)[1].split("enum class PaletteGroup", 1)[0]
        self.assertNotIn("if (!selected) return", text)
        self.assertIn("else Color.Transparent", text)
        self.assertIn("tint.value", text)
        self.assertIn("drawBehind", text)
        self.assertIn("motionEnabled", text)

    def test_theme_row_owns_selection_and_radio_is_display_only(self):
        text = (UI / "settings/ThemeSettingsPage.kt").read_text(encoding="utf-8")
        self.assertIn(".selectedCard(savedTheme == mode.key", text)
        self.assertIn("role = Role.RadioButton", text)
        self.assertIn("onClick = null", text)

    def test_disclosure_keeps_single_icon_and_rotation_in_layer(self):
        text = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertIn("rotationZ = arrowRotation.value", text)
        self.assertNotIn("KeyboardArrowDown", text)
        self.assertIn(".selectedCard(modelSelected", text)
