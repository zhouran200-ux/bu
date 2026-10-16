package com.lucent.app.network

import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

internal object StreamingResponseReader {
    suspend fun read(
        source: BufferedSource, spec: ApiSpec, ensureStreamActive: () -> Unit,
        onChunk: suspend (String, String?) -> Unit
    ): ChatCompletionResult {
        val fullContent = StringBuilder()
        val fullReasoning = StringBuilder()
        var usage = StreamUsage()
        var sawEndOfStream = false
        var pendingData: String? = null


        while (true) {
            ensureStreamActive()
            val line = source.readUtf8Line() ?: break
            ensureStreamActive()
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) {
                pendingData = null
                continue
            }
            if (trimmedLine.startsWith("data:")) {
                val lineData = trimmedLine.removePrefix("data:").trim()
                if (lineData == "[DONE]") {
                    sawEndOfStream = true
                    break
                }
                if (lineData.isBlank()) continue
                val data = pendingData?.let { it + lineData } ?: lineData
                val json = try {
                    JSONObject(data)
                } catch (_: JSONException) {
                    pendingData = data
                    continue
                }
                pendingData = null
                if (spec == ApiSpec.ANTHROPIC && json.optString("type") == "message_stop") {
                    sawEndOfStream = true
                    break
                }
                when (spec) {
                    ApiSpec.OPENAI -> {
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
                        StreamUsageParser.parse(spec, json)?.let { usage = it }
                        val candidates = json.optJSONArray("candidates")
                        val contentObj = candidates?.optJSONObject(0)?.optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts") ?: JSONArray()
                        for (i in 0 until parts.length()) {
                            ensureStreamActive()
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
            }
        }

        if (!sawEndOfStream && spec != ApiSpec.GOOGLE) {
            throw IOException("SSE 流在收到结束信号前断开")
        }
        ensureStreamActive()
        return ChatCompletionResult(
                content = fullContent.toString(),
                reasoningText = fullReasoning.toString().ifBlank { null },
                promptTokens = usage.promptTokens,
                cachedTokens = usage.cachedTokens,
                cachedTokensKnown = usage.cachedTokensKnown
        )
    }
}
