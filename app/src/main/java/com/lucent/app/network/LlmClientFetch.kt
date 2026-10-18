package com.lucent.app.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal object LlmClientFetch {
    suspend fun fetchModels(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        rawEndpoint: Boolean = false
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = baseUrl.trim().trimEnd('/')
            val url = if (rawEndpoint) trimmed else when (spec) {
                ApiSpec.GOOGLE -> {
                    val base = if (trimmed.endsWith("/models")) trimmed else "$trimmed/models"
                    if (apiKey.isNotBlank() && !base.contains("key=")) {
                        if (base.contains("?")) "$base&key=${queryValue(apiKey)}" else "$base?key=${queryValue(apiKey)}"
                    } else base
                }
                ApiSpec.ANTHROPIC, ApiSpec.OPENAI -> {
                    if (trimmed.endsWith("/models")) trimmed else "$trimmed/models"
                }
            }

            val requestBuilder = Request.Builder().url(url).get()
            applyHeaders(requestBuilder, spec, apiKey)

            val response = BufferedTransport.executeBuffered(LlmHttpClients.client, requestBuilder.build())
            val bodyStr = response.body
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: ${DiagnosticRedaction.text(bodyStr, apiKey)}"))
            val json = JSONObject(bodyStr)
            val dataArray = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
            val ids = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i)
                var id = item?.optString("id", item.optString("name", "")) ?: dataArray.optString(i)
                if (spec == ApiSpec.GOOGLE) id = id.removePrefix("models/")
                if (id.isNotBlank()) {
                    val trimmedId = id.trim()
                    ids.add(trimmedId)
                    val reasoningObj = item?.optJSONObject("reasoning")
                        ?: item?.optJSONObject("capabilities")?.optJSONObject("reasoning")
                    if (reasoningObj != null) {
                        val supported = reasoningObj.optBoolean("supported", true)
                        val controlStr = reasoningObj.optString("control")
                        val control = when (controlStr.lowercase()) {
                            "openai_effort", "effort" -> com.lucent.app.data.ReasoningControl.OPENAI_EFFORT
                            "anthropic_effort" -> com.lucent.app.data.ReasoningControl.ANTHROPIC_EFFORT
                            "anthropic_budget" -> com.lucent.app.data.ReasoningControl.ANTHROPIC_BUDGET
                            "gemini_thinking_level", "thinking_level" -> com.lucent.app.data.ReasoningControl.GEMINI_THINKING_LEVEL
                            "gemini_thinking_budget", "thinking_budget" -> com.lucent.app.data.ReasoningControl.GEMINI_THINKING_BUDGET
                            "deepseek_effort" -> com.lucent.app.data.ReasoningControl.DEEPSEEK_EFFORT
                            "qwen_effort" -> com.lucent.app.data.ReasoningControl.QWEN_EFFORT
                            "zhipu_effort", "glm_effort" -> com.lucent.app.data.ReasoningControl.ZHIPU_EFFORT
                            "none" -> com.lucent.app.data.ReasoningControl.NONE
                            else -> if (spec == ApiSpec.GOOGLE) com.lucent.app.data.ReasoningControl.GEMINI_THINKING_LEVEL else com.lucent.app.data.ReasoningControl.OPENAI_EFFORT
                        }
                        val levelArray = reasoningObj.optJSONArray("levels")
                        val levels = if (levelArray == null) emptyList<String>() else (0 until levelArray.length())
                            .map { levelArray.optString(it) }
                            .filter { it.isNotBlank() }
                        com.lucent.app.data.ModelCapabilityRegistry.register(
                            com.lucent.app.data.ModelDefinition(
                                id = trimmedId,
                                reasoning = com.lucent.app.data.ReasoningCapability(
                                    supported = supported,
                                    control = control,
                                    isAdaptive = reasoningObj.optBoolean("adaptive", true),
                                    levels = levels
                                )
                            )
                        )
                    } else {
                        com.lucent.app.data.ModelCapabilityRegistry.findOrRegister(trimmedId, spec.name)
                    }
                }
            }
            Result.success(ids.distinct())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Result.failure(DiagnosticRedaction.failure(e, apiKey))
        }
    }
}
