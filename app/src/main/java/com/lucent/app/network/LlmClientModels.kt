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

    fun formatDisplayMessage(): String = DiagnosticRedaction.text(formatDisplayMessageRaw())

    private fun formatDisplayMessageRaw(): String {
        if (probeKind != ConnectionProbeKind.Generation) {
            val summary = configurationProbeMessage(probeKind, success, httpCode, latencyMs)
            if (success) return summary
            val detail = parseErrorDetail(rawResponseBody, errorMessage)
            return if (detail.isNotBlank()) "$summary\n详细信息：$detail" else summary
        }
        if (success) {
            val targetLabel = if (targetModel.isNotBlank()) " [$targetModel]" else ""
            return "✓ 连接成功$targetLabel (${latencyMs}ms)"
        }

        val prefix = when (httpCode) {
            400 -> "请求参数不被接受，请检查模型名称与所选协议是否匹配（HTTP 400）"
            401 -> "API Key 无效或未授权，请核对密钥是否正确、是否已开通该服务（HTTP 401）"
            403 -> "该账号或密钥没有访问权限，请确认套餐、地区限制或模型授权（HTTP 403）"
            404 -> "接口地址或模型不存在，请检查基础 URL 结尾与模型名称（HTTP 404）"
            408 -> "服务商响应超时，请稍后重试或更换网络（HTTP 408）"
            429 -> "额度不足或请求太频繁，请稍后重试或检查账户余额（HTTP 429）"
            in 500..599 -> "服务商或网关暂时异常，通常是服务端问题，请稍后重试并查看下方详情（HTTP $httpCode）"
            0 -> when {
                errorMessage?.contains("timeout", true) == true || errorMessage?.contains("timed out", true) == true ->
                    "连接超时：服务器长时间未响应，请检查网络或更换接口地址"
                errorMessage?.contains("unable to resolve", true) == true || errorMessage?.contains("UnknownHost", true) == true ->
                    "无法解析地址：请检查基础 URL 拼写与设备网络"
                errorMessage?.contains("connection refused", true) == true ->
                    "连接被拒绝：端口或协议可能不对，请确认基础 URL"
                else -> "无法访问服务器：请检查设备网络与基础 URL 是否可达"
            }
            else -> "请求失败（HTTP $httpCode）"
        }

        val detail = parseErrorDetail(rawResponseBody, errorMessage)
        return if (detail.isNotBlank() && detail != prefix) {
            "$prefix\n详细信息：$detail"
        } else {
            prefix
        }
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
