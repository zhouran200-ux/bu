package com.lucent.app.ui

data class ChatScrollTarget(val index: Int, val offset: Int)

object ChatScrollMath {
    fun shouldFollowAfterScroll(wasFollowing: Boolean, atBottom: Boolean, userScrolling: Boolean): Boolean = when {
        !userScrolling -> wasFollowing
        atBottom -> true
        else -> false
    }

    fun shouldFollowRegeneration(isLastMessage: Boolean, atBottom: Boolean): Boolean =
        isLastMessage && atBottom
}
