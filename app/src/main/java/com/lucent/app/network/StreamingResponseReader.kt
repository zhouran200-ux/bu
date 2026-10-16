package com.lucent.app.network

import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
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
        var sawGoogleFinish = false
        val events = SseEventReader(source)


        while (true) {
            ensureStreamActive()
            val event = events.next(ensureStreamActive) ?: break
            ensureStreamActive()
            if (event.type == "error") throw IOException("服务端返回 SSE 错误事件")
            val data = event.data
            if (data.isNotBlank()) {
                if (data.trim() == "[DONE]") {
                    sawEndOfStream = true
                    break
                }
                val json = try {
                    val tokener = JSONTokener(data)
                    val value = tokener.nextValue()
                    if (value !is JSONObject || tokener.nextClean() != 0.toChar()) {
                        throw IOException("SSE 事件必须包含单个完整 JSON 对象")
                    }
                    value
                } catch (error: JSONException) {
                    throw IOException("服务端返回无效的 SSE JSON 事件", error)
                }
                if (json.has("error") || json.optString("type") == "error") {
                    throw IOException("服务端返回流式错误响应")
                }
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
                        val finishReason = candidates?.optJSONObject(0)?.optString("finishReason").orEmpty()
                        if (finishReason.isNotBlank() && finishReason != "FINISH_REASON_UNSPECIFIED") sawGoogleFinish = true
                        val contentObj = candidates?.optJSONObject(0)?.optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts") ?: JSONArray()
                        for (i in 0 until parts.length()) {
                            ensureStreamActive()
                            val part = parts.optJSONObject(i) ?: continue
                            val isThought = part.optBoolean("thought", false)
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

        if (!sawEndOfStream && !(spec == ApiSpec.GOOGLE && sawGoogleFinish)) {
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
