package com.lucent.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class StreamUsageParserTest {

    @Test
    fun openAiStyleUsageCarriesCachedTokensFromTheDetailsObject() {
        val chunk = JSONObject(
            """{"id":"c1","choices":[],"usage":{"prompt_tokens":8600,"completion_tokens":320,
               "prompt_tokens_details":{"cached_tokens":6144}}}"""
        )

        val usage = StreamUsageParser.parse(ApiSpec.OPENAI, chunk)

        assertEquals(8600, usage?.promptTokens)
        assertEquals(6144, usage?.cachedTokens)
    }

    @Test
    fun deepSeekStyleUsageUsesItsOwnCacheHitField() {
        val chunk = JSONObject(
            """{"choices":[],"usage":{"prompt_tokens":1200,"prompt_cache_hit_tokens":900,
               "prompt_cache_miss_tokens":300,"completion_tokens":80}}"""
        )

        val usage = StreamUsageParser.parse(ApiSpec.OPENAI, chunk)

        assertEquals(1200, usage?.promptTokens)
        assertEquals(900, usage?.cachedTokens)
    }

    @Test
    fun openAiDeltaWithoutUsageReportsNothing() {
        val chunk = JSONObject("""{"choices":[{"delta":{"content":"你好"}}]}""")

        assertNull(StreamUsageParser.parse(ApiSpec.OPENAI, chunk))
    }

    @Test
    fun anthropicMessageStartCountsCachedReadsAsPartOfThePrompt() {
        val chunk = JSONObject(
            """{"type":"message_start","message":{"usage":{"input_tokens":2400,
               "cache_read_input_tokens":1800,"cache_creation_input_tokens":200}}}"""
        )

        val usage = StreamUsageParser.parse(ApiSpec.ANTHROPIC, chunk)

        assertEquals(4400, usage?.promptTokens)
        assertEquals(1800, usage?.cachedTokens)
    }

    @Test
    fun anthropicContentDeltasCarryNoUsage() {
        val chunk = JSONObject("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"hi"}}""")

        assertNull(StreamUsageParser.parse(ApiSpec.ANTHROPIC, chunk))
    }

    @Test
    fun googleUsageMetadataIsReadFromEveryChunk() {
        val chunk = JSONObject(
            """{"candidates":[{"content":{"parts":[{"text":"hi"}]}}],
               "usageMetadata":{"promptTokenCount":5000,"cachedContentTokenCount":4864,
               "candidatesTokenCount":42}}"""
        )

        val usage = StreamUsageParser.parse(ApiSpec.GOOGLE, chunk)

        assertEquals(5000, usage?.promptTokens)
        assertEquals(4864, usage?.cachedTokens)
    }

    @Test
    fun streamOptionsAreOnlyRequestedFromHostsKnownToAcceptThem() {
        assertTrue(StreamUsageParser.wantsStreamOptions("https://api.openai.com/v1/chat/completions"))
        assertTrue(StreamUsageParser.wantsStreamOptions("https://api.deepseek.com/v1/chat/completions"))
        assertTrue(StreamUsageParser.wantsStreamOptions("https://api.moonshot.cn/v1/chat/completions"))
        assertFalse(StreamUsageParser.wantsStreamOptions("https://my-proxy.example.com/v1/chat/completions"))
        assertFalse(StreamUsageParser.wantsStreamOptions(""))
    }
}
