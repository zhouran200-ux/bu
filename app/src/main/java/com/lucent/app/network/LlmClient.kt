package com.lucent.app.network

object LlmClient {
    suspend fun fetchModels(baseUrl: String, spec: ApiSpec, apiKey: String, rawEndpoint: Boolean = false): Result<List<String>> =
        LlmClientFetch.fetchModels(baseUrl, spec, apiKey, rawEndpoint)

    suspend fun testConnection(baseUrl: String, spec: ApiSpec, apiKey: String, model: String = "", rawEndpoint: Boolean = false): Result<TestConnectionResult> =
        LlmClientConnection.testConnection(baseUrl, spec, apiKey, model, rawEndpoint)

    suspend fun chatStream(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String,
        messages: List<ChatMessagePayload>,
        systemInstruction: String = "",
        reasoningConfig: com.lucent.app.data.ResolvedReasoningConfig? = null,
        handle: StreamCallHandle? = null,
        rawEndpoint: Boolean = false,
        onChunk: suspend (deltaContent: String, deltaReasoning: String?) -> Unit
    ): Result<ChatCompletionResult> =
        LlmClientStream.chatStream(baseUrl, spec, apiKey, model, messages, systemInstruction, reasoningConfig, handle, rawEndpoint, onChunk)
}

data class ChatMessagePayload(
    val role: String,
    val content: String
)

data class ChatCompletionResult(
    val content: String,
    val reasoningText: String? = null,
    val promptTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cachedTokensKnown: Boolean = false
)
