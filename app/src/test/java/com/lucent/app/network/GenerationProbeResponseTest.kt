package com.lucent.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationProbeResponseTest {
    @Test fun visibleTextIsRequiredForEachProtocol() {
        assertTrue(generationProbeHasText("""{"choices":[{"message":{"content":"Hello"}}]}""", ApiSpec.OPENAI))
        assertTrue(generationProbeHasText("""{"content":[{"type":"text","text":"Hello"}]}""", ApiSpec.ANTHROPIC))
        assertTrue(generationProbeHasText("""{"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}""", ApiSpec.GOOGLE))
        for (spec in ApiSpec.entries) {
            for (body in listOf("", "{}", "<html>OK</html>", """{"error":{"message":"denied"}}""")) {
                assertFalse(generationProbeHasText(body, spec))
            }
        }
    }

    @Test fun reasoningAndNullContentDoNotClaimVisibleGeneration() {
        assertFalse(generationProbeHasText("""{"choices":[{"message":{"content":null,"reasoning_content":"thinking"}}]}""", ApiSpec.OPENAI))
        assertFalse(generationProbeHasText("""{"content":[{"type":"thinking","thinking":"thinking"}]}""", ApiSpec.ANTHROPIC))
        assertFalse(generationProbeHasText("""{"candidates":[{"content":{"parts":[{"text":"thinking","thought":true}]}}]}""", ApiSpec.GOOGLE))
    }
}
