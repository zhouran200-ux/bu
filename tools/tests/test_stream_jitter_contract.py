from pathlib import Path
import unittest

UI = Path(__file__).resolve().parents[2] / "app/src/main/java/com/lucent/app/ui"


class StreamJitterContractTest(unittest.TestCase):
    def test_streaming_does_not_own_chat_scroll_position(self):
        page = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        coordinator = (UI / "chat/ChatActionsCoordinator.kt").read_text(encoding="utf-8")
        self.assertNotIn("followStream", page + coordinator)
        self.assertNotIn("FollowChatTail", page)
        self.assertNotIn("ChatScrollMath", page + coordinator)

    def test_chat_scroll_remains_manual_during_streaming(self):
        page = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertNotIn("followStream", page)
        self.assertNotIn("ChatScrollOwnership", page)
        self.assertIn("FloatingScrollCapsule", page)
        rail = (UI / "ChatScrollRail.kt").read_text(encoding="utf-8")
        self.assertIn("latestDragStart()", rail)
        self.assertIn("stopScroll(MutatePriority.UserInput)", rail)
        self.assertNotIn("if (dragging) return@collect", rail)

    def test_live_thought_stays_in_a_fixed_scrollable_lane(self):
        lane = (UI / "ChatThinkingLane.kt").read_text(encoding="utf-8")
        state = (UI / "ThinkingLaneState.kt").read_text(encoding="utf-8")
        self.assertIn("heightIn(max = THINKING_LANE_MAX_HEIGHT)", lane)
        self.assertIn("verticalScroll(lane.scroll)", lane)
        self.assertIn("retainLiveHeight", state)
        self.assertNotIn("fullExpanded", state + lane)

    def test_citation_buffer_is_display_only_and_finalization_invalidates_parse(self):
        markdown = (UI / "Markdown.kt").read_text(encoding="utf-8")
        self.assertIn("LaunchedEffect(renderer, style, streaming)", markdown)
        self.assertIn("val display = if ((streaming || !snapshot.complete) && style.citationsEnabled) {", markdown)
        self.assertIn("streamingMarkdownTail(snapshot.text)", markdown)
        self.assertIn("snapshotFlow { latestReveal.value }", markdown)
        self.assertIn(".conflate()", markdown)
        renderer = (UI / "MarkdownRender.kt").read_text(encoding="utf-8")
        self.assertIn("internal class MarkdownRenderer", renderer)
        self.assertIn("sameBlock(", renderer)
        card = (UI / "ChatMessageComponents.kt").read_text(encoding="utf-8")
        self.assertIn("streaming = streaming,", card.split("MarkdownText(", 1)[1])


if __name__ == "__main__":
    unittest.main()
