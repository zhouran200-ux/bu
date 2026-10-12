package com.lucent.app.ui

import android.content.Context
import android.widget.Toast

object LucentToast {

    private val lock = Any()
    private var current: Toast? = null

    fun show(context: Context, message: String) {
        val appContext = context.applicationContext
        synchronized(lock) {
            current?.cancel()
            current = Toast.makeText(appContext, message, Toast.LENGTH_SHORT).also { it.show() }
        }
    }
}
