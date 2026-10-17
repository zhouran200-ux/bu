package com.lucent.app.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ApiProfileCommandQueue(private val scope: CoroutineScope) {
    companion object {
        val application by lazy { ApiProfileCommandQueue(com.lucent.app.AppScope.io) }
    }
    private var tail: Job? = null

    @Synchronized
    fun submit(operation: suspend () -> Unit, complete: (Result<Unit>) -> Unit = {}): Job {
        val previous = tail
        val job = scope.launch(start = CoroutineStart.LAZY) {
            previous?.join()
            val outcome = try {
                operation()
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
            complete(outcome)
        }
        tail = job
        job.start()
        return job
    }
}
