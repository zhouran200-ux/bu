package com.lucent.app.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal object LlmClientConnection {
    suspend fun testConnection(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String = "",
        rawEndpoint: Boolean = false
    ): Result<TestConnectionResult> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var resolvedUrl = ""
        var usedUa: String? = null
        val probeKind = when {
            model.isNotBlank() -> ConnectionProbeKind.Generation
            rawEndpoint -> ConnectionProbeKind.Endpoint
            else -> ConnectionProbeKind.ModelCatalog
        }
        try {
            val trimmed = baseUrl.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("基础 URL 不能为空"))
            }

            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBuilder = Request.Builder()
            val isModelSpecified = model.isNotBlank()
            val url: String

            if (isModelSpecified) {
                when (spec) {
                    ApiSpec.OPENAI -> {
                        url = EndpointUrls.generation(trimmed, spec, apiKey, model, rawEndpoint, stream = false).toString()
                        val payload = JSONObject().apply {
                            put("model", model.trim())
                            put("messages", JSONArray().put(JSONObject().apply {
                                put("role", "user")
                                put("content", "ping")
                            }))
                            applyOpenAiProbeCap(this, model)
                        }
                        requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                    }
                    ApiSpec.ANTHROPIC -> {
                        url = EndpointUrls.generation(trimmed, spec, apiKey, model, rawEndpoint, stream = false).toString()
                        val payload = JSONObject().apply {
                            put("model", model.trim())
                            put("max_tokens", 1)
                            put("messages", JSONArray().put(JSONObject().apply {
                                put("role", "user")
                                put("content", "ping")
                            }))
                        }
                        requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                    }
                    ApiSpec.GOOGLE -> {
                        url = EndpointUrls.generation(trimmed, spec, apiKey, model, rawEndpoint, stream = false).toString()

                        val payload = JSONObject().apply {
                            put("contents", JSONArray().put(JSONObject().apply {
                                put("parts", JSONArray().put(JSONObject().apply {
                                    put("text", "ping")
                                }))
                            }))
                        }
                        requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                    }
                }
            } else {
                url = EndpointUrls.models(trimmed, spec, apiKey, rawEndpoint).toString()
                if (rawEndpoint) requestBuilder.url(url).head() else requestBuilder.url(url).get()
            }
            resolvedUrl = url
            usedUa = applyHeaders(requestBuilder, spec, apiKey).ifBlank { null }

            val response = BufferedTransport.executeBuffered(LlmHttpClients.client, requestBuilder.build())
            val latency = System.currentTimeMillis() - start
            val bodyStr = response.body
            if (!response.isSuccessful) {
                val errMsg = try {
                    val json = JSONObject(bodyStr)
                    val errorObj = json.optJSONObject("error")
                    errorObj?.optString("message") ?: DiagnosticRedaction.text(bodyStr, apiKey).take(120)
                } catch (_: Exception) {
                    DiagnosticRedaction.text(bodyStr, apiKey).take(120)
                }
                val failureResult = TestConnectionResult(
                    success = false,
                    httpCode = response.code,
                    latencyMs = latency,
                    targetModel = model,
                    requestUrl = resolvedUrl,
                    rawResponseBody = bodyStr,
                    errorMessage = "HTTP ${response.code}: ${errMsg.ifBlank { response.message }}",
                    userAgentUsed = usedUa,
                    probeKind = probeKind
                )
                return@withContext Result.success(failureResult.redacted(apiKey))
            }
            Result.success(
                TestConnectionResult(
                    success = true,
                    httpCode = response.code,
                    latencyMs = latency,
                    targetModel = model,
                    requestUrl = resolvedUrl,
                    rawResponseBody = bodyStr,
                    errorMessage = null,
                    userAgentUsed = usedUa,
                    probeKind = probeKind
                ).redacted(apiKey)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            val latency = System.currentTimeMillis() - start
            Result.success(
                TestConnectionResult(
                    success = false,
                    httpCode = 0,
                    latencyMs = latency,
                    targetModel = model,
                    requestUrl = resolvedUrl,
                    rawResponseBody = DiagnosticRedaction.text(e.stackTraceToString(), apiKey).take(1500),
                    errorMessage = e.message ?: e.javaClass.simpleName,
                    userAgentUsed = usedUa,
                    probeKind = probeKind
                ).redacted(apiKey)
            )
        }
    }
}
