from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class SourceReaderActionsContractTest(unittest.TestCase):
    def test_actions_are_bottom_anchored_and_content_clears_navigation(self):
        reader = (UI / "SourceReaderDocument.kt").read_text(encoding="utf-8")
        actions = reader[reader.index("SourceReaderActions("):]
        self.assertIn(".align(Alignment.BottomEnd)", actions)
        self.assertIn(".navigationBarsPadding()", actions)
        self.assertNotIn("Alignment.TopEnd", reader)
        self.assertIn("bottom = 88.dp + bottomInset", reader)
        self.assertIn("val titleMaxWidth = (maxWidth - 24.dp)", reader)
        self.assertIn("previewListState = LazyListState(position.index, position.offset)", actions)

    def test_old_entries_are_removed_and_callbacks_remain_separate(self):
        sources = "\n".join(path.read_text(encoding="utf-8") for path in UI.glob("*.kt"))
        self.assertNotIn("SourceManageEntry", sources)
        self.assertNotIn("SourceIndexPreviewEntry", sources)
        actions = (UI / "SourceReaderActions.kt").read_text(encoding="utf-8")
        self.assertIn("contentDescription = S.sourcesManage", actions)
        self.assertIn("contentDescription = S.sourceIndexPreviewOpen", actions)
        self.assertEqual(2, actions.count("Modifier.size(48.dp)"))
        self.assertIn("remember(enabled, hasManageActions)", actions)
        self.assertIn("onReplace()", actions)
        self.assertIn("onRemove()", actions)
        # The UI only delegates removal; the existing confirmation remains the authority.
        tab = (UI / "NotebookSourcesTab.kt").read_text(encoding="utf-8")
        self.assertIn("if (confirmingRemove && current != null)", tab)
        self.assertIn("onRemoveDocument = { if (!importing) confirmingRemove = true }", tab)


if __name__ == "__main__":
    unittest.main()
