from pathlib import Path
import unittest

UI = Path(__file__).resolve().parents[2] / "app/src/main/java/com/lucent/app/ui"


class ChatSelectionContractTest(unittest.TestCase):
    def test_each_bubble_body_uses_framework_selection(self):
        text = (UI / "ChatMessageComponents.kt").read_text(encoding="utf-8")
        user = text.split("internal fun UserMessageCard(", 1)[1].split("internal fun AiMessageCard(", 1)[0]
        ai = text.split("internal fun AiMessageCard(", 1)[1].split("internal fun BubbleAction(", 1)[0]
        user_text = (UI / "CollapsibleUserText.kt").read_text(encoding="utf-8")
        self.assertIn("CollapsibleUserText(", user)
        for body in (user_text, ai):
            self.assertEqual(1, body.count("SelectionContainer {"))
            self.assertNotIn("onLongClick", body)
            self.assertNotIn("onLongPress", body)
            self.assertNotIn("LocalTextToolbar", body)
        self.assertLess(ai.index("SelectionContainer {"), ai.index("MarkdownText("))
        self.assertLess(user_text.index("SelectionContainer {"), user_text.index("text = text"))

    def test_existing_actions_stay_outside_selection_scope(self):
        text = (UI / "ChatMessageComponents.kt").read_text(encoding="utf-8")
        user = (UI / "CollapsibleUserText.kt").read_text(encoding="utf-8")
        ai = text.split("internal fun AiMessageCard(", 1)[1]
        for body in (user, ai):
            selected = body.split("SelectionContainer {", 1)[1]
            depth = 1
            end = 0
            for end, ch in enumerate(selected):
                depth += (ch == "{") - (ch == "}")
                if depth == 0:
                    break
            self.assertNotIn("BubbleAction(", selected[:end])
            self.assertNotIn("copyTextToClipboard", selected[:end])


if __name__ == "__main__":
    unittest.main()
