package com.lucent.app.network

import java.net.URLDecoder
import java.net.URLEncoder

internal object DiagnosticRedaction {
    private val query = Regex("""([?&])([^=&#\s]+)=([^&#\s]*)""")
    private val userInfo = Regex("""(?i)(https?://)[^\s/@]+@""")
    private val sensitive = setOf("key", "apikey", "api_key", "api-key", "access_token", "token", "auth", "authorization", "password", "secret")

    fun text(value: String, apiKey: String = ""): String {
        var safe = userInfo.replace(value) { it.groupValues[1] + "[REDACTED]@" }
        safe = query.replace(safe) { match ->
            val name = runCatching { URLDecoder.decode(match.groupValues[2], "UTF-8") }.getOrDefault(match.groupValues[2])
            if (name.lowercase() in sensitive) match.groupValues[1] + match.groupValues[2] + "=[REDACTED]" else match.value
        }
        if (apiKey.isNotBlank()) {
            val encoded = URLEncoder.encode(apiKey, "UTF-8")
            safe = safe.replace(apiKey, "[REDACTED]").replace(encoded, "[REDACTED]")
        }
        return safe
    }
}

internal fun TestConnectionResult.redacted(apiKey: String): TestConnectionResult = copy(
    targetModel = DiagnosticRedaction.text(targetModel, apiKey),
    requestUrl = DiagnosticRedaction.text(requestUrl, apiKey),
    rawResponseBody = DiagnosticRedaction.text(rawResponseBody, apiKey),
    errorMessage = errorMessage?.let { DiagnosticRedaction.text(it, apiKey) },
    userAgentUsed = userAgentUsed?.let { DiagnosticRedaction.text(it, apiKey) }
)
