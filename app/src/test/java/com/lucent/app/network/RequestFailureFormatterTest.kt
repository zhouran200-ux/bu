package com.lucent.app.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class RequestFailureFormatterTest {
    @Test fun httpFailureIsOneLineThenRawProviderData() {
        val key = "sk-secret-value"
        val headers = okhttp3.Headers.headersOf(
            "Content-Type", "application/json",
            "x-request-id", "req_abc123",
            "X-RateLimit-Remaining-Requests", "0"
        )
        val body = """{"error":{"message":"Incorrect API key $key","type":"invalid_request_error","code":"invalid_api_key"}}"""
        val message = RequestFailureFormatter.http(
            code = 401,
            body = body,
            apiKey = key,
            responseMessage = "Unauthorized",
            requestUrl = "https://api.example.com/v1/chat/completions?key=$key",
            model = "gpt-5",
            headersText = RequestFailureFormatter.headerText(headers, key)
        )
        val lines = message.lines()
        assertTrue(lines.first().startsWith("HTTP 401：API Key 无效或未授权"), message)
        assertTrue(message.contains("请求 ID：req_abc123"), message)
        assertTrue(message.contains("--- 原始数据 ---"), message)
        assertTrue(message.contains("状态：HTTP 401 Unauthorized"), message)
        assertTrue(message.contains("x-request-id: req_abc123"), message)
        assertTrue(message.contains("content-type: application/json"), message)
        assertTrue(message.contains("\"code\":\"invalid_api_key\""), message)
        assertTrue(message.contains("\"message\":\"Incorrect API key [REDACTED]\""), message)
        assertTrue(message.contains("请求地址：https://api.example.com/v1/chat/completions"), message)
        assertFalse(message.contains(key), message)
    }

    @Test fun providerRequestBodyAndIdSurviveWithoutSpecialHeaders() {
        val body = """{"request_id":"req_body_9","error":{"message":"model not found","code":"model_not_found"}}"""
        val message = RequestFailureFormatter.http(404, body)
        assertTrue(message.startsWith("HTTP 404：接口地址或模型不存在"), message)
        assertTrue(message.contains("请求 ID：req_body_9"), message)
        assertTrue(message.contains("model_not_found"), message)
        assertTrue(message.contains(body), message)
    }

    @Test fun unknownStatusStillShowsRawBody() {
        val message = RequestFailureFormatter.http(418, "", responseMessage = "I'm a teapot")
        assertTrue(message.startsWith("HTTP 418：请求失败"), message)
        assertTrue(message.contains("状态：HTTP 418 I'm a teapot"), message)
    }

    @Test fun longProviderBodyIsBoundedButMarkedTruncated() {
        val message = RequestFailureFormatter.http(500, "x".repeat(9000))
        assertTrue(message.contains("已截断"), message)
        assertTrue(message.length < 9000, message.length.toString())
    }

    @Test fun networkFailureKeepsTheOriginalExceptionText() {
        val timeout = RequestFailureFormatter.network(SocketTimeoutException("Read timed out"))
        assertTrue(timeout.startsWith("连接或读取超时"), timeout)
        assertTrue(timeout.contains("--- 原始数据 ---"), timeout)
        assertTrue(timeout.contains("java.net.SocketTimeoutException: Read timed out"), timeout)

        val dns = RequestFailureFormatter.network(UnknownHostException("Unable to resolve host api.test"))
        assertTrue(dns.startsWith("无法解析服务器地址"), dns)
        assertTrue(dns.contains("java.net.UnknownHostException"), dns)

        val tls = RequestFailureFormatter.network(SSLHandshakeException("Certificate expired"))
        assertTrue(tls.startsWith("TLS 或证书校验失败"), tls)

        val cleartext = RequestFailureFormatter.network(IOException("CLEARTEXT communication to api.test not permitted"))
        assertTrue(cleartext.startsWith("系统拦截了 HTTP 明文请求"), cleartext)

        val refused = RequestFailureFormatter.network(ConnectException("Connection refused"))
        assertTrue(refused.startsWith("无法建立连接"), refused)
        assertTrue(refused.contains("java.net.ConnectException: Connection refused"), refused)

        val invalid = RequestFailureFormatter.network(
            IllegalArgumentException("Expected URL scheme 'http' or 'https' but no scheme was found")
        )
        assertTrue(invalid.startsWith("接口地址格式无效"), invalid)
    }

    @Test fun streamFailureAndProtocolProblemsKeepRawContent() {
        val stream = RequestFailureFormatter.network(IOException("SSE 流在收到结束信号前断开"))
        assertTrue(stream.startsWith("流式响应格式异常"), stream)
        assertTrue(stream.contains("SSE 流在收到结束信号前断开"), stream)

        val empty = RequestFailureFormatter.protocol(
            "服务商返回了空响应体",
            requestUrl = "https://api.example.com/v1/chat/completions",
            model = "m"
        )
        assertTrue(empty.startsWith("响应格式异常"), empty)
        assertTrue(empty.contains("问题：服务商返回了空响应体"), empty)
        assertTrue(empty.contains("请求地址：https://api.example.com/v1/chat/completions"), empty)
    }

    @Test fun secretInHeadersAndUrlIsRedacted() {
        val key = "sk-live-123"
        val headers = okhttp3.Headers.headersOf("x-request-id", key, "authorization", "Bearer $key")
        val text = RequestFailureFormatter.headerText(headers, key)
        assertTrue(text.contains("x-request-id: [REDACTED]"), text)
        assertFalse(text.contains("authorization"), text)

        val message = RequestFailureFormatter.http(
            code = 403,
            body = """{"error":{"message":"denied for $key"}}""",
            apiKey = key,
            requestUrl = "https://api.example.com/v1?key=$key",
            headersText = text
        )
        assertFalse(message.contains(key), message)
    }
}
