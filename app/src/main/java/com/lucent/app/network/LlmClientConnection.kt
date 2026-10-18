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
        try {
            val trimmed = baseUrl.trim().trimEnd('/')
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
                        url = if (rawEndpoint || trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
                        val payload = JSONObject().apply {
                            put("model", model.trim())
                            put("messages", JSONArray().put(JSONObject().apply {
                                put("role", "user")
                                put("content", "ping")
                            }))
                            put("max_tokens", 1)
                        }
                        requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                    }
                    ApiSpec.ANTHROPIC -> {
                        url = if (trimmed.endsWith("/messages")) trimmed else if (trimmed.endsWith("/v1")) "$trimmed/messages" else "$trimmed/v1/messages"
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
                        val cleanModel = model.trim().removePrefix("models/")
                        val base = if (trimmed.endsWith("/models")) trimmed.removeSuffix("/models") else trimmed
                        val endpoint = "$base/models/$cleanModel:generateContent"
                        url = if (apiKey.isNotBlank() && !endpoint.contains("key=")) {
                            if (endpoint.contains("?")) "$endpoint&key=${queryValue(apiKey)}" else "$endpoint?key=${queryValue(apiKey)}"
                        } else endpoint

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
                url = if (rawEndpoint) trimmed else when (spec) {
                    ApiSpec.GOOGLE -> {
                        val base = if (trimmed.endsWith("/models")) trimmed else "$trimmed/models"
                        if (apiKey.isNotBlank() && !base.contains("key=")) {
                            if (base.contains("?")) "$base&key=${queryValue(apiKey)}" else "$base?key=${queryValue(apiKey)}"
                        } else base
                    }
                    ApiSpec.ANTHROPIC, ApiSpec.OPENAI -> {
                        if (trimmed.endsWith("/models")) trimmed else "$trimmed/models"
                    }
                }
                requestBuilder.url(url).get()
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
                    userAgentUsed = usedUa
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
                    userAgentUsed = usedUa
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
                    userAgentUsed = usedUa
                ).redacted(apiKey)
            )
        }
    }
}
