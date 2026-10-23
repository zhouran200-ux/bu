package com.lucent.app.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object EndpointUrls {
    fun models(base: String, spec: ApiSpec, key: String, raw: Boolean): HttpUrl =
        build(base, spec, key, raw, "models")

    fun generation(base: String, spec: ApiSpec, key: String, model: String, raw: Boolean, stream: Boolean): HttpUrl {
        val url = base.trim().toHttpUrl()
        val suffix = when (spec) {
            ApiSpec.OPENAI -> "chat/completions"
            ApiSpec.ANTHROPIC -> if (url.encodedPath.trimEnd('/').endsWith("/v1") ||
                url.encodedPath.trimEnd('/').endsWith("/messages")) "messages" else "v1/messages"
            ApiSpec.GOOGLE -> "models"
        }
        var result = build(base, spec, key, raw, suffix)
        if (spec == ApiSpec.GOOGLE && !raw) {
            result = result.newBuilder().addPathSegment(model.trim().removePrefix("models/") +
                if (stream) ":streamGenerateContent" else ":generateContent").build()
        }
        if (spec == ApiSpec.GOOGLE && stream && !raw) {
            result = result.newBuilder().setQueryParameter("alt", "sse").build()
        }
        return result
    }

    private fun build(base: String, spec: ApiSpec, key: String, raw: Boolean, suffix: String): HttpUrl {
        val url = base.trim().toHttpUrl()
        if (raw) return url
        val builder = url.newBuilder()
        val path = url.encodedPath.trimEnd('/')
        if (!path.endsWith("/$suffix")) builder.encodedPath("$path/$suffix")
        else builder.encodedPath(path)
        if (spec == ApiSpec.GOOGLE && key.isNotBlank() && url.queryParameter("key") == null) {
            builder.addQueryParameter("key", key)
        }
        return builder.build()
    }
}
