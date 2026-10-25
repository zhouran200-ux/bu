from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/lucent/app"
UI = MAIN / "ui"
SETTINGS = UI / "settings"


class TypographyScopeContractTest(unittest.TestCase):
    def test_paragraph_spacing_and_scope_toggle_live_in_the_typography_card(self):
        text = (SETTINGS / "FontTypographySection.kt").read_text(encoding="utf-8")
        self.assertIn("paragraphSpacing: Float", text)
        self.assertIn("typographyScope: String", text)
        self.assertIn("S.paragraphSpacingTitle", text)
        self.assertIn("S.typographyScopeGlobal", text)
        self.assertIn("S.typographyScopeText", text)
        self.assertIn("TypographyScopeToggle(", text)
        self.assertLess(text.index("TypographyScopeToggle("), text.index("S.resetTypographyDefaults"))

    def test_scope_splits_global_and_reading_application(self):
        main = (MAIN / "MainActivity.kt").read_text(encoding="utf-8")
        detail = (UI / "NotebookDetailScreen.kt").read_text(encoding="utf-8")
        markdown = (UI / "Markdown.kt").read_text(encoding="utf-8")
        preview = (UI / "SourceIndexPreview.kt").read_text(encoding="utf-8")
        chat_cards = (UI / "ChatMessageComponents.kt").read_text(encoding="utf-8")
        source_reader = (UI / "SourceReaderDocument.kt").read_text(encoding="utf-8")
        user_text = (UI / "CollapsibleUserText.kt").read_text(encoding="utf-8")
        thinking = (UI / "ChatThinkingLane.kt").read_text(encoding="utf-8")
        self.assertIn("TypographyScopes.TEXT", main)
        self.assertIn("LocalReadingTypography provides ReadingTypography(", main)
        self.assertNotIn("ScopedReadingTypography {", detail)
        self.assertIn("ScopedReadingTypography {", chat_cards)
        self.assertIn("ScopedReadingTypography {", source_reader)
        self.assertIn("ScopedReadingTypography {", user_text)
        self.assertIn("ScopedReadingTypography {", thinking)
        self.assertIn("LocalReadingTypography.current", markdown)
        self.assertIn("fontFamily = readingFontFamily", markdown)
        self.assertIn("paragraphGap", markdown)
        self.assertIn("fontFamily = reading.fontFamily", user_text)
        self.assertIn("fontFamily = reading.fontFamily", thinking)
        self.assertIn("fontFamily = reading.fontFamily", preview)
        self.assertIn("fontFamily = reading.fontFamily", source_reader)
        reading = (UI / "ReadingTypography.kt").read_text(encoding="utf-8")
        self.assertIn("val fontFamily: FontFamily?", reading)
        self.assertIn("LocalReadingTypography.current", preview)
        self.assertIn("paragraphSpacing", preview)

    def test_both_new_typography_values_are_persisted(self):
        repository = (MAIN / "data/SettingsRepository.kt").read_text(encoding="utf-8")
        app_scope = (MAIN / "AppScope.kt").read_text(encoding="utf-8")
        self.assertIn('floatPreferencesKey("paragraph_spacing")', repository)
        self.assertIn('stringPreferencesKey("typography_scope")', repository)
        self.assertIn("setParagraphSpacing", repository)
        self.assertIn("setTypographyScope", repository)
        self.assertIn("setParagraphSpacing", app_scope)
        self.assertIn("setTypographyScope", app_scope)


if __name__ == "__main__":
    unittest.main()
