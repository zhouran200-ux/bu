package com.lucent.app.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class RequestFailureFormatterTest {
    @Test fun httpFailureExplainsStatusKeepsProviderDetailAndRedactsSecrets() {
        val key = "sk-secret-value"
        val message = RequestFailureFormatter.http(
            code = 401,
            body = """{"error":{"message":"Incorrect API key $key","type":"invalid_request_error"}}""",
            apiKey = key,
            responseMessage = "Unauthorized",
            requestUrl = "https://api.example.com/v1/chat/completions?key=$key",
            model = "gpt-5"
        )
        assertTrue(message.startsWith("HTTP 401：API Key 无效或未授权"), message)
        assertTrue(message.contains("invalid_request_error: Incorrect API key [REDACTED]"), message)
        assertTrue(message.contains("建议："), message)
        assertTrue(message.contains("请求地址：https://api.example.com/v1/chat/completions"), message)
        assertTrue(message.contains("模型：gpt-5"), message)
        assertFalse(message.contains(key), message)
    }

    @Test fun statusSpecificGuidanceCoversEndpointQuotaAndServerFailures() {
        val notFound = RequestFailureFormatter.http(404, """{"message":"model not found"}""")
        assertTrue(notFound.startsWith("HTTP 404：接口地址或模型不存在"), notFound)
        assertTrue(notFound.contains("model not found"), notFound)

        val limited = RequestFailureFormatter.http(429, """{"error":{"message":"quota exhausted"}}""")
        assertTrue(limited.startsWith("HTTP 429：请求频率过高或额度不足"), limited)
        assertTrue(limited.contains("quota exhausted"), limited)

        val server = RequestFailureFormatter.http(503, "<html><body>Bad Gateway</body></html>")
        assertTrue(server.startsWith("HTTP 503：服务商或网关异常"), server)
        assertTrue(server.contains("Bad Gateway"), server)
        assertTrue(server.contains("通常是服务端问题"), server)
    }

    @Test fun unknownStatusStillGetsAReadableFallback() {
        val message = RequestFailureFormatter.http(418, "", responseMessage = "I'm a teapot")
        assertTrue(message.startsWith("HTTP 418：请求失败"), message)
        assertTrue(message.contains("I'm a teapot"), message)
    }

    @Test fun networkFailureDistinguishesTimeoutDnsTlsCleartextAndRefusal() {
        val timeout = RequestFailureFormatter.network(SocketTimeoutException("Read timed out"))
        assertTrue(timeout.startsWith("连接或读取超时"), timeout)
        assertTrue(timeout.contains("SocketTimeoutException: Read timed out"), timeout)

        val dns = RequestFailureFormatter.network(UnknownHostException("Unable to resolve host api.test"))
        assertTrue(dns.startsWith("无法解析服务器地址"), dns)

        val tls = RequestFailureFormatter.network(SSLHandshakeException("Certificate expired"))
        assertTrue(tls.startsWith("TLS 或证书校验失败"), tls)

        val cleartext = RequestFailureFormatter.network(
            UnknownServiceException("CLEARTEXT communication to api.test not permitted")
        )
        assertTrue(cleartext.startsWith("系统拦截了 HTTP 明文请求"), cleartext)

        val refused = RequestFailureFormatter.network(ConnectException("Connection refused"))
        assertTrue(refused.startsWith("无法建立连接"), refused)

        val invalid = RequestFailureFormatter.network(
            IllegalArgumentException("Expected URL scheme 'http' or 'https' but no scheme was found")
        )
        assertTrue(invalid.startsWith("接口地址格式无效"), invalid)
    }

    @Test fun streamAndProtocolFailuresKeepTheirCause() {
        val stream = RequestFailureFormatter.network(IOException("SSE 流在收到结束信号前断开"))
        assertTrue(stream.startsWith("流式响应格式异常"), stream)
        assertTrue(stream.contains("SSE 流在收到结束信号前断开"), stream)

        val empty = RequestFailureFormatter.protocol(
            "服务商返回了空响应体",
            requestUrl = "https://api.example.com/v1/chat/completions",
            model = "m"
        )
        assertTrue(empty.startsWith("响应格式异常：服务商返回了空响应体"), empty)
        assertTrue(empty.contains("请求地址：https://api.example.com/v1/chat/completions"), empty)
    }
}
