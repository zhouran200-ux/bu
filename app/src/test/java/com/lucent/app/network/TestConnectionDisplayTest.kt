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
    fun endpointAndCatalogProbesDoNotClaimGenerationWorks() {
        val catalog = TestConnectionResult(
            success = true, httpCode = 200, latencyMs = 8, targetModel = "",
            requestUrl = "https://api.example.com/models", rawResponseBody = "[]",
            probeKind = ConnectionProbeKind.ModelCatalog
        ).formatDisplayMessage()
        assertTrue(catalog.contains("模型列表探测"))
        assertTrue(catalog.contains("未验证模型调用"))
        val endpoint = TestConnectionResult(
            success = false, httpCode = 405, latencyMs = 9, targetModel = "",
            requestUrl = "https://api.example.com/generate", rawResponseBody = "",
            errorMessage = "method not allowed", probeKind = ConnectionProbeKind.Endpoint
        ).formatDisplayMessage()
        assertTrue(endpoint.contains("HEAD"))
        assertTrue(endpoint.contains("不能据此判定生成接口无效"))
    }

    @Test
    fun statusIsBoundedAndSuccessfulHttpDoesNotHideMissingGeneration() {
        assertTrue(failure(200, "{}", "no visible text").formatDisplayMessage().contains("尚未确认"))
        val oversized = org.json.JSONObject().put("error", org.json.JSONObject().put("message", "x".repeat(10000))).toString()
        assertTrue(failure(400, oversized).formatDisplayMessage().length <= 600)
    }

    @Test
    fun successfulGenerationKeepsLatencySummary() {
        val result = TestConnectionResult(
            success = true, httpCode = 200, latencyMs = 88, targetModel = "m",
            requestUrl = "https://api.example.com", rawResponseBody = "{}"
        )
        assertEquals("✓ 模型生成成功 [m] (88ms)", result.formatDisplayMessage())
    }
}
