package com.lucent.app.network

import java.net.URLDecoder
import java.net.URLEncoder
import org.json.JSONObject
import org.json.JSONTokener

internal object DiagnosticRedaction {
    private val query = Regex("""([?&])([^=&#\s]+)=([^&#\s]*)""")
    private val userInfo = Regex("""(?i)(https?://)[^\s/@]+@""")
    private val jsonString = Regex(""""(?:\\.|[^"\\])*+"""")
    private val sensitive = setOf("key", "apikey", "api_key", "api-key", "access_token", "token", "auth", "authorization", "password", "secret")

    fun failure(error: Exception, apiKey: String): Exception =
        java.io.IOException(text(error.message ?: error.javaClass.simpleName, apiKey))

    fun text(value: String, apiKey: String = ""): String {
        val encoded = apiKey.takeIf { it.isNotBlank() }?.let { URLEncoder.encode(it, "UTF-8") }
        var safe = if (encoded == null) value else jsonString.replace(value) { match ->
            if (!match.value.contains(92.toChar())) return@replace match.value
            val decoded = runCatching { JSONTokener(match.value).nextValue() as? String }.getOrNull()
                ?: return@replace match.value
            val redacted = decoded.replace(apiKey, "[REDACTED]").replace(encoded, "[REDACTED]")
            if (redacted == decoded) match.value else JSONObject.quote(redacted)
        }
        safe = userInfo.replace(safe) { it.groupValues[1] + "[REDACTED]@" }
        safe = query.replace(safe) { match ->
            val name = runCatching { URLDecoder.decode(match.groupValues[2], "UTF-8") }.getOrDefault(match.groupValues[2])
            if (name.lowercase() in sensitive) match.groupValues[1] + match.groupValues[2] + "=[REDACTED]" else match.value
        }
        if (encoded != null) {
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
