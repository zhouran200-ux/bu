package com.lucent.app.network

import org.json.JSONArray
import org.json.JSONObject

internal const val CONNECTION_PROBE_PROMPT = "hello world"

internal fun applyOpenAiProbeCap(payload: JSONObject, model: String) {
    val name = model.trim().substringAfterLast('/').lowercase()
    val reasoningModel = Regex("^(o[134](?:-|$)|gpt-5(?:[.\\-]|$))").containsMatchIn(name)
    if (reasoningModel) payload.put("max_completion_tokens", 1024)
    else payload.put("max_tokens", 64)
}

internal fun connectionProbePayload(spec: ApiSpec, model: String): JSONObject = JSONObject().apply {
    when (spec) {
        ApiSpec.OPENAI -> {
            put("model", model.trim())
            put("messages", JSONArray().put(userMessage()))
            applyOpenAiProbeCap(this, model)
        }
        ApiSpec.ANTHROPIC -> {
            put("model", model.trim())
            put("max_tokens", 64)
            put("messages", JSONArray().put(userMessage()))
        }
        ApiSpec.GOOGLE -> {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", CONNECTION_PROBE_PROMPT))
                    )
                )
            )
        }
    }
}

private fun userMessage(): JSONObject = JSONObject().apply {
    put("role", "user")
    put("content", CONNECTION_PROBE_PROMPT)
}
