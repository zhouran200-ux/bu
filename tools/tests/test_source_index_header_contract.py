from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class SourceIndexHeaderContractTest(unittest.TestCase):
    def test_metadata_is_bottom_left_and_has_no_action_callbacks(self):
        page = (UI / "SourceIndexPreview.kt").read_text(encoding="utf-8")
        dock = (UI / "SourcePreviewInfoDock.kt").read_text(encoding="utf-8")
        self.assertIn("Alignment.BottomStart", page)
        self.assertIn("navigationBarsPadding()", page)
        self.assertIn("bottom = dockHeight + 28.dp + bottomInset", page)
        self.assertIn("onSizeChanged", page)
        self.assertIn("val sourceLine = listOf(meta, source.title.trim())", page)
        for removed in ("IconButton", "onBack", "onCopy", "copyPlainTextWithToast"):
            self.assertNotIn(removed, page + dock)
        self.assertIn("foregroundGlass(12.dp, reading = true)", dock)

    def test_both_pages_share_chapter_badge_and_system_back_survives(self):
        reader = (UI / "SourceReaderDocument.kt").read_text(encoding="utf-8")
        preview = (UI / "SourceIndexPreview.kt").read_text(encoding="utf-8")
        for page in (reader, preview):
            self.assertIn("SourceChapterBadge(", page)
            self.assertIn("Alignment.TopStart", page)
            self.assertIn(".padding(12.dp)", page)
            self.assertIn(".widthIn(max = titleMaxWidth)", page)
        self.assertIn("sourcePreviewSectionTitles(rows, source.title)", preview)
        self.assertIn("BackHandler(enabled = active && showIndexPreview, onBack = returnToDocument)", reader)


if __name__ == "__main__":
    unittest.main()
