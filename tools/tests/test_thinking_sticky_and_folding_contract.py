from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class ThinkingStickyAndFoldingContractTest(unittest.TestCase):
    def test_every_ai_reply_keeps_a_thinking_header(self):
        cards = (UI / "ChatMessageComponents.kt").read_text(encoding="utf-8")
        ai = cards.split("internal fun AiMessageCard(", 1)[1].split("internal fun BubbleAction(", 1)[0]
        self.assertIn("ThinkingLane(", ai)
        self.assertNotIn("if (streaming || liveThought != null", ai)
        header = (UI / "ThinkingHeader.kt").read_text(encoding="utf-8")
        self.assertIn("!hasThought -> S.thinkingUnsupported", header)
        lane = (UI / "ChatThinkingLane.kt").read_text(encoding="utf-8")
        self.assertIn("lane.expanded && canExpand", lane)

    def test_sticky_header_uses_actual_top_owner_and_shared_state(self):
        sticky = (UI / "StickyThinkingHeader.kt").read_text(encoding="utf-8")
        self.assertIn("layout.viewportStartOffset", sticky)
        self.assertIn('message.role != "assistant"', sticky)
        self.assertIn("!lane.expanded", sticky)
        self.assertIn("owner)", sticky)
        self.assertIn("onToggle(); lane.toggle()", sticky)
        self.assertNotIn("lastOrNull { it.role", sticky)
        page = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertIn("StickyThinkingHeader(", page)
        self.assertIn("thinkingState = rememberThinkingLaneState(", page)

    def test_user_expansion_belongs_to_notebook_visit_not_lazy_item_or_session(self):
        detail = (UI / "NotebookDetailScreen.kt").read_text(encoding="utf-8")
        self.assertIn("remember(notebookId) { NotebookMessageUiState() }", detail)
        self.assertIn("if (!active) messageUiState.resetUserMessages()", detail)
        self.assertIn("LocalNotebookMessageUiState provides messageUiState", detail)
        page = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertIn("expanded = messageUiState.userExpanded(msg.id)", page)
        self.assertNotIn("resetUserMessages()", page)
        state = (UI / "ThinkingLaneState.kt").read_text(encoding="utf-8")
        users = state.split("internal class NotebookMessageUiState", 1)[1].split("@Composable", 1)[0]
        for forbidden in ("rememberSaveable", "SharedPreferences", "ReadingPositionMemory", "ChatSession"):
            self.assertNotIn(forbidden, users)

    def test_collapsing_preserves_full_text_and_selection(self):
        component = (UI / "CollapsibleUserText.kt").read_text(encoding="utf-8")
        self.assertIn("UserMessageCollapsedLines = 6", component)
        self.assertIn("onTextLayout", component)
        self.assertIn("didOverflowHeight", component)
        self.assertIn("SubcomposeLayout", component)
        self.assertNotIn("mutableStateOf", component)
        self.assertIn("SelectionContainer", component)
        self.assertNotIn("text.take(", component)
        cards = (UI / "ChatMessageComponents.kt").read_text(encoding="utf-8")
        self.assertIn("copyPlainTextWithToast(context, chatBodyForCopy(message.content))", cards)
        self.assertNotIn("copyTextToClipboard", cards)
        clipboard = (UI / "ClipboardText.kt").read_text(encoding="utf-8")
        self.assertIn("fun copyPlainText(", clipboard)
        self.assertIn("fun copyPlainTextWithToast(", clipboard)


if __name__ == "__main__":
    unittest.main()
