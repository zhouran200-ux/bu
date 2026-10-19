package com.lucent.app.network

import com.lucent.app.data.ResolvedReasoningConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal fun anthropicOutputCap(model: String): Int = when {
    model.contains("claude-3-opus") || model.contains("claude-3-sonnet") || model.contains("claude-3-haiku") -> 4096
    model.contains("claude-2") -> 4096
    model.contains("claude-3-5") || model.contains("claude-3.5") || model.contains("claude35") -> 8192
    model.contains("claude-opus-4-5") || model.contains("claude-sonnet-4-5") || model.contains("claude-haiku-4-5") -> 65536
    model.contains("claude-opus-4-6") || model.contains("claude-sonnet-4-6") || model.contains("claude-opus-4-7") ||
        model.contains("claude-opus-4-8") || model.contains("claude-opus-5") || model.contains("claude-sonnet-5") ||
        model.contains("claude-haiku-5-5") || model.contains("claude-fable-5") || model.contains("claude-mythos-5") -> 131072
    else -> Int.MAX_VALUE
}

internal object StreamingRequestBuilder {
    fun build(
        baseUrl: String, spec: ApiSpec, apiKey: String, model: String,
        messages: List<ChatMessagePayload>, systemInstruction: String,
        reasoningConfig: ResolvedReasoningConfig?, rawEndpoint: Boolean
    ): Request.Builder {
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
                val url = if (rawEndpoint || defaultUrl.endsWith("/chat/completions")) defaultUrl else "$defaultUrl/chat/completions"
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
                    applyCompatibleReasoning(this, reasoningConfig, cleanModel, url)
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
                    applyAnthropicReasoning(this, reasoningConfig, cleanModel)
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

                }
                requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
            }
            ApiSpec.GOOGLE -> {
                val defaultUrl = if (trimmed.isBlank()) "https://generativelanguage.googleapis.com/v1beta" else trimmed
                val base = if (defaultUrl.endsWith("/models")) defaultUrl.removeSuffix("/models") else defaultUrl
                val targetModel = cleanModel.removePrefix("models/")
                val endpoint = "$base/models/$targetModel:streamGenerateContent?alt=sse"
                val url = if (apiKey.isNotBlank() && !endpoint.contains("key=")) {
                    if (endpoint.contains("?")) "$endpoint&key=${queryValue(apiKey)}" else "$endpoint?key=${queryValue(apiKey)}"
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
                    googleThinkingConfig(reasoningConfig, compatible = false)?.let { thinking ->
                        put("generationConfig", JSONObject().put("thinkingConfig", thinking))
                    }
                }
                requestBuilder.url(url).post(payload.toString().toRequestBody(jsonMediaType))
            }
        }


        return requestBuilder
    }
}
