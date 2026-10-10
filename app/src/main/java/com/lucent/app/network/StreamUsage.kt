package com.lucent.app.network

import org.json.JSONObject

data class StreamUsage(
    val promptTokens: Int = 0,
    val cachedTokens: Int = 0
)

object StreamUsageParser {

    fun parse(spec: ApiSpec, chunk: JSONObject): StreamUsage? = when (spec) {
        ApiSpec.OPENAI -> chunk.optJSONObject("usage")?.let { usage ->
            val prompt = usage.optInt("prompt_tokens", 0)
            val cached = usage.optJSONObject("prompt_tokens_details")?.optInt("cached_tokens", 0)
                ?.takeIf { it > 0 }
                ?: usage.optInt("prompt_cache_hit_tokens", 0)
            StreamUsage(prompt, cached)
        }

        ApiSpec.ANTHROPIC -> when (chunk.optString("type")) {
            "message_start" -> chunk.optJSONObject("message")?.optJSONObject("usage")?.let { usage ->
                StreamUsage(
                    promptTokens = usage.optInt("input_tokens", 0) +
                        usage.optInt("cache_read_input_tokens", 0) +
                        usage.optInt("cache_creation_input_tokens", 0),
                    cachedTokens = usage.optInt("cache_read_input_tokens", 0)
                )
            }

            else -> null
        }

        ApiSpec.GOOGLE -> chunk.optJSONObject("usageMetadata")?.let { usage ->
            StreamUsage(
                promptTokens = usage.optInt("promptTokenCount", 0),
                cachedTokens = usage.optInt("cachedContentTokenCount", 0)
            )
        }
    }

    fun wantsStreamOptions(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore("/", "").lowercase()
        return host == "api.openai.com" ||
            host == "api.deepseek.com" ||
            host == "api.moonshot.cn" ||
            host == "api.moonshot.ai" ||
            host == "api.siliconflow.cn" ||
            host.endsWith(".openai.azure.com")
    }
}
