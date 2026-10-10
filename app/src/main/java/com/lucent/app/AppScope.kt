package com.lucent.app

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppScope {
    val io = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e(TAG, "background write failed", throwable)
        }
    )

    private const val TAG = "LucentAppScope"
}
