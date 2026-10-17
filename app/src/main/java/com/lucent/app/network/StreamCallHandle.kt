package com.lucent.app.network

import okhttp3.Call

class StreamCallHandle {
    private val lock = Any()
    private var call: Call? = null

    @Volatile
    var isCancelled: Boolean = false
        private set

    fun attach(value: Call) {
        val shouldCancel = synchronized(lock) {
            if (isCancelled) {
                true
            } else {
                call = value
                false
            }
        }
        if (shouldCancel) value.cancel()
    }

    fun detach(value: Call) {
        synchronized(lock) {
            if (call === value) call = null
        }
    }

    fun cancel() {
        val callToCancel = synchronized(lock) {
            isCancelled = true
            call.also { call = null }
        }
        callToCancel?.cancel()
    }
}
