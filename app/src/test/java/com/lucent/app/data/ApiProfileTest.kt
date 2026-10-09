package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiProfileTest {

    @Test
    fun testUserAgentPresetsValues() {
        assertEquals("", UserAgentPresets.DEFAULT)
        assertEquals("node-fetch (+https://github.com/node-fetch/node-fetch)", UserAgentPresets.SILLY_TAVERN_NODE_FETCH)
    }

    @Test
    fun testApiProfileCustomUserAgentRoundTrip() {
        val original = listOf(
            ApiProfile(
                name = "SillyTavern Profile",
                spec = "openai",
                baseUrl = "https://api.example.com/v1",
                apiKey = "test-key-123",
                model = "gpt-4o",
                customUserAgent = UserAgentPresets.SILLY_TAVERN_NODE_FETCH
            ),
            ApiProfile(
                name = "Default Profile",
                spec = "openai",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-default",
                model = "gpt-3.5-turbo",
                customUserAgent = ""
            )
        )

        val serialized = ApiProfiles.serialize(original, encryptKeys = false)
        val parsed = ApiProfiles.parse(serialized)

        assertEquals(2, parsed.size)
        assertEquals("SillyTavern Profile", parsed[0].name)
        assertEquals(UserAgentPresets.SILLY_TAVERN_NODE_FETCH, parsed[0].customUserAgent)
        assertEquals("Default Profile", parsed[1].name)
        assertEquals("", parsed[1].customUserAgent)
    }

    @Test
    fun testLegacyJsonWithoutCustomUserAgentParsesGracefully() {
        val legacyJson = """
            [
                {
                    "name": "Legacy Profile",
                    "spec": "openai",
                    "baseUrl": "https://legacy.example.com",
                    "model": "model-1",
                    "provider": "custom",
                    "selectedModels": ["model-1"]
                }
            ]
        """.trimIndent()

        val parsed = ApiProfiles.parse(legacyJson)
        assertEquals(1, parsed.size)
        assertEquals("Legacy Profile", parsed[0].name)
        assertEquals("", parsed[0].customUserAgent)
    }
}
