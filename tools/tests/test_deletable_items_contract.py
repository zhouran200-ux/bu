from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/lucent/app"


class DeletableItemsContractTest(unittest.TestCase):
    def test_notebooks_are_not_recreated_when_the_list_is_empty(self):
        text = (MAIN / "ui/NotebooksScreen.kt").read_text(encoding="utf-8")
        self.assertNotIn('title = "空白笔记本"', text)
        self.assertNotIn("LaunchedEffect(notebooks)", text)
        self.assertNotIn("notebookDao().getAllOnce()", text)
        self.assertIn("S.notebooksEmptyHint", text)

    def test_sensenova_preset_is_not_forced_back_after_deletion(self):
        settings_files = [
            MAIN / "ui/SettingsScreen.kt",
            MAIN / "ui/settings/ApiSettingsOwner.kt",
            MAIN / "ui/settings/ApiSettingsState.kt",
        ]
        combined = "".join(path.read_text(encoding="utf-8") for path in settings_files)
        self.assertNotIn("sensenova", combined)
        self.assertNotIn('name == "商汤"', combined)
        owner = (MAIN / "ui/settings/ApiSettingsOwner.kt").read_text(encoding="utf-8")
        self.assertIn("ApiProfiles.parseStored", owner)
        self.assertIn("apiProfilePreferences", owner)

    def test_chat_picker_respects_an_empty_profile_list(self):
        text = (MAIN / "ui/NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertIn("ApiProfiles.parseStored", text)

    def test_empty_state_string_exists_in_the_catalog(self):
        catalog = (ROOT / "tools/i18n/catalog.py").read_text(encoding="utf-8")
        self.assertIn("notebooksEmptyHint", catalog)


if __name__ == "__main__":
    unittest.main()
