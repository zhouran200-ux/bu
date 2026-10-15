package com.lucent.app.chat

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal suspend fun guardedNotebookDeletion(
    dispatcher: CoroutineDispatcher,
    prepare: suspend () -> Unit,
    delete: suspend () -> Unit,
    finish: (Boolean) -> Unit
) {
    var prepared = false
    var success = false
    try {
        withContext(dispatcher) {
            prepare()
            prepared = true
        }
        delete()
        success = true
    } finally {
        if (prepared) withContext(NonCancellable + dispatcher) { finish(success) }
    }
}
