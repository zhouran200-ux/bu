from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class ForegroundGlassContractTest(unittest.TestCase):
    def test_cards_have_real_background_sampling_and_pure_white_bypass(self):
        text = (UI / "ForegroundGlass.kt").read_text(encoding="utf-8")
        self.assertIn("LocalGlassBackdrop", text)
        self.assertIn("Modifier.hazeEffect(source", text)
        self.assertIn("if (LocalPureWhite.current)", text)
        self.assertIn("fallbackTint", text)
        self.assertIn("foregroundGlass(cornerRadius)", (UI / "Glass.kt").read_text(encoding="utf-8"))

    def test_source_is_not_registered_on_foreground_pages(self):
        for name in ("NotebooksScreen.kt", "SettingsScreen.kt"):
            self.assertNotIn("hazeSource", (UI / name).read_text(encoding="utf-8"))
        main = (ROOT / "app/src/main/java/com/lucent/app/MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn(".hazeSource(state = glassBackdrop)", main)
        self.assertNotIn("HazeMaterials.ultraThin", main)

    def test_popups_use_stable_panel_and_keep_drawer_gestures(self):
        for name in ("ChatHistorySheet.kt", "ProfileModelPickerSheet.kt", "SettingsComponents.kt"):
            text = (UI / name).read_text(encoding="utf-8")
            self.assertIn(".glassPanel(", text)
            self.assertIn("containerColor = Color.Transparent", text)
        picker = (UI / "ProfileModelPickerSheet.kt").read_text(encoding="utf-8")
        self.assertIn("sheetGesturesEnabled = false", picker)
        self.assertIn("dragHandle = drawer.handle", picker)


if __name__ == "__main__":
    unittest.main()
