package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject

data class ApiProfile(
    val name: String = "方案 1",
    val spec: String = "openai",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val provider: String = ApiProviders.CUSTOM,
    val selectedModels: List<String> = emptyList(),
    val customUserAgent: String = ""
)

object UserAgentPresets {
    const val DEFAULT = ""
    const val SILLY_TAVERN_NODE_FETCH = "node-fetch (+https://github.com/node-fetch/node-fetch)"
}

object ApiProfiles {

    const val MAX = 20

    val DEFAULT_PROFILES = listOf(
        ApiProfile(
            name = "商汤",
            spec = "openai",
            baseUrl = "https://token.sensenova.cn/v1",
            apiKey = "",
            model = "",
            provider = ApiProviders.CUSTOM
        )
    )

    fun serialize(profiles: List<ApiProfile>, encryptKeys: Boolean = true): String {
        val arr = JSONArray()
        profiles.take(MAX).forEach { p ->
            val models = JSONArray()
            p.selectedModels.forEach { models.put(it) }
            arr.put(
                JSONObject()
                    .put("name", p.name)
                    .put("spec", p.spec)
                    .put("baseUrl", p.baseUrl)
                    .put("keyEnc", if (encryptKeys) CryptoUtil.encrypt(p.apiKey) else p.apiKey)
                    .put("model", p.model)
                    .put("provider", p.provider)
                    .put("selectedModels", models)
                    .put("customUserAgent", p.customUserAgent)
            )
        }
        return arr.toString()
    }

    fun parse(json: String?): List<ApiProfile> {
        if (json.isNullOrBlank()) return DEFAULT_PROFILES
        val list = try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val spec = o.optString("spec", "openai")
                val baseUrl = o.optString("baseUrl", "")
                val model = o.optString("model", "")
                val stored = o.optJSONArray("selectedModels")
                val selectedModels = if (stored == null) {
                    listOfNotNull(model.trim().takeIf { it.isNotBlank() })
                } else {
                    (0 until stored.length())
                        .map { stored.optString(it) }
                        .filter { it.isNotBlank() }
                        .distinct()
                }
                ApiProfile(
                    name = o.optString("name", "方案 ${i + 1}"),
                    spec = spec,
                    baseUrl = baseUrl,
                    apiKey = CryptoUtil.decrypt(o.optString("keyEnc", "")),
                    model = model,
                    provider = ApiProviders.resolve(o.optString("provider", ""), spec, baseUrl),
                    selectedModels = selectedModels,
                    customUserAgent = o.optString("customUserAgent", "")
                )
            }.take(MAX)
        } catch (e: Exception) {
            emptyList()
        }
        return if (list.isEmpty()) DEFAULT_PROFILES else list
    }

    fun serializeForBackup(profiles: List<ApiProfile>): String = serialize(profiles, encryptKeys = true)

    fun nextDefaultName(existing: List<ApiProfile>): String {
        val taken = existing.mapNotNull { p ->
            Regex("^(?:API|方案)\\s*(\\d+)$").find(p.name.trim())?.groupValues?.get(1)?.toIntOrNull()
        }.toSet()
        var n = 1
        while (n in taken) n++
        return "方案 $n"
    }
}
