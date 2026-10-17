package com.lucent.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class BufferedReply(val code: Int, val message: String, val isSuccessful: Boolean, val body: String)

internal object BufferedTransport {
    suspend fun executeBuffered(client: OkHttpClient, request: Request): BufferedReply = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val call = client.newCall(request)
        val cancellationSignal = Job(coroutineContext[Job])
        cancellationSignal.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        try {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                coroutineContext.ensureActive()
                BufferedReply(response.code, response.message, response.isSuccessful, body)
            }
        } catch (failure: Exception) {
            coroutineContext.ensureActive()
            throw failure
        } finally {
            cancellationSignal.complete()
        }
    }

}
