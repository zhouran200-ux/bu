package com.lucent.app

object AppLifecycleState {
    @Volatile
    var isForeground: Boolean = false
        private set

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
