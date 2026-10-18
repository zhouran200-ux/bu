package com.lucent.app.network

internal object ApiProbe {

    fun specOf(spec: String): ApiSpec = when (spec) {
        "anthropic" -> ApiSpec.ANTHROPIC
        "google" -> ApiSpec.GOOGLE
        else -> ApiSpec.OPENAI
    }

    suspend fun fetchModels(url: String, spec: String, key: String): Result<List<String>> =
        LlmClient.fetchModels(
            baseUrl = url.trim(),
            spec = specOf(spec),
            apiKey = key.trim(),
            rawEndpoint = spec == "custom"
        )

    suspend fun testConnection(
        url: String,
        spec: String,
        key: String,
        model: String
    ): Result<TestConnectionResult> =
        LlmClient.testConnection(
            baseUrl = url.trim(),
            spec = specOf(spec),
            apiKey = key.trim(),
            model = model,
            rawEndpoint = spec == "custom"
        )
}
