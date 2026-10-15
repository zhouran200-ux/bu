from pathlib import Path
import unittest

UI = Path(__file__).resolve().parents[2] / "app/src/main/java/com/lucent/app/ui"


class HistoryEntryContractTest(unittest.TestCase):
    def test_history_action_is_between_reasoning_and_settings(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        composer = text.split("private fun BottomGlassComposer(", 1)[1].split("private fun DialogueSettingsSheet(", 1)[0]
        self.assertLess(composer.index('contentDescription = "思考程度"'), composer.index('contentDescription = "对话记录"'))
        self.assertLess(composer.index('contentDescription = "对话记录"'), composer.index('contentDescription = "对话设置"'))
        self.assertIn("Icons.Rounded.History", composer)
        self.assertIn("onOpenHistory()", composer)
        self.assertIn("onOpenHistory = { openHistorySheet() }", text)
        self.assertIn("modifier = Modifier.weight(1f).padding(end = 12.dp)", composer)

    def test_regeneration_preflight_precedes_destructive_changes_and_resets_stats(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        regenerate = text.split("fun regenerateAnswer(", 1)[1].split("fun resendEditedQuestion(", 1)[0]
        self.assertLess(regenerate.index("selection.first.apiKey.isBlank()"), regenerate.index("messages[idx] ="))
        self.assertLess(regenerate.index("selection.first.apiKey.isBlank()"), regenerate.index("deleteByIds"))
        for reset in ("firstTokenMillis = null", "generationMillis = null", "cachedTokensKnown = false"):
            self.assertIn(reset, regenerate)

    def test_rail_has_no_hidden_history_navigation(self):
        text = (UI / "ChatScrollRail.kt").read_text(encoding="utf-8")
        for old in ("onOpenHistory", "latestHistory", "detectTapGestures", "点按打开对话记录", "onClick {"):
            self.assertNotIn(old, text)
        self.assertIn("detectVerticalDragGestures", text)
        self.assertIn("setProgress", text)
        self.assertIn("if (show || dragging || shown > 0.01f)", text)
        self.assertIn(".size(width = 28.dp, height = hitHeight)", text)
        self.assertIn("localToRoot", text)
        source = (UI / "NotebookSourcesTab.kt").read_text(encoding="utf-8")
        self.assertIn("FloatingScrollCapsule(", source)
        self.assertIn("enabled = active", source)


if __name__ == "__main__":
    unittest.main()
