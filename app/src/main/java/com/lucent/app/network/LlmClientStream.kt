package com.lucent.app.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.io.IOException

internal object LlmClientStream {
    suspend fun chatStream(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String,
        messages: List<ChatMessagePayload>,
        systemInstruction: String = "",
        reasoningConfig: com.lucent.app.data.ResolvedReasoningConfig? = null,
        handle: StreamCallHandle? = null,
        rawEndpoint: Boolean = false,
        onChunk: suspend (deltaContent: String, deltaReasoning: String?) -> Unit
    ): Result<ChatCompletionResult> = withContext(Dispatchers.IO) {
        fun ensureStreamActive() {
            coroutineContext.ensureActive()
            if (handle?.isCancelled == true) {
                throw CancellationException("Stream cancelled")
            }
        }

        var requestUrl = ""
        try {
            ensureStreamActive()
            val requestBuilder = StreamingRequestBuilder.build(
                baseUrl, spec, apiKey, model, messages, systemInstruction, reasoningConfig, rawEndpoint
            )

            requestBuilder.header("Accept", "text/event-stream")
            applyHeaders(requestBuilder, spec, apiKey)

            val request = requestBuilder.build()
            requestUrl = request.url.toString()
            val call = LlmHttpClients.streamClient.newCall(request)
            handle?.attach(call)
            val cancellationSignal = Job(coroutineContext[Job])
            cancellationSignal.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            try {
                ensureStreamActive()
                call.execute().use { response ->
                    ensureStreamActive()
                    if (!response.isSuccessful) {
                        val bodyStr = response.body?.let { body ->
                            body.source().timeout().deadline(20, java.util.concurrent.TimeUnit.SECONDS)
                            BufferedResponseLimits.read(body)
                        }.orEmpty()
                        ensureStreamActive()
                        return@withContext Result.failure(IOException(RequestFailureFormatter.http(
                            code = response.code,
                            body = bodyStr,
                            apiKey = apiKey,
                            responseMessage = response.message,
                            requestUrl = requestUrl,
                            model = model
                        )))
                    }

                    val source = response.body?.source()
                        ?: return@withContext Result.failure(IOException(RequestFailureFormatter.protocol(
                            problem = "服务商返回了空响应体",
                            requestUrl = requestUrl,
                            model = model,
                            apiKey = apiKey
                        )))
                    Result.success(StreamingResponseReader.read(source, spec, ::ensureStreamActive, onChunk))
                }
            } finally {
                cancellationSignal.complete()
                handle?.detach(call)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ensureStreamActive()
            Result.failure(IOException(RequestFailureFormatter.network(e, apiKey, requestUrl, model)))
        }
    }
}
