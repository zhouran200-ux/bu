package com.lucent.app.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ApiRequestOwner {
    private var generation = 0L
    private var job: Job? = null
    private var clearBusy: (() -> Unit)? = null

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        clearBusy?.invoke()
        clearBusy = null
    }

    fun <T> start(scope: CoroutineScope, isTargetCurrent: () -> Boolean, setBusy: (Boolean) -> Unit,
                  request: suspend () -> T, onFailure: (Exception) -> Unit = {}, accept: (T) -> Unit) {
        cancel()
        val token = generation
        clearBusy = { setBusy(false) }
        setBusy(true)
        val launched = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val value = request()
                if (token == generation && isTargetCurrent()) accept(value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (token == generation && isTargetCurrent()) onFailure(failure)
            } finally {
                if (token == generation) {
                    setBusy(false)
                    clearBusy = null
                    job = null
                }
            }
        }
        job = launched
        launched.invokeOnCompletion {
            if (token == generation && job === launched) {
                clearBusy?.invoke()
                clearBusy = null
                job = null
            }
        }
        launched.start()
    }
}

internal data class ApiRequestTarget(val profileId: String?, val url: String, val spec: String, val key: String, val active: Boolean)
