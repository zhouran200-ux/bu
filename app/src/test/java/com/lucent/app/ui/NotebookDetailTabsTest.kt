package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NotebookDetailTabsTest {
    @Test fun savedTabRestoresByNameAndInvalidValuesFallBackToChat() {
        assertEquals(NotebookDetailTab.SOURCES, notebookDetailTab("SOURCES"))
        assertEquals(NotebookDetailTab.CHAT, notebookDetailTab("CHAT"))
        assertEquals(NotebookDetailTab.CHAT, notebookDetailTab(null))
        assertEquals(NotebookDetailTab.CHAT, notebookDetailTab("removed-tab"))
    }

    @Test
    fun detailTabsKeepChatFirstSoSwipeOrderIsStable() {
        assertEquals(
            listOf(NotebookDetailTab.CHAT, NotebookDetailTab.SOURCES),
            NotebookDetailTab.entries.toList()
        )
    }

    @Test
    fun sourcesBackReturnsToChatInsteadOfLeavingNotebook() {
        assertEquals(NotebookDetailTab.CHAT, notebookBackTarget(NotebookDetailTab.SOURCES))
    }

    @Test
    fun chatBackLeavesNotebook() {
        assertEquals(null, notebookBackTarget(NotebookDetailTab.CHAT))
    }

    @Test
    fun backDuringSwipeNeverSkipsChatParent() {
        val tabs = NotebookDetailTab.entries
        for (current in tabs) for (settled in tabs) for (target in tabs) {
            val expected = if (listOf(current, settled, target).all { it == NotebookDetailTab.CHAT }) null else NotebookDetailTab.CHAT
            assertEquals(expected, notebookBackTarget(current, settled, target))
        }
    }

    @Test
    fun detailTabIndexMatchesPagerPage() {
        val tabs = NotebookDetailTab.entries
        assertEquals(0, tabs.indexOf(NotebookDetailTab.CHAT))
        assertEquals(1, tabs.indexOf(NotebookDetailTab.SOURCES))
        assertEquals(NotebookDetailTab.CHAT, tabs[0])
        assertEquals(NotebookDetailTab.SOURCES, tabs[1])
    }
}
