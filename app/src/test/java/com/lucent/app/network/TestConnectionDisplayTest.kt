package com.lucent.app.network

import org.junit.Assert.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class TestConnectionDisplayTest {
    private fun failure(
        httpCode: Int,
        raw: String = "",
        errorMessage: String? = null
    ) = TestConnectionResult(
        success = false, httpCode = httpCode, latencyMs = 12, targetModel = "m",
        requestUrl = "https://api.example.com", rawResponseBody = raw,
        errorMessage = errorMessage
    )

    @Test
    fun plainExplanationComesFirstFollowedByProviderDetail() {
        val message = failure(401, """{"error":{"message":"Incorrect API key","type":"invalid_request_error"}}""")
            .formatDisplayMessage()
        assertTrue(message.startsWith("API Key 无效或未授权"), message)
        assertTrue(message.contains("详细信息：invalid_request_error: Incorrect API key"), message)
    }

    @Test
    fun quotaAndRateLimitMentionWaiting() {
        val message = failure(429, """{"error":{"message":"rate limit exceeded"}}""").formatDisplayMessage()
        assertTrue(message.startsWith("额度不足或请求太频繁"), message)
        assertTrue(message.contains("rate limit exceeded"), message)
    }

    @Test
    fun timeoutAndUnresolvableHostExplainTheNextStep() {
        assertTrue(
            failure(0, errorMessage = "java.net.SocketTimeoutException: Read timed out")
                .formatDisplayMessage().startsWith("连接超时")
        )
        assertTrue(
            failure(0, errorMessage = "Unable to resolve host api.example.com")
                .formatDisplayMessage().startsWith("无法解析地址")
        )
        assertTrue(
            failure(0, errorMessage = "Connection refused")
                .formatDisplayMessage().startsWith("连接被拒绝")
        )
    }

    @Test
    fun serverErrorsDoNotBlameLocalConfiguration() {
        assertTrue(failure(503).formatDisplayMessage().contains("通常是服务端问题"))
    }

    @Test
    fun missingDetailFallsBackToExplanationOnly() {
        assertEquals("请求失败（HTTP 418）", failure(418).formatDisplayMessage())
    }

    @Test
    fun successfulConnectionKeepsLatencySummary() {
        val result = TestConnectionResult(
            success = true, httpCode = 200, latencyMs = 88, targetModel = "m",
            requestUrl = "https://api.example.com", rawResponseBody = "{}"
        )
        assertEquals("✓ 连接成功 [m] (88ms)", result.formatDisplayMessage())
    }
}
