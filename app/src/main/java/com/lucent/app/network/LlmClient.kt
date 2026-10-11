package com.lucent.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TestConnectionResult(
    val success: Boolean,
    val httpCode: Int,
    val latencyMs: Long,
    val targetModel: String,
    val requestUrl: String,
    val rawResponseBody: String,
    val errorMessage: String? = null,
    val userAgentUsed: String? = null
) {
    fun prettyResponseBody(): String {
        val trimmed = rawResponseBody.trim()
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> rawResponseBody
            }
        } catch (_: Exception) {
            rawResponseBody
        }
    }

    fun formatDisplayMessage(): String {
        if (success) {
            val targetLabel = if (targetModel.isNotBlank()) " [$targetModel]" else ""
            return "✓ 连接成功$targetLabel (${latencyMs}ms)"
        }

        val prefix = when (httpCode) {
            400 -> "[请求参数错误/模型参数不兼容 HTTP 400]"
            401 -> "[API Key无效/未授权 HTTP 401]"
            403 -> "[权限不足/访问受限 HTTP 403]"
            404 -> "[模型未找到/接口地址不存在 HTTP 404]"
            429 -> "[额度不足/请求频率超限 HTTP 429]"
            in 500..599 -> "[服务商服务端异常 HTTP $httpCode]"
            0 -> "[网络连接失败/无法访问服务器]"
            else -> "[请求失败 HTTP $httpCode]"
        }

        val detail = parseErrorDetail(rawResponseBody, errorMessage)
        return if (detail.isNotBlank()) {
            "$prefix$detail"
        } else {
            "$prefix${errorMessage ?: "未知错误"}"
        }
    }

    private fun parseErrorDetail(bodyStr: String, fallback: String?): String {
        if (bodyStr.isBlank()) return fallback.orEmpty()
        try {
            val trimmed = bodyStr.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val errorObj = json.optJSONObject("error")
                val code = errorObj?.optString("code")?.takeIf { it.isNotBlank() }
                    ?: errorObj?.optString("type")?.takeIf { it.isNotBlank() }
                    ?: json.optString("code")?.takeIf { it.isNotBlank() }
                    ?: json.optString("error_code")?.takeIf { it.isNotBlank() }

                val message = errorObj?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json.optString("error_msg")?.takeIf { it.isNotBlank() }
                    ?: json.optString("error")?.takeIf { it.isNotBlank() }

                return when {
                    code != null && message != null -> "$code: $message"
                    message != null -> message
                    code != null -> code
                    else -> fallback ?: bodyStr.take(180)
                }
            }
        } catch (_: Exception) {
        }
        return fallback ?: bodyStr.take(180)
    }

    fun toFullReport(): String = buildString {
        appendLine("=== 测试连接报告 ===")
        appendLine("目标模型: ${targetModel.ifBlank { "(未指定)" }}")
        appendLine("请求地址: $requestUrl")
        if (!userAgentUsed.isNullOrBlank()) {
            appendLine("客户端标识 (UA): $userAgentUsed")
        }
        if (httpCode > 0) appendLine("HTTP 状态码: $httpCode")
        appendLine("响应延迟: ${latencyMs}ms")
        if (!errorMessage.isNullOrBlank()) {
            appendLine("错误概要: $errorMessage")
        }
        appendLine("--- 原始响应报文 (Raw Response Body) ---")
        appendLine(rawResponseBody.ifBlank { "(空响应)" })
    }
}

class StreamCallHandle {
    @Volatile
    private var call: okhttp3.Call? = null

    fun attach(value: okhttp3.Call) {
        call = value
    }

    fun cancel() {
        call?.cancel()
        call = null
    }
}

object LlmClient {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val streamClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private fun applyHeaders(
        builder: Request.Builder,
        spec: ApiSpec,
        apiKey: String,
        customUserAgent: String = ""
    ): String {
        val effectiveUa = customUserAgent.trim().ifBlank {
            com.lucent.app.data.SettingsCache.customUserAgent.trim()
        }
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

    suspend fun fetchModels(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        customUserAgent: String = ""
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = baseUrl.trim().trimEnd('/')
            val url = when (spec) {
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
            applyHeaders(requestBuilder, spec, apiKey, customUserAgent)

            val response = client.newCall(requestBuilder.build()).execute()
            val bodyStr = response.body?.string() ?: ""
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
                                provider = spec.name,
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String = "",
        customUserAgent: String = ""
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
                        url = if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
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
                url = when (spec) {
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
            usedUa = applyHeaders(requestBuilder, spec, apiKey, customUserAgent).ifBlank { null }

            val response = client.newCall(requestBuilder.build()).execute()
            val latency = System.currentTimeMillis() - start
            val bodyStr = response.body?.string().orEmpty()
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
                return@withContext Result.success(failureResult)
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
                )
            )
        } catch (e: Exception) {
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
                )
            )
        }
    }

    suspend fun chat(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String,
        messages: List<ChatMessagePayload>,
        systemInstruction: String = "",
        customUserAgent: String = "",
        reasoningConfig: com.lucent.app.data.ResolvedReasoningConfig? = null
    ): Result<ChatCompletionResult> = withContext(Dispatchers.IO) {
        try {
            val trimmed = baseUrl.trim().trimEnd('/')
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBuilder = Request.Builder()
            val cleanModel = model.trim().ifBlank {
                when (spec) {
                    ApiSpec.GOOGLE -> "gemini-3.5-flash"
                    ApiSpec.ANTHROPIC -> "claude-3-5-sonnet"
                    ApiSpec.OPENAI -> "gpt-4o"
                }
            }

            when (spec) {
                ApiSpec.OPENAI -> {
                    val defaultUrl = if (trimmed.isBlank()) "https://api.openai.com/v1" else trimmed
                    val url = if (defaultUrl.endsWith("/chat/completions")) defaultUrl else "$defaultUrl/chat/completions"
                    val payload = JSONObject().apply {
                        put("model", cleanModel)
                        val messagesArray = JSONArray()
                        if (systemInstruction.isNotBlank()) {
                            messagesArray.put(JSONObject().apply {
                                put("role", "system")
                                put("content", systemInstruction)
                            })
                        }
                        messages.forEach { m ->
                            messagesArray.put(JSONObject().apply {
                                put("role", m.role)
                                put("content", m.content)
                            })
                        }
                        put("messages", messagesArray)
                        if (reasoningConfig != null && !reasoningConfig.isAuto) {
                            if (reasoningConfig.openAiMode != null) {
                                put("reasoning", JSONObject().apply {
                                    put("mode", reasoningConfig.openAiMode)
                                    if (reasoningConfig.openAiEffort != null) {
                                        put("effort", reasoningConfig.openAiEffort)
                                    }
                                })
                            } else if (reasoningConfig.openAiEffort != null) {
                                put("reasoning_effort", reasoningConfig.openAiEffort)
                            }
                        }
                    }
                    requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                }
                ApiSpec.ANTHROPIC -> {
                    val defaultUrl = if (trimmed.isBlank()) "https://api.anthropic.com/v1" else trimmed
                    val url = if (defaultUrl.endsWith("/messages")) defaultUrl
                    else if (defaultUrl.endsWith("/v1")) "$defaultUrl/messages"
                    else "$defaultUrl/v1/messages"
                    val payload = JSONObject().apply {
                        put("model", cleanModel)
                        put("max_tokens", 8192)
                        if (systemInstruction.isNotBlank()) {
                            put("system", systemInstruction)
                        }
                        val messagesArray = JSONArray()
                        messages.forEach { m ->
                            messagesArray.put(JSONObject().apply {
                                put("role", if (m.role == "assistant") "assistant" else "user")
                                put("content", m.content)
                            })
                        }
                        put("messages", messagesArray)
                        if (reasoningConfig != null && !reasoningConfig.isAuto) {
                            if (reasoningConfig.claudeEffort != null) {
                                put("output_config", JSONObject().apply {
                                    put("effort", reasoningConfig.claudeEffort)
                                })
                            } else if (reasoningConfig.claudeBudgetTokens != null) {
                                put("thinking", JSONObject().apply {
                                    put("type", "enabled")
                                    put("budget_tokens", reasoningConfig.claudeBudgetTokens)
                                })
                            }
                        }
                    }
                    requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                }
                ApiSpec.GOOGLE -> {
                    val defaultUrl = if (trimmed.isBlank()) "https://generativelanguage.googleapis.com/v1beta" else trimmed
                    val base = if (defaultUrl.endsWith("/models")) defaultUrl.removeSuffix("/models") else defaultUrl
                    val targetModel = cleanModel.removePrefix("models/")
                    val endpoint = "$base/models/$targetModel:generateContent"
                    val url = if (apiKey.isNotBlank() && !endpoint.contains("key=")) {
                        if (endpoint.contains("?")) "$endpoint&key=$apiKey" else "$endpoint?key=$apiKey"
                    } else endpoint

                    val payload = JSONObject().apply {
                        if (systemInstruction.isNotBlank()) {
                            put("system_instruction", JSONObject().apply {
                                put("parts", JSONArray().put(JSONObject().apply {
                                    put("text", systemInstruction)
                                }))
                            })
                        }
                        val contentsArray = JSONArray()
                        messages.forEach { m ->
                            contentsArray.put(JSONObject().apply {
                                put("role", if (m.role == "assistant") "model" else "user")
                                put("parts", JSONArray().put(JSONObject().apply {
                                    put("text", m.content)
                                }))
                            })
                        }
                        put("contents", contentsArray)
                        val genConfig = JSONObject()
                        if (reasoningConfig != null && !reasoningConfig.isAuto) {
                            val thinkingConfig = JSONObject()
                            if (reasoningConfig.geminiThinkingLevel != null) {
                                thinkingConfig.put("thinkingLevel", reasoningConfig.geminiThinkingLevel)
                            }
                            if (reasoningConfig.geminiThinkingBudget != null) {
                                thinkingConfig.put("thinkingBudget", reasoningConfig.geminiThinkingBudget)
                            }
                            if (thinkingConfig.length() > 0) {
                                genConfig.put("thinkingConfig", thinkingConfig)
                            }
                        }
                        if (genConfig.length() > 0) {
                            put("generationConfig", genConfig)
                        }
                    }
                    requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                }
            }

            applyHeaders(requestBuilder, spec, apiKey, customUserAgent)
            val response = client.newCall(requestBuilder.build()).execute()
            val bodyStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errMsg = try {
                    val json = JSONObject(bodyStr)
                    val errorObj = json.optJSONObject("error")
                    errorObj?.optString("message") ?: bodyStr.take(200)
                } catch (_: Exception) {
                    bodyStr.take(200)
                }
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${errMsg.ifBlank { response.message }}"))
            }

            val json = JSONObject(bodyStr)
            val parsedResult = when (spec) {
                ApiSpec.OPENAI -> {
                    val choices = json.optJSONArray("choices")
                    val firstChoice = choices?.optJSONObject(0)
                    val msgObj = firstChoice?.optJSONObject("message")
                    val content = msgObj?.optString("content", "").orEmpty()
                    val reasoning = msgObj?.optString("reasoning_content", "")
                        ?.ifBlank { msgObj.optString("reasoning", "") }
                        ?.ifBlank { null }
                    ChatCompletionResult(content = content, reasoningText = reasoning)
                }
                ApiSpec.ANTHROPIC -> {
                    val contentArray = json.optJSONArray("content") ?: JSONArray()
                    val textBuilder = StringBuilder()
                    var reasoning: String? = null
                    for (i in 0 until contentArray.length()) {
                        val part = contentArray.optJSONObject(i) ?: continue
                        when (part.optString("type")) {
                            "text" -> textBuilder.append(part.optString("text"))
                            "thinking" -> reasoning = part.optString("thinking")
                        }
                    }
                    ChatCompletionResult(content = textBuilder.toString(), reasoningText = reasoning)
                }
                ApiSpec.GOOGLE -> {
                    val candidates = json.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val contentObj = firstCandidate?.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts") ?: JSONArray()
                    val textBuilder = StringBuilder()
                    var reasoning: String? = null
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        val isThought = part.optBoolean("thought", false) || part.has("thought")
                        val text = part.optString("text")
                        if (isThought) {
                            reasoning = text
                        } else {
                            textBuilder.append(text)
                        }
                    }
                    ChatCompletionResult(content = textBuilder.toString(), reasoningText = reasoning)
                }
            }

            Result.success(parsedResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun chatStream(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String,
        messages: List<ChatMessagePayload>,
        systemInstruction: String = "",
        customUserAgent: String = "",
        reasoningConfig: com.lucent.app.data.ResolvedReasoningConfig? = null,
        handle: StreamCallHandle? = null,
        onChunk: suspend (deltaContent: String, deltaReasoning: String?) -> Unit
    ): Result<ChatCompletionResult> = withContext(Dispatchers.IO) {
        try {
            val trimmed = baseUrl.trim().trimEnd('/')
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBuilder = Request.Builder()
            val cleanModel = model.trim().ifBlank {
                when (spec) {
                    ApiSpec.GOOGLE -> "gemini-3.5-flash"
                    ApiSpec.ANTHROPIC -> "claude-3-5-sonnet"
                    ApiSpec.OPENAI -> "gpt-4o"
                }
            }

            when (spec) {
                ApiSpec.OPENAI -> {
                    val defaultUrl = if (trimmed.isBlank()) "https://api.openai.com/v1" else trimmed
                    val url = if (defaultUrl.endsWith("/chat/completions")) defaultUrl else "$defaultUrl/chat/completions"
                    val payload = JSONObject().apply {
                        put("model", cleanModel)
                        put("stream", true)
                        if (StreamUsageParser.wantsStreamOptions(url)) {
                            put("stream_options", JSONObject().put("include_usage", true))
                        }
                        val messagesArray = JSONArray()
                        if (systemInstruction.isNotBlank()) {
                            messagesArray.put(JSONObject().apply {
                                put("role", "system")
                                put("content", systemInstruction)
                            })
                        }
                        messages.forEach { m ->
                            messagesArray.put(JSONObject().apply {
                                put("role", m.role)
                                put("content", m.content)
                            })
                        }
                        put("messages", messagesArray)
                        if (reasoningConfig != null && !reasoningConfig.isAuto) {
                            if (reasoningConfig.openAiMode != null) {
                                put("reasoning", JSONObject().apply {
                                    put("mode", reasoningConfig.openAiMode)
                                    if (reasoningConfig.openAiEffort != null) {
                                        put("effort", reasoningConfig.openAiEffort)
                                    }
                                })
                            } else if (reasoningConfig.openAiEffort != null) {
                                put("reasoning_effort", reasoningConfig.openAiEffort)
                            }
                            val budget = reasoningConfig.claudeBudgetTokens ?: reasoningConfig.geminiThinkingBudget
                            if (budget != null && budget > 0) {
                                put("max_completion_tokens", budget)
                            }
                        }
                    }
                    requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                }
                ApiSpec.ANTHROPIC -> {
                    val defaultUrl = if (trimmed.isBlank()) "https://api.anthropic.com/v1" else trimmed
                    val url = if (defaultUrl.endsWith("/messages")) defaultUrl
                    else if (defaultUrl.endsWith("/v1")) "$defaultUrl/messages"
                    else "$defaultUrl/v1/messages"
                    val payload = JSONObject().apply {
                        put("model", cleanModel)
                        put("stream", true)
                        put("max_tokens", 8192)
                        if (systemInstruction.isNotBlank()) {
                            put("system", systemInstruction)
                        }
                        val messagesArray = JSONArray()
                        messages.forEach { m ->
                            messagesArray.put(JSONObject().apply {
                                put("role", if (m.role == "assistant") "assistant" else "user")
                                put("content", m.content)
                            })
                        }
                        put("messages", messagesArray)
                        if (reasoningConfig != null && !reasoningConfig.isAuto) {
                            if (reasoningConfig.claudeEffort != null) {
                                put("output_config", JSONObject().apply {
                                    put("effort", reasoningConfig.claudeEffort)
                                })
                            } else if (reasoningConfig.claudeBudgetTokens != null) {
                                put("thinking", JSONObject().apply {
                                    put("type", "enabled")
                                    put("budget_tokens", reasoningConfig.claudeBudgetTokens)
                                })
                            }
                        }
                    }
                    requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                }
                ApiSpec.GOOGLE -> {
                    val defaultUrl = if (trimmed.isBlank()) "https://generativelanguage.googleapis.com/v1beta" else trimmed
                    val base = if (defaultUrl.endsWith("/models")) defaultUrl.removeSuffix("/models") else defaultUrl
                    val targetModel = cleanModel.removePrefix("models/")
                    val endpoint = "$base/models/$targetModel:streamGenerateContent?alt=sse"
                    val url = if (apiKey.isNotBlank() && !endpoint.contains("key=")) {
                        if (endpoint.contains("?")) "$endpoint&key=$apiKey" else "$endpoint?key=$apiKey"
                    } else endpoint

                    val payload = JSONObject().apply {
                        if (systemInstruction.isNotBlank()) {
                            put("system_instruction", JSONObject().apply {
                                put("parts", JSONArray().put(JSONObject().apply {
                                    put("text", systemInstruction)
                                }))
                            })
                        }
                        val contentsArray = JSONArray()
                        messages.forEach { m ->
                            contentsArray.put(JSONObject().apply {
                                put("role", if (m.role == "assistant") "model" else "user")
                                put("parts", JSONArray().put(JSONObject().apply {
                                    put("text", m.content)
                                }))
                            })
                        }
                        put("contents", contentsArray)
                        val genConfig = JSONObject()
                        if (reasoningConfig != null && !reasoningConfig.isAuto) {
                            val thinkingConfig = JSONObject()
                            if (reasoningConfig.geminiThinkingLevel != null) {
                                thinkingConfig.put("thinkingLevel", reasoningConfig.geminiThinkingLevel)
                            }
                            if (reasoningConfig.geminiThinkingBudget != null) {
                                thinkingConfig.put("thinkingBudget", reasoningConfig.geminiThinkingBudget)
                            }
                            if (thinkingConfig.length() > 0) {
                                genConfig.put("thinkingConfig", thinkingConfig)
                            }
                        }
                        if (genConfig.length() > 0) {
                            put("generationConfig", genConfig)
                        }
                    }
                    requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
                }
            }

            requestBuilder.header("Accept", "text/event-stream")
            applyHeaders(requestBuilder, spec, apiKey, customUserAgent)

            val call = streamClient.newCall(requestBuilder.build())
            handle?.attach(call)
            val response = call.execute()
            if (!response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                val errMsg = try {
                    val json = JSONObject(bodyStr)
                    val errorObj = json.optJSONObject("error")
                    errorObj?.optString("message") ?: bodyStr.take(200)
                } catch (_: Exception) {
                    bodyStr.take(200)
                }
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${errMsg.ifBlank { response.message }}"))
            }

            val fullContent = StringBuilder()
            val fullReasoning = StringBuilder()
            var usage = StreamUsage()
            val source = response.body?.source() ?: return@withContext Result.failure(Exception("响应数据为空"))

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("data:")) {
                    val data = trimmedLine.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isBlank()) continue

                    try {
                        when (spec) {
                            ApiSpec.OPENAI -> {
                                val json = JSONObject(data)
                                StreamUsageParser.parse(spec, json)?.let { usage = it }
                                val choices = json.optJSONArray("choices")
                                val delta = choices?.optJSONObject(0)?.optJSONObject("delta")
                                if (delta != null) {
                                    val content = delta.optString("content", "")
                                    val reasoning = delta.optString("reasoning_content", "")
                                        .ifBlank { delta.optString("reasoning", "") }
                                        .ifBlank { null }
                                    if (content.isNotEmpty() || reasoning != null) {
                                        if (content.isNotEmpty()) fullContent.append(content)
                                        if (reasoning != null) fullReasoning.append(reasoning)
                                        onChunk(content, reasoning)
                                    }
                                }
                            }
                            ApiSpec.GOOGLE -> {
                                val json = JSONObject(data)
                                StreamUsageParser.parse(spec, json)?.let { usage = it }
                                val candidates = json.optJSONArray("candidates")
                                val contentObj = candidates?.optJSONObject(0)?.optJSONObject("content")
                                val parts = contentObj?.optJSONArray("parts") ?: JSONArray()
                                for (i in 0 until parts.length()) {
                                    val part = parts.optJSONObject(i) ?: continue
                                    val isThought = part.optBoolean("thought", false) || part.has("thought")
                                    val text = part.optString("text", "")
                                    if (text.isNotEmpty()) {
                                        if (isThought) {
                                            fullReasoning.append(text)
                                            onChunk("", text)
                                        } else {
                                            fullContent.append(text)
                                            onChunk(text, null)
                                        }
                                    }
                                }
                            }
                            ApiSpec.ANTHROPIC -> {
                                val json = JSONObject(data)
                                StreamUsageParser.parse(spec, json)?.let { usage = it }
                                val delta = json.optJSONObject("delta")
                                if (delta != null) {
                                    when (delta.optString("type")) {
                                        "text_delta" -> {
                                            val text = delta.optString("text", "")
                                            if (text.isNotEmpty()) {
                                                fullContent.append(text)
                                                onChunk(text, null)
                                            }
                                        }
                                        "thinking_delta" -> {
                                            val thinking = delta.optString("thinking", "")
                                            if (thinking.isNotEmpty()) {
                                                fullReasoning.append(thinking)
                                                onChunk("", thinking)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            Result.success(
                ChatCompletionResult(
                    content = fullContent.toString(),
                    reasoningText = fullReasoning.toString().ifBlank { null },
                    promptTokens = usage.promptTokens,
                    cachedTokens = usage.cachedTokens
                )
            )
        } catch (e: Exception) {
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
    val cachedTokens: Int = 0
)
