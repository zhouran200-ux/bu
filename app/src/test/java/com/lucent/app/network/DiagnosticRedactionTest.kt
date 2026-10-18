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
        for (field in listOf(result.requestUrl, result.rawResponseBody, result.errorMessage.orEmpty(), result.userAgentUsed.orEmpty(), result.formatDisplayMessage())) assertFalse(field.contains("abc-secret"))
    }

    @Test fun formatterProtectsDirectlyConstructedResults() {
        val raw = "https://host/?key=url-secret"
        val result = TestConnectionResult(false, 500, 1, "m", raw, raw, raw)
        assertFalse(result.formatDisplayMessage().contains("url-secret"))
        assertFalse(result.prettyResponseBody().contains("url-secret"))
        assertFalse(result.formatDisplayMessage().contains("url-secret"))
    }

    @Test fun actualConnectionFailureDoesNotReturnTheConfiguredKey() = kotlinx.coroutines.runBlocking {
        val result = LlmClient.testConnection(
            baseUrl = "not a valid URL?key=network-secret", spec = ApiSpec.GOOGLE,
            apiKey = "network-secret", model = "gemini-test"
        ).getOrThrow()
        assertFalse(result.success)
        for (field in listOf(result.requestUrl, result.rawResponseBody, result.errorMessage.orEmpty(), result.prettyResponseBody())) assertFalse(field.contains("network-secret"))
    }

    @Test fun modelFetchAndStreamFailuresDoNotExposeCredentialsFromInvalidUrls() = kotlinx.coroutines.runBlocking {
        val key = "a+b/c-secret"
        val url = "invalid URL https://user:password@host/?key=$key"
        val models = LlmClient.fetchModels(url, ApiSpec.GOOGLE, key)
        val streamed = LlmClient.chatStream(
            baseUrl = url, spec = ApiSpec.GOOGLE, apiKey = key,
            model = "gemini-test", messages = emptyList(), onChunk = { _, _ -> }
        )
        for (result in listOf(models, streamed)) {
            assertTrue(result.isFailure)
            val trace = result.exceptionOrNull()!!.stackTraceToString()
            for (secret in listOf(key, "password", "user:")) assertFalse(trace.contains(secret))
        }
    }

    @Test fun sanitizedFailureDoesNotRetainSecretsInCauseOrSuppressedExceptions() {
        val failure = IllegalArgumentException("echo secret-key", IllegalStateException("secret-key"))
        failure.addSuppressed(IllegalStateException("secret-key"))
        val safe = DiagnosticRedaction.failure(failure, "secret-key")
        assertFalse(safe.stackTraceToString().contains("secret-key"))
        assertTrue(safe.message!!.contains("[REDACTED]"))
        assertNull(safe.cause)
    }

    @Test fun encodedSecretInEchoedFailureIsRemoved() {
        assertFalse(DiagnosticRedaction.text("echo a%2Bb%2Fc", "a+b/c").contains("a%2Bb%2Fc"))
    }

    @Test fun escapedJsonEchoesAreRedactedBeforeFormatting() {
        val key = "a/b/c"
        for (escaped in listOf("a\\/b\\/c", "\\u0061/b/c", "a\\u002Fb/c")) {
            val body = """{"error":{"message":"echo $escaped"},"other":"visible"}"""
            val result = TestConnectionResult(false, 401, 1, "m", "https://host", body).redacted(key)
            for (field in listOf(result.rawResponseBody, result.prettyResponseBody(), result.formatDisplayMessage())) {
                assertFalse(field.contains(key))
                assertTrue(field.contains("[REDACTED]"))
            }
            assertEquals("visible", org.json.JSONObject(result.rawResponseBody).getString("other"))
        }
    }

    @Test fun unrelatedJsonEscapesRemainUnchanged() {
        val body = """{"message":"ordinary\\/path", "other":"\\u0061"}"""
        assertEquals(body, DiagnosticRedaction.text(body, "secret-key"))
    }

    @Test fun hidesUserInfoAndToleratesMalformedEncoding() {
        val safe = DiagnosticRedaction.text("https://name:password@host/path?key=value")
        assertFalse(safe.contains("password"))
        assertFalse(safe.contains("name:"))
        assertTrue(safe.contains("@host/path"))
        assertFalse(DiagnosticRedaction.text("https://host/?%zz=abc-secret", "abc-secret").contains("abc-secret"))
    }
}
