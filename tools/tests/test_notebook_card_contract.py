from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/lucent/app"
UI = MAIN / "ui"
CARD = UI / "NotebookShelfItem.kt"


class NotebookCardContractTest(unittest.TestCase):
    def test_card_shows_a_green_dot_only_when_a_source_exists(self):
        card = CARD.read_text(encoding="utf-8")
        self.assertNotIn("notebookSourcesSummary", card)
        self.assertNotIn("个来源", card)
        self.assertIn("if (sourceCount > 0)", card)
        self.assertIn("LucentStatusColors.SourcePresent", card)
        self.assertIn("CircleShape", card)
        self.assertIn("S.notebookHasSource", card)
        self.assertIn("NotebookShelfItem(", (UI / "NotebooksScreen.kt").read_text(encoding="utf-8"))

    def test_card_keeps_the_modified_clock_before_the_date_on_the_title_row(self):
        card = CARD.read_text(encoding="utf-8")
        self.assertIn("FlowRow(", card)
        self.assertIn("notebookModifiedAtLabel(notebook.updatedAt)", card)
        catalog = (ROOT / "tools/i18n/catalog.py").read_text(encoding="utf-8")
        self.assertIn('("notebookModifiedAtPattern", "HH:mm · M月d日")', catalog)

    def test_edits_write_the_modified_time_while_browsing_does_not(self):
        chat = (MAIN / "chat/ChatSessions.kt").read_text(encoding="utf-8")
        self.assertEqual(4, chat.count("notebookDao.touch(notebookId, System.currentTimeMillis())"))
        purge = (MAIN / "data/NotebookPurge.kt").read_text(encoding="utf-8")
        self.assertEqual(3, purge.count("db.notebookDao().touch(notebookId, System.currentTimeMillis())"))
        screen = (UI / "NotebooksScreen.kt").read_text(encoding="utf-8")
        detail = (UI / "NotebookDetailScreen.kt").read_text(encoding="utf-8")
        self.assertNotIn("repository.touch", screen)
        self.assertIn("onDialogueSettingsSaved = {", detail)
        self.assertIn("repository.touch(notebookId)", detail)


if __name__ == "__main__":
    unittest.main()
