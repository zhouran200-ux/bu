from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/lucent/app/ui"


class ApprovedUxContractTest(unittest.TestCase):
    def test_user_scroll_pauses_before_list_consumes_delta(self):
        text = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        pre_scroll = text.split("override fun onPreScroll(", 1)[1].split("override suspend fun onPostFling", 1)[0]
        self.assertIn("NestedScrollSource.UserInput", pre_scroll)
        self.assertIn("followStream = false", pre_scroll)
        self.assertIn(".nestedScroll(readingScrollConnection)", text)
        self.assertNotIn("LaunchedEffect(atBottom, listState.isScrollInProgress)", text)

    def test_notebook_tab_container_is_centered_and_twenty_percent_narrower(self):
        text = (UI / "NotebookDetailScreen.kt").read_text(encoding="utf-8")
        header = text.split("private fun NotebookDetailHeader(", 1)[1].split("private fun TabPill(", 1)[0]
        container = header.split("Box(", 1)[0]
        self.assertIn("horizontalArrangement = Arrangement.Center", container)
        self.assertIn(".fillMaxWidth(0.8f)", header)
        self.assertIn(".frostedGlass(cornerRadius = 24.dp)", header)
        self.assertEqual(2, header.count("modifier = Modifier.weight(1f)"))
        self.assertIn("onSelectTab(NotebookDetailTab.CHAT)", header)
        self.assertIn("onSelectTab(NotebookDetailTab.SOURCES)", header)

    def test_return_to_bottom_is_borderless_circle_with_clear_arrow(self):
        overlay = (UI / "ChatScrollOverlays.kt").read_text(encoding="utf-8")
        button = overlay.split("internal fun BoxScope.ChatScrollToBottomButton(", 1)[1]
        self.assertIn("shape = CircleShape", button)
        self.assertIn(".size(44.dp)", button)
        self.assertIn("color = glassPanelColor()", button)
        self.assertIn("shadowElevation = 4.dp", button)
        self.assertIn("Icons.Rounded.ArrowDownward", button)
        self.assertNotIn("border", button)
        self.assertNotIn("foregroundGlass", button)
        page = (UI / "NotebookChatTab.kt").read_text(encoding="utf-8")
        call = page.split("ChatScrollToBottomButton(", 1)[1].split("\n        )", 1)[0]
        self.assertIn("followStream = true", call)
        self.assertIn("listState.animateScrollToItem", call)

    # Empty history visibility and absent jump actions now live in
    # ChatHistorySheetTest.emptyHistoryShowsExplanationWithoutJumpActionsOrCallbacks.

    def test_error_details_remain_below_summary(self):
        text = (UI / "settings/ApiSettingsPage.kt").read_text(encoding="utf-8")
        self.assertLess(text.index("text = testStatusText"), text.index("lastTestResult?.let { res ->"))
        self.assertIn("appendLine(S.apiDetails)", text)
        self.assertIn("res.prettyResponseBody()", text)
        self.assertIn("onCopy = { copyRawResponse(textToCopy) }", text)


if __name__ == "__main__":
    unittest.main()
