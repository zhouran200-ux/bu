package com.lucent.app.network

import org.json.JSONObject

internal fun applyOpenAiProbeCap(payload: JSONObject, model: String) {
    val name = model.trim().substringAfterLast('/').lowercase()
    val reasoningModel = Regex("^(o[134](?:-|$)|gpt-5(?:[.\\-]|$))").containsMatchIn(name)
    if (reasoningModel) payload.put("max_completion_tokens", 1024)
    else payload.put("max_tokens", 1)
}
