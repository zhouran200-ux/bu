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

    def test_empty_history_has_no_jump_chips(self):
        text = (UI / "ChatHistorySheet.kt").read_text(encoding="utf-8")
        section = text.split("if (hasRecords) {", 1)[1].split("if (!hasRecords)", 1)[0]
        self.assertEqual(2, section.count("JumpChip("))
        empty_state = text.split("if (!hasRecords) {", 1)[1].split("} else {", 1)[0]
        self.assertNotIn("JumpChip", empty_state)

    def test_error_details_remain_below_summary(self):
        text = (UI / "settings/ApiSettingsPage.kt").read_text(encoding="utf-8")
        self.assertLess(text.index("text = testStatusText"), text.index("lastTestResult?.let { res ->"))
        self.assertIn('appendLine("详细信息")', text)
        self.assertIn("res.prettyResponseBody()", text)
        self.assertIn("onCopy = { copyRawResponse(textToCopy) }", text)


if __name__ == "__main__":
    unittest.main()
