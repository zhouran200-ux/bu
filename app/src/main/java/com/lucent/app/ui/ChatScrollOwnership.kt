package com.lucent.app.ui

internal class ChatScrollOwnership {
    private var listGesturePendingEnd = false

    fun listScrolled() { listGesturePendingEnd = true }

    fun takeOver() { listGesturePendingEnd = false }

    fun finishListGesture(): Boolean {
        val owned = listGesturePendingEnd
        listGesturePendingEnd = false
        return owned
    }
}
