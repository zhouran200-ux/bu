package com.lucent.app.network

import com.lucent.app.data.SettingsCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal object LlmHttpClients {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val streamClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

internal fun applyHeaders(
        builder: Request.Builder,
        spec: ApiSpec,
        apiKey: String
    ): String {
        val effectiveUa = com.lucent.app.data.SettingsCache.customUserAgent.trim()
        if (effectiveUa.isNotBlank()) {
            builder.header("User-Agent", effectiveUa)
            if (effectiveUa.contains("node-fetch", ignoreCase = true)) {
                builder.header("Accept", "*/*")
            }
        }
        when (spec) {
            ApiSpec.OPENAI -> {
                if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
            }
            ApiSpec.ANTHROPIC -> {
                if (apiKey.isNotBlank()) {
                    builder.header("x-api-key", apiKey)
                    builder.header("Authorization", "Bearer $apiKey")
                }
                builder.header("anthropic-version", "2023-06-01")
            }
            ApiSpec.GOOGLE -> {
                if (apiKey.isNotBlank()) builder.header("x-goog-api-key", apiKey)
            }
        }
        return effectiveUa
    }
