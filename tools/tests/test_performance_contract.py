from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"

class PerformanceContractTest(unittest.TestCase):
    def test_settings_uses_no_per_card_live_blur(self):
        settings = (UI / "SettingsScreen.kt").read_text(encoding="utf-8")
        self.assertIn("LocalLiveGlassEnabled provides false", settings)
        material = (UI / "ForegroundGlass.kt").read_text(encoding="utf-8")
        light = material.split("if (!liveGlass)", 1)[1].split("val source", 1)[0]
        self.assertNotIn("hazeEffect", light)
        self.assertNotIn("shadow(", light)

    def test_single_pager_animation_owner_and_layer_only_route_effect(self):
        main = (ROOT / "app/src/main/java/com/lucent/app/AppShell.kt").read_text(encoding="utf-8")
        home = (ROOT / "app/src/main/java/com/lucent/app/HomeTabs.kt").read_text(encoding="utf-8")
        self.assertNotIn("pagerState.animateScrollToPage", main)
        self.assertEqual(1, home.count("pagerState.animateScrollToPage"))
        settings = (UI / "SettingsScreen.kt").read_text(encoding="utf-8")
        self.assertIn(".graphicsLayer { alpha = 0.85f + enterProgress.value * 0.15f }", settings)
        self.assertNotIn("AnimatedContent", settings)

    def test_startup_does_not_block_main_thread_for_datastore(self):
        activity = (ROOT / "app/src/main/java/com/lucent/app/MainActivity.kt").read_text(encoding="utf-8")
        self.assertNotIn("runBlocking", activity)
        self.assertIn("settingsRepo.startupPrefsOnce()", activity)
        self.assertIn("Dispatchers.IO", activity)
        self.assertNotIn("settingsReady", activity)
        self.assertIn("SettingsCache.snapshot()", activity)
        self.assertIn("if (databaseReady) {", activity)

    def test_expensive_api_derived_values_are_cached(self):
        api = (UI / "settings/ApiSettingsPage.kt").read_text(encoding="utf-8")
        self.assertIn("remember(fetchedModels, models) { modelChoices", api)
        self.assertIn("val prettyBody = remember(res)", api)

if __name__ == "__main__":
    unittest.main()
