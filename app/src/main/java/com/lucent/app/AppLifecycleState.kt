package com.lucent.app

/**
 * Tracks whether an app window is visible.
 *
 * Background time must never be charged against an in-flight generation: a frozen process cannot
 * read its socket, and wall-clock timers would otherwise report a false timeout on return.
 */
object AppLifecycleState {
    @Volatile
    var isForeground: Boolean = false
        private set

    /** Bumped on every return to the foreground so watchdogs can restart their countdown. */
    @Volatile
    var resumeToken: Long = 0L
        private set

    fun onForeground() {
        resumeToken += 1
        isForeground = true
    }

    fun onBackground() {
        isForeground = false
    }
}
