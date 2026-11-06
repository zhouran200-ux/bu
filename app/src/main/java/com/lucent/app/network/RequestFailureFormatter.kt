package com.lucent.app.network

import java.io.EOFException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object RequestFailureFormatter {
    private const val MAX_DETAIL = 360
    private const val MAX_URL = 220
    private const val MAX_MODEL = 120

    fun http(
        code: Int,
        body: String,
        apiKey: String = "",
        responseMessage: String = "",
        requestUrl: String = "",
        model: String = ""
    ): String {
        if (code == 0) return networkText(responseMessage.ifBlank { body }, requestUrl, model, apiKey)
        val status = statusFor(code)
        val detail = providerDetail(body, apiKey)
            .ifBlank { DiagnosticRedaction.text(responseMessage.trim(), apiKey).take(MAX_DETAIL) }
        return buildString {
            append("HTTP ").append(code).append("：").append(status.title)
            if (detail.isNotBlank()) append("\n服务商返回：").append(detail)
            append("\n建议：").append(status.hint)
            appendContext(this, requestUrl, model, apiKey)
        }
    }

    fun network(
        error: Throwable,
        apiKey: String = "",
        requestUrl: String = "",
        model: String = ""
    ): String {
        val relevant = generateSequence(error) { it.cause }
            .firstOrNull { !it.message.isNullOrBlank() }
            ?: error
        val raw = DiagnosticRedaction.text(relevant.message.orEmpty().trim(), apiKey)
        val detail = when {
            raw.isBlank() -> relevant.javaClass.simpleName
            raw.contains(relevant.javaClass.simpleName) -> raw
            else -> relevant.javaClass.simpleName + ": " + raw
        }
        return networkText(detail, requestUrl, model, apiKey, error)
    }

    fun networkText(
        detail: String,
        requestUrl: String = "",
        model: String = "",
        apiKey: String = "",
        error: Throwable? = null
    ): String {
        val safeDetail = DiagnosticRedaction.text(detail.trim(), apiKey).take(MAX_DETAIL)
        val status = networkStatus(safeDetail, error)
        return buildString {
            append(status.title)
            if (safeDetail.isNotBlank() && !safeDetail.startsWith(status.title)) {
                append("\n错误详情：").append(safeDetail)
            }
            append("\n建议：").append(status.hint)
            appendContext(this, requestUrl, model, apiKey)
        }
    }

    fun protocol(
        problem: String,
        requestUrl: String = "",
        model: String = "",
        apiKey: String = ""
    ): String = buildString {
        append("响应格式异常：").append(DiagnosticRedaction.text(problem.trim(), apiKey).take(MAX_DETAIL))
        append("\n建议：确认接口地址指向 API 端点、所选协议与模型匹配，并查看服务商原始响应。")
        appendContext(this, requestUrl, model, apiKey)
    }

    private data class Explanation(val title: String, val hint: String)

    private fun statusFor(code: Int): Explanation = when (code) {
        400 -> Explanation(
            "请求参数不被接受",
            "核对模型名称、所选协议与请求格式；该状态表示服务商拒绝了当前参数。"
        )
        401 -> Explanation(
            "API Key 无效或未授权",
            "在 API 设置中核对密钥是否完整、是否属于当前服务商，并确认所选协议与地址。"
        )
        402 -> Explanation(
            "账户余额不足或尚未开通计费",
            "检查服务商账户余额、套餐和计费状态。"
        )
        403 -> Explanation(
            "账号或密钥没有访问权限",
            "检查模型授权、套餐、地区限制，以及该密钥是否允许访问此端点。"
        )
        404 -> Explanation(
            "接口地址或模型不存在",
            "检查基础 URL 是否多写或漏写 /v1，以及模型名称是否属于当前服务商。"
        )
        405 -> Explanation(
            "接口不接受当前请求方法",
            "检查协议类型和基础 URL；自定义地址应填写完整的最终端点。"
        )
        408 -> Explanation(
            "服务商请求超时",
            "稍后重试；如果持续出现，检查网关或代理是否稳定。"
        )
        409 -> Explanation(
            "请求与账户当前状态冲突",
            "查看服务商返回内容，确认账户、模型或并发限制。"
        )
        413 -> Explanation(
            "请求内容过大",
            "减少历史轮数、来源文档长度或单次请求内容后重试。"
        )
        415 -> Explanation(
            "请求内容类型不被接受",
            "确认所选协议与接口地址匹配；自定义端点可能需要不同的 Content-Type。"
        )
        422 -> Explanation(
            "请求参数校验失败",
            "根据服务商返回内容修正模型、消息或参数。"
        )
        429 -> Explanation(
            "请求频率过高或额度不足",
            "降低请求频率并检查账户余额或速率限制，稍后重试。"
        )
        in 300..399 -> Explanation(
            "接口返回重定向",
            "应用不会依循自动重定向；请直接把基础 URL 改为最终可访问地址。"
        )
        in 500..599 -> Explanation(
            "服务商或网关异常",
            "通常是服务端问题，请稍后重试；若持续出现，检查中转站或服务商状态。"
        )
        else -> Explanation(
            "请求失败",
            "查看服务商返回内容，并核对网络、API 密钥、协议与模型配置。"
        )
    }

    private fun networkStatus(detail: String, error: Throwable?): Explanation {
        val chain = generateSequence(error) { it.cause }.toList()
        val lower = detail.lowercase()
        return when {
            chain.any { it is UnknownHostException } ||
                "unable to resolve" in lower || "unknownhost" in lower || "无法解析" in detail ->
                Explanation("无法解析服务器地址", "确认域名拼写、DNS 与网络；如果使用中转站，确认域名仍然有效。")
            "cleartext" in lower || "明文" in detail ->
                Explanation("系统拦截了 HTTP 明文请求", "Android 默认限制明文 HTTP；请改用 HTTPS，或仅在确认风险的调试环境允许明文。")
            chain.any { it is SSLException } ||
                "ssl" in lower || "certificate" in lower || "handshake" in lower || "证书" in detail ->
                Explanation("TLS 或证书校验失败", "确认设备时间正确、代理没有替换证书；自定义地址应使用有效的 HTTPS 证书。")
            chain.any { it is SocketTimeoutException } ||
                "timeout" in lower || "timed out" in lower || "超时" in detail ->
                Explanation("连接或读取超时", "检查网络和代理；若模型生成较慢，可稍后重试或降低输出长度。")
            chain.any { it is ConnectException } ||
                "connection refused" in lower || "failed to connect" in lower ||
                "network is unreachable" in lower || "no route to host" in lower ||
                "connection abort" in lower || "拒绝连接" in detail ->
                Explanation("无法建立连接", "确认服务已启动、端口和协议正确；如果填写本机地址，确认设备本身能够访问该地址。")
            chain.any { it is EOFException } ||
                "unexpected end of stream" in lower || "connection reset" in lower ||
                "broken pipe" in lower || "stream was reset" in lower || "连接已关闭" in detail ->
                Explanation("连接在响应完成前中断", "检查代理或网关稳定性后重试；已经生成的回答会保留。")
            "sse" in lower || "流式" in detail || "事件" in detail ->
                Explanation("流式响应格式异常", "服务商返回的事件不完整或与所选协议不匹配；核对协议与基础 URL，并查看原始响应。")
            chain.any { it is ProtocolException } || "protocol" in lower ->
                Explanation("网络协议异常", "确认接口地址、协议类型和代理设置；如使用中转站，检查其兼容性。")
            "json" in lower || "解析" in detail ->
                Explanation("响应解析失败", "确认接口地址指向 API，而不是网页或登录页，并查看原始响应。")
            "invalid url" in lower || "url" in lower && "invalid" in lower ||
                "expected url scheme" in lower || "no scheme" in lower ||
                "malformed url" in lower || "invalid host" in lower || "地址格式" in detail ->
                Explanation("接口地址格式无效", "填写完整的 http(s) 地址，并确认没有多余空格或缺失主机名。")
            else -> Explanation("请求未完成", "查看错误详情与服务商原始响应，核对网络、API 密钥、协议和模型名称。")
        }
    }

    private fun providerDetail(body: String, apiKey: String): String {
        val safe = DiagnosticRedaction.text(body.trim(), apiKey)
        if (safe.isBlank()) return ""
        val json = runCatching { JSONTokener(safe).nextValue() }.getOrNull()
        val detail = when (json) {
            is JSONObject -> detailFromObject(json)
            is JSONArray -> (0 until json.length()).firstNotNullOfOrNull { index ->
                (json.opt(index) as? JSONObject)?.let(::detailFromObject)
                    ?: (json.opt(index) as? String)?.takeIf { it.isNotBlank() }
            }
            is String -> json
            else -> null
        }
        return (detail?.takeIf { it.isNotBlank() } ?: normalizePlain(safe)).take(MAX_DETAIL)
    }

    private fun detailFromObject(obj: JSONObject): String? {
        val errorValue = obj.opt("error")
        val errorObj = errorValue as? JSONObject
        val errors = errorObj?.optJSONArray("errors") ?: obj.optJSONArray("errors")
        val nestedMessage = errors?.optJSONObject(0)?.optString("message")?.takeIf { it.isNotBlank() }
        val message = sequenceOf(
            errorObj?.optString("message"),
            (errorValue as? String),
            obj.optString("message"),
            obj.optString("msg"),
            obj.optString("error_msg"),
            obj.optString("error_description"),
            nestedMessage,
            obj.optString("detail")
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
        val code = sequenceOf(
            errorObj?.optString("type"),
            errorObj?.optString("status"),
            errorObj?.optString("code"),
            obj.optString("type"),
            obj.optString("status"),
            obj.optString("error_code"),
            obj.optString("code")
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
        return when {
            code != null && message != null -> "$code: $message"
            message != null -> message
            code != null -> code
            else -> null
        }
    }

    private fun normalizePlain(value: String): String =
        value.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun appendContext(
        builder: StringBuilder,
        requestUrl: String,
        model: String,
        apiKey: String
    ) {
        val url = DiagnosticRedaction.text(requestUrl.trim(), apiKey)
            .substringBefore('#')
            .substringBefore('?')
            .take(MAX_URL)
        if (url.isNotBlank()) builder.append("\n请求地址：").append(url)
        val safeModel = DiagnosticRedaction.text(model.trim(), apiKey).take(MAX_MODEL)
        if (safeModel.isNotBlank()) builder.append("\n模型：").append(safeModel)
    }
}
