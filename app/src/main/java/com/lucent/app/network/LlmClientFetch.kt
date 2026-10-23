package com.lucent.app.network

import com.lucent.app.data.ModelCapabilityRegistry
import com.lucent.app.data.ModelDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal object LlmClientFetch {
    private const val MAX_PAGES = 100

    suspend fun fetchModels(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        rawEndpoint: Boolean = false
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val initialUrl = EndpointUrls.models(baseUrl, spec, apiKey, rawEndpoint)
            var url = initialUrl
            val ids = linkedSetOf<String>()
            val definitions = linkedMapOf<String, ModelDefinition>()
            val seenTokens = mutableSetOf<String>()
            var pages = 0
            while (true) {
                coroutineContext.ensureActive()
                check(pages++ < MAX_PAGES) { "Model pagination exceeded $MAX_PAGES pages" }
                val request = Request.Builder().url(url).get()
                applyHeaders(request, spec, apiKey)
                val response = BufferedTransport.executeBuffered(LlmHttpClients.client, request.build())
                if (!response.isSuccessful) return@withContext Result.failure(Exception(
                    "HTTP "+response.code+": "+DiagnosticRedaction.text(response.body, apiKey)))
                val json = JSONObject(response.body)
                val array = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
                for (i in 0 until array.length()) {
                    coroutineContext.ensureActive()
                    val item = array.optJSONObject(i)
                    var id = ((item?.opt("id") as? String) ?: (item?.opt("name") as? String)
                        ?: (array.opt(i) as? String)).orEmpty().trim()
                    if (spec == ApiSpec.GOOGLE) id = id.removePrefix("models/")
                    if (id.isBlank()) continue
                    ids.add(id)
                    val metadata = item?.optJSONObject("reasoning")
                        ?: item?.optJSONObject("capabilities")?.optJSONObject("reasoning")
                    if (metadata != null) discoveredReasoning(id, spec, metadata)?.let { definitions[id] = it }
                }
                val token = when (spec) {
                    ApiSpec.GOOGLE -> (json.opt("nextPageToken") as? String)?.takeIf { it.isNotBlank() }
                    ApiSpec.ANTHROPIC -> if (json.optBoolean("has_more", false)) {
                        (json.opt("last_id") as? String)?.takeIf { it.isNotBlank() }
                            ?: error("Model pagination missing last_id")
                    } else null
                    ApiSpec.OPENAI -> null
                } ?: break
                check(seenTokens.add(token)) { "Model pagination repeated continuation token" }
                url = initialUrl.newBuilder().setQueryParameter(
                    if (spec == ApiSpec.GOOGLE) "pageToken" else "after_id", token).build()
            }
            coroutineContext.ensureActive()
            definitions.values.forEach { ModelCapabilityRegistry.registerDiscovered(it, spec.name, baseUrl) }
            Result.success(ids.toList())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Result.failure(DiagnosticRedaction.failure(e, apiKey))
        }
    }
}
