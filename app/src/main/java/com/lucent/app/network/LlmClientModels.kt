package com.lucent.app.network

import org.json.JSONArray
import org.json.JSONObject

data class TestConnectionResult(
    val success: Boolean,
    val httpCode: Int,
    val latencyMs: Long,
    val targetModel: String,
    val requestUrl: String,
    val rawResponseBody: String,
    val errorMessage: String? = null,
    val userAgentUsed: String? = null,
    val probeKind: ConnectionProbeKind = ConnectionProbeKind.Generation
) {
    fun prettyResponseBody(): String = DiagnosticRedaction.text(prettyResponseBodyRaw())

    private fun prettyResponseBodyRaw(): String {
        val trimmed = rawResponseBody.trim()
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> rawResponseBody
            }
        } catch (_: Exception) {
            rawResponseBody
        }
    }

    fun formatDisplayMessage(): String = DiagnosticRedaction.text(formatDisplayMessageRaw()).take(700)

    private fun formatDisplayMessageRaw(): String {
        if (probeKind != ConnectionProbeKind.Generation) {
            val summary = configurationProbeMessage(probeKind, success, httpCode, latencyMs)
            if (success) return summary
            val detail = parseErrorDetail(rawResponseBody, errorMessage)
            return if (detail.isNotBlank()) "$summary\n详细信息：$detail" else summary
        }
        if (success) {
            val targetLabel = if (targetModel.isNotBlank()) " [$targetModel]" else ""
            return "✓ 模型生成成功$targetLabel (${latencyMs}ms)"
        }

        if (httpCode in 200..299) {
            val detail = parseErrorDetail(rawResponseBody, errorMessage)
            val prefix = "接口已响应，但尚未确认可见文本生成（HTTP $httpCode）"
            return if (detail.isNotBlank() && detail != prefix) "$prefix\n详细信息：$detail" else prefix
        }
        return RequestFailureFormatter.http(
            code = httpCode,
            body = rawResponseBody,
            responseMessage = errorMessage.orEmpty(),
            requestUrl = requestUrl,
            model = targetModel
        )
    }

    private fun parseErrorDetail(bodyStr: String, fallback: String?): String {
        if (bodyStr.isBlank()) return fallback.orEmpty()
        try {
            val trimmed = bodyStr.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val errorObj = json.optJSONObject("error")
                val code = errorObj?.optString("code")?.takeIf { it.isNotBlank() }
                    ?: errorObj?.optString("type")?.takeIf { it.isNotBlank() }
                    ?: json.optString("code")?.takeIf { it.isNotBlank() }
                    ?: json.optString("error_code")?.takeIf { it.isNotBlank() }

                val message = errorObj?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json.optString("error_msg")?.takeIf { it.isNotBlank() }
                    ?: json.optString("error")?.takeIf { it.isNotBlank() }

                return when {
                    code != null && message != null -> "$code: $message"
                    message != null -> message
                    code != null -> code
                    else -> fallback ?: bodyStr.take(180)
                }
            }
        } catch (_: Exception) {
        }
        return fallback ?: bodyStr.take(180)
    }
}
