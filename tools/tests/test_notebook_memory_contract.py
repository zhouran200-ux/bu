from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class NotebookMemoryContractTest(unittest.TestCase):
    def test_model_memory_waits_for_real_profiles_before_persisting_fallback(self):
        source = (ROOT / "app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertIn("produceState<String?>(initialValue = null", source)
        effect = source.split("LaunchedEffect(notebookId, loadedProfilesJson, resolvedSelection)", 1)[1].split("var showDialogueSettingsDialog", 1)[0]
        self.assertLess(effect.index("if (loadedProfilesJson == null) return@LaunchedEffect"), effect.index("prefs.edit()"))


if __name__ == "__main__":
    unittest.main()
