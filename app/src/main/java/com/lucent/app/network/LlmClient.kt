package com.lucent.app.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LlmClient {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    internal val streamClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun applyHeaders(
        builder: Request.Builder,
        spec: ApiSpec,
        apiKey: String
    ): String {
        val effectiveUa = com.lucent.app.data.SettingsCache.customUserAgent.trim()
        if (effectiveUa.isNotBlank()) {
            builder.header("User-Agent", effectiveUa)
            if (effectiveUa.contains("node-fetch", ignoreCase = true)) {
                builder.header("Accept", "*/*")
            }
        }
        when (spec) {
            ApiSpec.OPENAI -> {
                if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
            }
            ApiSpec.ANTHROPIC -> {
                if (apiKey.isNotBlank()) {
                    builder.header("x-api-key", apiKey)
                    builder.header("Authorization", "Bearer $apiKey")
                }
                builder.header("anthropic-version", "2023-06-01")
            }
            ApiSpec.GOOGLE -> {
                if (apiKey.isNotBlank()) builder.header("x-goog-api-key", apiKey)
            }
        }
        return effectiveUa
    }

    private data class BufferedReply(val code: Int, val message: String, val isSuccessful: Boolean, val body: String)

    private suspend fun executeBuffered(request: Request): BufferedReply = withContext(Dispatchers.IO) {
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

    suspend fun fetchModels(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        rawEndpoint: Boolean = false
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = baseUrl.trim().trimEnd('/')
            val url = if (rawEndpoint) trimmed else when (spec) {
                ApiSpec.GOOGLE -> {
                    val base = if (trimmed.endsWith("/models")) trimmed else "$trimmed/models"
                    if (apiKey.isNotBlank() && !base.contains("key=")) {
                        if (base.contains("?")) "$base&key=$apiKey" else "$base?key=$apiKey"
                    } else base
                }
                ApiSpec.ANTHROPIC, ApiSpec.OPENAI -> {
                    if (trimmed.endsWith("/models")) trimmed else "$trimmed/models"
                }
            }

            val requestBuilder = Request.Builder().url(url).get()
            applyHeaders(requestBuilder, spec, apiKey)

            val response = executeBuffered(requestBuilder.build())
            val bodyStr = response.body
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $bodyStr"))
            val json = JSONObject(bodyStr)
            val dataArray = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
            val ids = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i)
                var id = item?.optString("id", item.optString("name", "")) ?: dataArray.optString(i)
                if (spec == ApiSpec.GOOGLE) id = id.removePrefix("models/")
                if (id.isNotBlank()) {
                    val trimmedId = id.trim()
                    ids.add(trimmedId)
                    val reasoningObj = item?.optJSONObject("reasoning")
                        ?: item?.optJSONObject("capabilities")?.optJSONObject("reasoning")
                    if (reasoningObj != null) {
                        val supported = reasoningObj.optBoolean("supported", true)
                        val controlStr = reasoningObj.optString("control")
                        val control = when (controlStr.lowercase()) {
                            "openai_effort", "effort" -> com.lucent.app.data.ReasoningControl.OPENAI_EFFORT
                            "anthropic_effort" -> com.lucent.app.data.ReasoningControl.ANTHROPIC_EFFORT
                            "anthropic_budget" -> com.lucent.app.data.ReasoningControl.ANTHROPIC_BUDGET
                            "gemini_thinking_level", "thinking_level" -> com.lucent.app.data.ReasoningControl.GEMINI_THINKING_LEVEL
                            "deepseek_effort" -> com.lucent.app.data.ReasoningControl.DEEPSEEK_EFFORT
                            "none" -> com.lucent.app.data.ReasoningControl.NONE
                            else -> if (spec == ApiSpec.GOOGLE) com.lucent.app.data.ReasoningControl.GEMINI_THINKING_LEVEL else com.lucent.app.data.ReasoningControl.OPENAI_EFFORT
                        }
                        com.lucent.app.data.ModelCapabilityRegistry.register(
                            com.lucent.app.data.ModelDefinition(
                                id = trimmedId,
                                reasoning = com.lucent.app.data.ReasoningCapability(
                                    supported = supported,
                                    control = control,
                                    isAdaptive = reasoningObj.optBoolean("adaptive", true)
                                )
                            )
                        )
                    } else {
                        com.lucent.app.data.ModelCapabilityRegistry.findOrRegister(trimmedId, spec.name)
                    }
                }
            }
            Result.success(ids.distinct())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Result.failure(e)
        }
    }

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
                            if (endpoint.contains("?")) "$endpoint&key=$apiKey" else "$endpoint?key=$apiKey"
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
                            if (base.contains("?")) "$base&key=$apiKey" else "$base?key=$apiKey"
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

            val response = executeBuffered(requestBuilder.build())
            val latency = System.currentTimeMillis() - start
            val bodyStr = response.body
            if (!response.isSuccessful) {
                val errMsg = try {
                    val json = JSONObject(bodyStr)
                    val errorObj = json.optJSONObject("error")
                    errorObj?.optString("message") ?: bodyStr.take(120)
                } catch (_: Exception) {
                    bodyStr.take(120)
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
                    rawResponseBody = e.stackTraceToString().take(1500),
                    errorMessage = e.message ?: e.javaClass.simpleName,
                    userAgentUsed = usedUa
                ).redacted(apiKey)
            )
        }
    }

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

            val call = streamClient.newCall(requestBuilder.build())
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
                            errorObj?.optString("message") ?: bodyStr.take(200)
                        } catch (_: JSONException) {
                            bodyStr.take(200)
                        }
                        return@withContext Result.failure(Exception("HTTP ${response.code}: ${errMsg.ifBlank { response.message }}"))
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
            Result.failure(e)
        }
    }
}

data class ChatMessagePayload(
    val role: String,
    val content: String
)

data class ChatCompletionResult(
    val content: String,
    val reasoningText: String? = null,
    val promptTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cachedTokensKnown: Boolean = false
)
