package com.lucent.app.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate

internal suspend fun followStreamUpdates(
    updates: Flow<Int>,
    canFollow: () -> Boolean,
    awaitLayout: suspend () -> Unit,
    onFollow: () -> Unit
) {
    updates.conflate().collect { progress ->
        if (progress > 0 && canFollow()) {
            awaitLayout()
            if (canFollow()) onFollow()
        }
    }
}
