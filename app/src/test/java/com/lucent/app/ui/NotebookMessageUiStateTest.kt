package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotebookMessageUiStateTest {
    @Test fun messagesStartCollapsedAndExpandIndependently() {
        val owner = NotebookMessageUiState()
        assertFalse(owner.userExpanded(1))
        assertFalse(owner.userExpanded(2))

        owner.setUserExpanded(1, true)
        assertTrue(owner.userExpanded(1))
        assertFalse(owner.userExpanded(2))

        owner.setUserExpanded(2, true)
        owner.setUserExpanded(1, false)
        assertFalse(owner.userExpanded(1))
        assertTrue(owner.userExpanded(2))
    }

    @Test fun sameMessageIdIsIsolatedAcrossNotebookOwners() {
        val first = NotebookMessageUiState()
        val second = NotebookMessageUiState()
        first.setUserExpanded(42, true)
        assertTrue(first.userExpanded(42))
        assertFalse(second.userExpanded(42))

        second.setUserExpanded(42, true)
        first.resetUserMessages()
        assertFalse(first.userExpanded(42))
        assertTrue(second.userExpanded(42))
    }

    @Test fun reenteringWithANewOwnerDoesNotRestoreExpansion() {
        val previousVisit = NotebookMessageUiState()
        previousVisit.setUserExpanded(42, true)
        val newVisit = NotebookMessageUiState()

        assertTrue(previousVisit.userExpanded(42))
        assertFalse(newVisit.userExpanded(42))
    }

    @Test fun resetClearsEveryUserExpansionAndAllowsNewChanges() {
        val owner = NotebookMessageUiState()
        owner.setUserExpanded(1, true)
        owner.setUserExpanded(2, true)
        owner.resetUserMessages()
        owner.resetUserMessages()
        assertFalse(owner.userExpanded(1))
        assertFalse(owner.userExpanded(2))

        owner.setUserExpanded(2, true)
        assertFalse(owner.userExpanded(1))
        assertTrue(owner.userExpanded(2))
    }
}
