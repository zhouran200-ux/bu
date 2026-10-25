package com.lucent.app.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class StreamingResponseReaderTest {
    private fun wire(vararg lines: String) = lines.joinToString(10.toChar().toString())
    private fun event(json: String) = wire("data: " + json, "", "")
    private fun read(text: String, spec: ApiSpec = ApiSpec.OPENAI) = runBlocking {
        StreamingResponseReader.read(Buffer().writeUtf8(text), spec, {}, { _, _ -> })
    }

    @Test fun nullAndNonStringDeltasAreNotRenderedAsText() {
        val result = read(
            event("""{"choices":[{"delta":{"content":null,"reasoning_content":null}}]}""") +
                event("""{"choices":[{"delta":{"content":{},"reasoning_content":[],"reasoning":"thought"}}]}""") +
                event("""{"choices":[{"delta":{"content":"hello"}}]}""") + event("[DONE]")
        )
        assertEquals("hello", result.content)
        assertEquals("thought", result.reasoningText)
    }

    @Test fun multilineJsonDeliversTextAndUsage() {
        val text = wire("data: {", "data: " + """"choices":[{"delta":{"content":"hello"}}],""", "data: " + """"usage":{"prompt_tokens":9}}""", "", "data: [DONE]", "", "")
        val result = read(text)
        assertEquals("hello", result.content)
        assertEquals(9, result.promptTokens)
    }

    @Test fun separateCompleteObjectsInsideOneEventCannotBeDeliveredAsTwoEvents() {
        val chunks = mutableListOf<String>()
        val json = """{"choices":[{"delta":{"content":"hello"}}]}"""
        val text = wire("data: " + json, "data: " + json, "", "data: [DONE]", "", "")
        try {
            runBlocking { StreamingResponseReader.read(Buffer().writeUtf8(text), ApiSpec.OPENAI, {}, { text, _ -> chunks += text }) }
            fail("Expected invalid event failure")
        } catch (_: IOException) { assertTrue(chunks.isEmpty()) }
    }

    @Test fun malformedEventCannotBeHiddenByDone() {
        try { read(event("not-json") + event("[DONE]")); fail("Expected failure") }
        catch (_: IOException) { }
    }

    @Test fun validJsonWithErrorIsNotSuccess() {
        for (spec in ApiSpec.entries) {
            try { read(wire("event: error", "data: " + """{"error":{"message":"bad"}}""", "", ""), spec); fail("Expected failure") }
            catch (_: IOException) { }
        }
    }

    @Test fun incompleteStreamsFailForEveryProvider() {
        for (spec in ApiSpec.entries) {
            try { read(event("{}"), spec); fail("Expected incomplete stream failure") }
            catch (_: IOException) { }
        }
    }

    @Test fun googleFalseAndMissingThoughtRemainBodyInPartOrder() {
        val json = """{"candidates":[{"content":{"parts":[{"text":"think","thought":true},{"text":"body","thought":false},{"text":"tail"}]},"finishReason":"STOP"}]}"""
        val result = read(event(json), ApiSpec.GOOGLE)
        assertEquals("bodytail", result.content)
        assertEquals("think", result.reasoningText)
    }

    @Test fun googleUsageAndPartCallbacksKeepArrivalOrder() = runBlocking {
        val chunks = mutableListOf<Pair<String, String?>>()
        val json = """{"candidates":[{"content":{"parts":[{"text":"a","thought":false},{"text":"b","thought":true},{"text":"c"}]},"finishReason":"MAX_TOKENS"}],"usageMetadata":{"promptTokenCount":9}}"""
        val result = StreamingResponseReader.read(Buffer().writeUtf8(event(json)), ApiSpec.GOOGLE, {}, { text, reasoning -> chunks += text to reasoning })
        assertEquals(listOf("a" to null, "" to "b", "c" to null), chunks)
        assertEquals("ac", result.content)
        assertEquals(9, result.promptTokens)
    }

    @Test fun unclosedEventNeverDeliversEvenIfItsJsonIsComplete() {
        val chunks = mutableListOf<String>()
        val json = """{"choices":[{"delta":{"content":"hello"}}]}"""
        try {
            runBlocking { StreamingResponseReader.read(Buffer().writeUtf8(wire("data: " + json, "")), ApiSpec.OPENAI, {}, { text, _ -> chunks += text }) }
            fail("Expected truncated event failure")
        } catch (_: IOException) { assertTrue(chunks.isEmpty()) }
    }

    @Test fun anthropicStopIsTerminal() {
        assertEquals("", read(event("""{"type":"message_stop"}"""), ApiSpec.ANTHROPIC).content)
    }

    @Test fun cancellationPreservesPreviouslyDeliveredChunks() = runBlocking {
        val chunks = mutableListOf<String>()
        val text = event("""{"choices":[{"delta":{"content":"hello"}}]}""") + event("[DONE]")
        try {
            StreamingResponseReader.read(Buffer().writeUtf8(text), ApiSpec.OPENAI, {}, { text, _ -> chunks += text; throw CancellationException("stop") })
            fail("Expected cancellation")
        } catch (_: CancellationException) { assertEquals(listOf("hello"), chunks) }
    }
}
