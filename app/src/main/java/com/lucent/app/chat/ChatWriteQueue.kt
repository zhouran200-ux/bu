package com.lucent.app.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ChatWriteQueue(
    private val scope: CoroutineScope,
    private val onChanged: (Int, Exception?) -> Unit
) {
    private class Entry(val messageId: Long?, var write: suspend () -> Unit)

    private val pending = ArrayDeque<Entry>()
    private var active: Entry? = null
    private var tail: Job? = null
    private var failure: Exception? = null

    val isIdle: Boolean get() = pending.isEmpty() && tail?.isCompleted != false

    fun submit(messageId: Long? = null, block: suspend () -> Unit): Deferred<Result<Unit>> {
        val last = pending.lastOrNull()
        val entry = if (messageId != null && last?.messageId == messageId && last !== active) {
            last.apply { write = block }
        } else {
            Entry(messageId, block).also { pending.addLast(it) }
        }
        changed()
        return schedule { drainThrough(entry) }
    }

    fun retry(): Deferred<Result<Unit>> {
        val last = pending.lastOrNull() ?: return CompletableDeferred(Result.success(Unit))
        return schedule { drainThrough(last) }
    }

    suspend fun awaitIdle() { tail?.join() }

    private fun schedule(block: suspend () -> Result<Unit>): Deferred<Result<Unit>> {
        val previous = tail
        val receipt = CompletableDeferred<Result<Unit>>()
        val worker = scope.launch(start = CoroutineStart.LAZY) {
            previous?.join()
            receipt.complete(block())
        }
        worker.invokeOnCompletion { failure ->
            if (failure != null) receipt.cancel(CancellationException("Write worker cancelled", failure))
        }
        tail = worker
        worker.start()
        return receipt
    }

    private suspend fun drainThrough(target: Entry): Result<Unit> {
        if (pending.none { it === target }) return Result.success(Unit)
        while (pending.isNotEmpty()) {
            val entry = pending.first()
            active = entry
            try {
                entry.write()
            } catch (cancelled: CancellationException) {
                pending.removeFirst()
                if (pending.isEmpty()) failure = null
                changed()
                throw cancelled
            } catch (error: Exception) {
                failure = error
                changed()
                return Result.failure(error)
            } finally {
                active = null
            }
            pending.removeFirst()
            if (pending.isEmpty()) failure = null
            changed()
            if (entry === target) break
        }
        return Result.success(Unit)
    }

    private fun changed() { onChanged(pending.size, failure) }
}
