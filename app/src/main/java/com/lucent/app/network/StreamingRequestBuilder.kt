package com.lucent.app.network

import com.lucent.app.data.ResolvedReasoningConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

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
                    if (reasoningConfig != null && !reasoningConfig.isAuto) {
                        val wireEffort = reasoningConfig.openAiEffort ?: reasoningConfig.claudeEffort
                        if (reasoningConfig.openAiMode != null) {
                            put("reasoning", JSONObject().apply {
                                put("mode", reasoningConfig.openAiMode)
                                if (wireEffort != null) {
                                    put("effort", wireEffort)
                                }
                            })
                        } else if (wireEffort != null) {
                            put("reasoning_effort", wireEffort)
                        }
                        val budget = reasoningConfig.claudeBudgetTokens
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


        return requestBuilder
    }
}
