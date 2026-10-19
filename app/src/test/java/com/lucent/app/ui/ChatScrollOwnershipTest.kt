package com.lucent.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollOwnershipTest {
    @Test fun onlyAnActualListGestureCanResumeFollowing() {
        val owner = ChatScrollOwnership()
        assertFalse(owner.finishListGesture())
        owner.listScrolled()
        assertTrue(owner.finishListGesture())
        assertFalse(owner.finishListGesture())
    }

    @Test fun capsuleTakeoverRejectsLateFlingCompletion() {
        val owner = ChatScrollOwnership()
        owner.listScrolled()
        owner.takeOver()
        assertFalse(owner.finishListGesture())
        owner.listScrolled()
        assertTrue(owner.finishListGesture())
    }
}
