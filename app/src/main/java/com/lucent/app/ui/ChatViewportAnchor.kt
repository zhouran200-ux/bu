package com.lucent.app.ui

internal class ChatViewportAnchor {
    private var height = 0
    private var wasAtBottom = false
    private var followingResize = false

    fun userScrolled() {
        followingResize = false
        wasAtBottom = false
    }

    fun update(viewportHeight: Int, atBottom: Boolean): Boolean {
        if (viewportHeight <= 0) return false
        val shrinking = height > 0 && viewportHeight < height
        val keepBottom = shrinking && (wasAtBottom || followingResize)
        if (viewportHeight > height) followingResize = false
        if (keepBottom) followingResize = true
        height = viewportHeight
        wasAtBottom = atBottom
        return keepBottom
    }
}
