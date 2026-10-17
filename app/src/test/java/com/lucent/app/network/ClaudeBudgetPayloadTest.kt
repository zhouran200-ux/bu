package com.lucent.app.network

import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.ResolvedReasoningConfig
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ClaudeBudgetPayloadTest {
    private fun payload(config: ResolvedReasoningConfig): JSONObject {
        val request = StreamingRequestBuilder.build("https://example.test/v1", ApiSpec.ANTHROPIC, "key", "custom-budget-model",
            listOf(ChatMessagePayload("user", "hello")), "", config, false).build()
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test fun everyBudgetLeavesOutputRoomWithoutChangingRequestedBudget() {
        for (budget in listOf(2048, 8192, 16384, 32768)) {
            val json = payload(ResolvedReasoningConfig(ReasoningPreset.DEEP, claudeBudgetTokens = budget))
            assertTrue(json.getInt("max_tokens") > budget)
            assertEquals(budget, json.getJSONObject("thinking").getInt("budget_tokens"))
        }
    }

    @Test fun adaptiveAndAutoKeepDefaultLimit() {
        val adaptive = payload(ResolvedReasoningConfig(ReasoningPreset.DEEP, claudeEffort = "high"))
        assertEquals(8192, adaptive.getInt("max_tokens"))
        assertEquals("high", adaptive.getJSONObject("output_config").getString("effort"))
        val auto = payload(ResolvedReasoningConfig.AUTO)
        assertEquals(8192, auto.getInt("max_tokens"))
        assertFalse(auto.has("thinking"))
    }
}
