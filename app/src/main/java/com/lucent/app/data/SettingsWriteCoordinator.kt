package com.lucent.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class SettingsWriteCoordinator(private val scope: CoroutineScope) {
    internal class Setting<T>(val cached: () -> T, val cache: (T) -> Unit) {
        internal var revision = 0L
        private var committed: T? = null
        private var initialized = false
        fun change(value: T, persisted: suspend () -> T): Change = object : Change {
            private var acceptedRevision = 0L
            override fun accept() {
                if (!initialized) {
                    committed = cached()
                    initialized = true
                }
                acceptedRevision = ++revision
                cache(value)
            }
            override suspend fun read() { committed = persisted() }
            override fun finish(success: Boolean) {
                if (success) committed = value
                @Suppress("UNCHECKED_CAST")
                if (revision == acceptedRevision) cache(committed as T)
            }
        }
    }

    internal interface Change {
        fun accept()
        suspend fun read()
        fun finish(success: Boolean)
    }

    private val lock = Any()
    private var tail: Job? = null

    fun submit(
        changes: List<Change>,
        write: suspend () -> Unit,
        failed: suspend (Exception) -> Unit
    ): Job = synchronized(lock) {
        changes.forEach { it.accept() }
        val previous = tail
        var finalized = false
        fun finish(success: Boolean) = synchronized(lock) {
            if (!finalized) {
                finalized = true
                changes.forEach { it.finish(success) }
            }
        }
        scope.launch {
            try {
                previous?.join()
                changes.forEach { it.read() }
                write()
                finish(true)
            } catch (cancelled: CancellationException) {
                finish(false)
                throw cancelled
            } catch (failure: Exception) {
                finish(false)
                failed(failure)
            }
        }.also { job ->
            tail = job
            job.invokeOnCompletion { finish(false) }
        }
    }
}
