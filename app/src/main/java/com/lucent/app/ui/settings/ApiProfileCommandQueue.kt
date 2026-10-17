package com.lucent.app.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ApiProfileCommandQueue(private val scope: CoroutineScope) {
    private var tail: Job? = null

    fun submit(operation: suspend () -> Unit, complete: (Result<Unit>) -> Unit = {}): Job {
        val previous = tail
        val job = scope.launch(start = CoroutineStart.LAZY) {
            previous?.join()
            try {
                operation()
                complete(Result.success(Unit))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                complete(Result.failure(failure))
            }
        }
        tail = job
        job.start()
        return job
    }
}
