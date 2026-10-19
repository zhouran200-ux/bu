from pathlib import Path
import unittest

UI = Path(__file__).resolve().parents[2] / "app/src/main/java/com/lucent/app/ui"


class HistoryEntryContractTest(unittest.TestCase):
    def test_history_action_is_between_reasoning_and_settings(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        composer = (UI / "ChatOverlayComponents.kt").read_text(encoding="utf-8").split("internal fun BottomGlassComposer(", 1)[1]
        self.assertLess(composer.index("contentDescription = S.composerReasoning"), composer.index("contentDescription = S.composerHistory"))
        self.assertLess(composer.index("contentDescription = S.composerHistory"), composer.index("contentDescription = S.composerSettings"))
        self.assertIn("Icons.Rounded.History", composer)
        self.assertIn("onOpenHistory()", composer)
        self.assertIn("onOpenHistory = { openHistorySheet() }", text)
        self.assertIn("modifier = Modifier.weight(1f).padding(end = 12.dp)", composer)

    def test_composer_controls_are_one_third_shorter_without_losing_width(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        composer = (UI / "ChatOverlayComponents.kt").read_text(encoding="utf-8").split("internal fun BottomGlassComposer(", 1)[1]
        self.assertEqual(3, composer.count(".size(width = 36.dp, height = 24.dp)"))
        self.assertIn(".size(width = 44.dp, height = (44f * 2f / 3f).dp)", composer)
        self.assertIn("Modifier.padding(horizontal = 10.dp, vertical = 2.dp)", composer)
        self.assertNotIn(".size(36.dp)", composer)
        self.assertNotIn(".size(44.dp)", composer)

    def test_history_jump_buttons_preserve_glass_alpha(self):
        text = (UI / "ChatHistorySheet.kt").read_text(encoding="utf-8")
        jump = text.split("private fun JumpChip(", 1)[1].split("private fun HistoryRow(", 1)[0]
        # Jump labels, hints and real callbacks are covered by ChatHistorySheetTest.
        self.assertIn("it.copy(alpha = it.alpha * fade)", jump)
        self.assertNotIn("glassControlColor().copy(alpha = fade)", jump)

    def test_regeneration_preflight_precedes_destructive_changes_and_resets_stats(self):
        text = (UI / "chat/ChatActionsCoordinator.kt").read_text(encoding="utf-8")
        regenerate = text.split("fun regenerateAnswer(", 1)[1].split("fun resendEditedQuestion(", 1)[0]
        self.assertLess(regenerate.index("selection.first.apiKey.isBlank()"), regenerate.index("askModel(answerId)"))
        self.assertNotIn("messages[idx] =", regenerate)
        self.assertNotIn("session.deleteMessages(", regenerate)

    def test_rail_has_no_hidden_history_navigation(self):
        text = (UI / "ChatScrollRail.kt").read_text(encoding="utf-8")
        for old in ("onOpenHistory", "latestHistory", "detectTapGestures", "点按打开对话记录", "onClick {"):
            self.assertNotIn(old, text)
        self.assertIn("detectChatRailDrag", text)
        gesture = (UI / "ChatRailGesture.kt").read_text(encoding="utf-8")
        self.assertIn("awaitVerticalTouchSlopOrCancellation", gesture)
        self.assertIn("finally", gesture)
        self.assertIn("onRelease()", gesture)
        self.assertIn("setProgress", text)
        self.assertIn("if (show || dragging || shown > 0.01f)", text)
        self.assertIn(".size(width = 28.dp, height = hitHeight)", text)
        self.assertIn("localToRoot", text)
        source = (UI / "NotebookSourcesTab.kt").read_text(encoding="utf-8")
        self.assertIn("FloatingScrollCapsule(", source)
        self.assertIn("enabled = active", source)


if __name__ == "__main__":
    unittest.main()
