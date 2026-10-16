package com.lucent.app.network

import org.junit.Assert.*
import org.junit.Test

class DiagnosticRedactionTest {
    @Test fun redactsEncodedAndRepeatedQuerySecrets() {
        val safe = DiagnosticRedaction.text("https://host/path?%6Bey=first&API_KEY=second&access_token=third&model=visible#section")
        for (secret in listOf("first", "second", "third")) assertFalse(safe.contains(secret))
        assertTrue(safe.contains("model=visible"))
        assertTrue(safe.contains("#section"))
    }

    @Test fun preservesNonSensitiveUrls() {
        val raw = "https://host/models?model=gpt&alt=sse"
        assertEquals(raw, DiagnosticRedaction.text(raw))
    }

    @Test fun sanitizesKnownSecretInAllReturnedDiagnosticFields() {
        val result = TestConnectionResult(false, 401, 1, "model", "https://host/?key=abc-secret", "echo abc-secret", "failure abc-secret", "agent abc-secret").redacted("abc-secret")
        for (field in listOf(result.requestUrl, result.rawResponseBody, result.errorMessage.orEmpty(), result.userAgentUsed.orEmpty(), result.toFullReport(), result.formatDisplayMessage())) assertFalse(field.contains("abc-secret"))
    }

    @Test fun formatterProtectsDirectlyConstructedResults() {
        val raw = "https://host/?key=url-secret"
        val result = TestConnectionResult(false, 500, 1, "m", raw, raw, raw)
        assertFalse(result.toFullReport().contains("url-secret"))
        assertFalse(result.prettyResponseBody().contains("url-secret"))
        assertFalse(result.formatDisplayMessage().contains("url-secret"))
    }

    @Test fun actualConnectionFailureDoesNotReturnTheConfiguredKey() = kotlinx.coroutines.runBlocking {
        val result = LlmClient.testConnection(
            baseUrl = "not a valid URL?key=network-secret", spec = ApiSpec.GOOGLE,
            apiKey = "network-secret", model = "gemini-test"
        ).getOrThrow()
        assertFalse(result.success)
        for (field in listOf(result.requestUrl, result.rawResponseBody, result.errorMessage.orEmpty(), result.toFullReport())) assertFalse(field.contains("network-secret"))
    }

    @Test fun encodedSecretInEchoedFailureIsRemoved() {
        assertFalse(DiagnosticRedaction.text("echo a%2Bb%2Fc", "a+b/c").contains("a%2Bb%2Fc"))
    }

    @Test fun hidesUserInfoAndToleratesMalformedEncoding() {
        val safe = DiagnosticRedaction.text("https://name:password@host/path?key=value")
        assertFalse(safe.contains("password"))
        assertFalse(safe.contains("name:"))
        assertTrue(safe.contains("@host/path"))
        assertFalse(DiagnosticRedaction.text("https://host/?%zz=abc-secret", "abc-secret").contains("abc-secret"))
    }
}
