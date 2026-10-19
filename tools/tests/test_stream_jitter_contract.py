from pathlib import Path
import unittest

UI = Path(__file__).resolve().parents[2] / "app/src/main/java/com/lucent/app/ui"


class StreamJitterContractTest(unittest.TestCase):
    def test_follow_uses_measured_geometry_without_byte_count_reanchoring(self):
        page = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        follow = (UI / "ChatTailFollow.kt").read_text(encoding="utf-8")
        self.assertIn("FollowChatTail(", page)
        self.assertNotIn("streamProgress", page)
        self.assertNotIn("followStreamUpdates", page)
        self.assertIn("snapshotFlow { listState.chatTailLayout()", follow)
        self.assertIn("listState.scrollBy(distance)", follow)
        self.assertNotIn("requestScrollToItem", follow)
        self.assertNotIn("delay(", follow)
        coordinator = (UI / "chat/ChatActionsCoordinator.kt").read_text(encoding="utf-8")
        initial = coordinator.split("fun startAnswer(", 1)[1].split("fun regenerateAnswer(", 1)[0]
        self.assertNotIn("animateScrollToItem", initial)
        self.assertNotIn("delay(", initial)

    def test_capsule_press_invalidates_old_fling_completion(self):
        page = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        self.assertIn("scrollOwnership.listScrolled()", page)
        self.assertIn("if (scrollOwnership.finishListGesture())", page)
        self.assertIn("scrollOwnership.takeOver()", page)
        press = page.split("onPress =", 1)[1].split("onTap =", 1)[0]
        self.assertIn("actions.followStream = false", press)
        self.assertIn("viewportAnchor.userScrolled()", press)
        self.assertIn("tryAwaitRelease()", press)
        rail = (UI / "ChatScrollRail.kt").read_text(encoding="utf-8")
        press = rail.split("onPress =", 1)[1].split("onDragStart =", 1)[0]
        self.assertIn("latestDragStart()", press)
        self.assertIn("stopScroll(MutatePriority.UserInput)", press)
        self.assertNotIn("if (dragging) return@collect", rail)

    def test_live_thought_retains_height_until_explicit_collapse(self):
        lane = (UI / "ChatThinkingLane.kt").read_text(encoding="utf-8")
        state = (UI / "ThinkingLaneState.kt").read_text(encoding="utf-8")
        self.assertIn("streaming || lane.retainLiveHeight", lane)
        self.assertIn("if (!expanded) retainLiveHeight = false", state)
        self.assertIn("lane.retainStreamingHeight(streaming)", lane)

    def test_citation_buffer_is_display_only_and_finalization_invalidates_parse(self):
        markdown = (UI / "Markdown.kt").read_text(encoding="utf-8")
        self.assertIn("LaunchedEffect(text, streaming,", markdown)
        self.assertIn("if (streaming && citationsEnabled) streamingMarkdownTail(text) else text", markdown)
        card = (UI / "ChatMessageComponents.kt").read_text(encoding="utf-8")
        self.assertIn("streaming = streaming,", card.split("MarkdownText(", 1)[1])


if __name__ == "__main__":
    unittest.main()
