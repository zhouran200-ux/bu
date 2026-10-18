package com.lucent.app.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import org.json.JSONException
import org.json.JSONObject

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

        try {
            ensureStreamActive()
            val requestBuilder = StreamingRequestBuilder.build(
                baseUrl, spec, apiKey, model, messages, systemInstruction, reasoningConfig, rawEndpoint
            )

            requestBuilder.header("Accept", "text/event-stream")
            applyHeaders(requestBuilder, spec, apiKey)

            val call = LlmHttpClients.streamClient.newCall(requestBuilder.build())
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
                        val bodyStr = response.body?.string().orEmpty()
                        ensureStreamActive()
                        val errMsg = try {
                            val json = JSONObject(bodyStr)
                            val errorObj = json.optJSONObject("error")
                            errorObj?.optString("message") ?: DiagnosticRedaction.text(bodyStr, apiKey).take(200)
                        } catch (_: JSONException) {
                            DiagnosticRedaction.text(bodyStr, apiKey).take(200)
                        }
                        return@withContext Result.failure(Exception("HTTP ${response.code}: ${DiagnosticRedaction.text(errMsg.ifBlank { response.message }, apiKey)}"))
                    }

                    val source = response.body?.source()
                        ?: return@withContext Result.failure(Exception("响应数据为空"))
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
            Result.failure(DiagnosticRedaction.failure(e, apiKey))
        }
    }
}
