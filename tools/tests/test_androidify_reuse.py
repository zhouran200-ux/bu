from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"

class AndroidifyReuseTest(unittest.TestCase):
    def test_port_retains_attribution_and_real_interaction_algorithm(self):
        source = (UI / "ScaleIndication.kt").read_text(encoding="utf-8")
        for text in ("Copyright 2025 The Android Open Source Project", "Modified for Lucent", "IndicationNodeFactory", "DrawModifierNode", "collectLatest", "PressInteraction.Cancel", "scale(animatedScalePercent.value)"):
            self.assertIn(text, source)
        assets = ROOT / "app/src/main/assets/licenses"
        self.assertTrue((assets / "androidify-APACHE-2.0.txt").exists())
        self.assertTrue((assets / "androidify-NOTICE.txt").exists())

    def test_port_is_used_and_old_parallel_press_scale_is_removed(self):
        for name in ("UiComponents.kt", "Glass.kt"):
            text = (UI / name).read_text(encoding="utf-8")
            self.assertIn("rememberScaleIndication()", text)
            self.assertIn("indication = pressIndication", text)
            self.assertNotIn('label = "glassButtonScale"', text)
            self.assertNotIn('label = "glassPressScale"', text)

if __name__ == "__main__":
    unittest.main()
