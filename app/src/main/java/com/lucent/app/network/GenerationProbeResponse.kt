package com.lucent.app.network

import org.json.JSONObject

internal fun generationProbeHasText(body: String, spec: ApiSpec): Boolean = try {
    val root = JSONObject(body)
    if (root.has("error") && !root.isNull("error")) false else when (spec) {
        ApiSpec.OPENAI -> {
            val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            (message?.opt("content") as? String)?.isNotBlank() == true
        }
        ApiSpec.ANTHROPIC -> {
            val content = root.optJSONArray("content")
            (0 until (content?.length() ?: 0)).any { index ->
                val block = content?.optJSONObject(index)
                block?.optString("type") == "text" && (block.opt("text") as? String)?.isNotBlank() == true
            }
        }
        ApiSpec.GOOGLE -> {
            val parts = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            (0 until (parts?.length() ?: 0)).any { index ->
                val part = parts?.optJSONObject(index)
                part?.optBoolean("thought", false) != true && (part?.opt("text") as? String)?.isNotBlank() == true
            }
        }
    }
} catch (_: Exception) {
    false
}
