from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class SourceIndexHeaderContractTest(unittest.TestCase):
    def test_header_keeps_one_compact_navigation_row(self):
        text = (UI / "SourceIndexPreview.kt").read_text(encoding="utf-8")
        self.assertNotIn("S.sourceIndexPreviewSubtitle", text)
        self.assertIn("val sourceLine = listOf(source.title.trim(), meta)", text)
        self.assertIn('joinToString(" · ")', text)
        self.assertEqual(2, text.count("modifier = Modifier.size(44.dp)"))
        self.assertIn("LocalMinimumInteractiveComponentSize provides 0.dp", text)
        self.assertIn("PaddingValues(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 24.dp)", text)


if __name__ == "__main__":
    unittest.main()
