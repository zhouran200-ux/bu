package com.lucent.app.ui

import com.lucent.app.Screen

object LastScreen {

    @Volatile
    var current: Screen = Screen.Notebooks

    fun remember(screen: Screen) {
        current = screen
    }

}
