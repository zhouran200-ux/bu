package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ApiProfile(
    val name: String = "方案 1",
    val spec: String = "openai",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val selectedModels: List<String> = emptyList(),
    val id: String = UUID.randomUUID().toString()
)

object UserAgentPresets {
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
            id = deterministicId("商汤", "openai", "https://token.sensenova.cn/v1", 0)
        )
    )

    fun serialize(profiles: List<ApiProfile>): String {
        val arr = JSONArray()
        profiles.take(MAX).forEach { p ->
            val models = JSONArray()
            p.selectedModels.forEach { models.put(it) }
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("spec", p.spec)
                    .put("baseUrl", p.baseUrl)
                    .put("apiKey", p.apiKey)
                    .put("model", p.model)
                    .put("selectedModels", models)
            )
        }
        return arr.toString()
    }

    fun parse(json: String?): List<ApiProfile> {
        val stored = parseStored(json)
        return if (stored.isNullOrEmpty()) DEFAULT_PROFILES else stored
    }

    fun parseStored(json: String?): List<ApiProfile>? {
        if (json.isNullOrBlank()) return null
        val list = try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name", "方案 ${i + 1}")
                val spec = o.optString("spec", "openai")
                val baseUrl = o.optString("baseUrl", "")
                val model = o.optString("model", "")
                val stored = o.optJSONArray("selectedModels")
                val selectedModels = if (stored == null) {
                    listOfNotNull(model.trim().takeIf { it.isNotBlank() })
                } else {
                    (0 until stored.length())
                        .map { stored.optString(it).trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                }
                ApiProfile(
                    name = name,
                    spec = spec,
                    baseUrl = baseUrl,
                    apiKey = o.optString("apiKey", ""),
                    model = model,
                    selectedModels = selectedModels,
                    id = o.optString("id", "").ifBlank {
                        deterministicId(name, spec, baseUrl, i)
                    }
                )
            }.take(MAX)
        } catch (e: Exception) {
            return null
        }
        val reservedIds = list.map { it.id }.toMutableSet()
        val seenIds = mutableSetOf<String>()
        return list.mapIndexed { index, profile ->
            if (seenIds.add(profile.id)) {
                profile
            } else {
                var attempt = 0
                var replacement: String
                do {
                    replacement = deterministicId(profile.name, profile.spec, profile.baseUrl, index, attempt++)
                } while (!reservedIds.add(replacement))
                seenIds.add(replacement)
                profile.copy(id = replacement)
            }
        }
    }

    private fun deterministicId(name: String, spec: String, baseUrl: String, index: Int, attempt: Int = 0): String {
        val seed = listOf(name, spec, baseUrl, index.toString(), attempt.toString())
            .joinToString(separator = "") { "${it.length}:$it" }
        return UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
    }

    fun nextDefaultName(existing: List<ApiProfile>): String {
        val taken = existing.mapNotNull { p ->
            Regex("^(?:API|方案)\\s*(\\d+)$").find(p.name.trim())?.groupValues?.get(1)?.toIntOrNull()
        }.toSet()
        var n = 1
        while (n in taken) n++
        return "方案 $n"
    }

    fun <T> moved(items: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in items.indices || to !in items.indices) return items
        val out = items.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }

    fun selectionAfterMove(selected: Int, from: Int, to: Int): Int {
        if (from == to) return selected
        return when {
            selected == from -> to
            from < selected && to >= selected -> selected - 1
            from > selected && to <= selected -> selected + 1
            else -> selected
        }
    }

    fun availableModels(profile: ApiProfile): List<String> =
        (profile.selectedModels + profile.model).map { it.trim() }.filter { it.isNotBlank() }.distinct()

    fun resolveSelection(
        profiles: List<ApiProfile>,
        savedProfileId: String,
        savedProfileName: String,
        savedModel: String
    ): Pair<ApiProfile, String>? {
        val profile = if (savedProfileId.isNotBlank()) {
            profiles.firstOrNull { it.id == savedProfileId }
        } else {
            profiles.firstOrNull { it.name == savedProfileName }
        } ?: profiles.firstOrNull() ?: return null
        val models = availableModels(profile)
        val model = models.firstOrNull { it == savedModel.trim() }
            ?: models.firstOrNull { it == profile.model.trim() }
            ?: models.firstOrNull().orEmpty()
        return profile to model
    }

    fun selectionFor(
        profiles: List<ApiProfile>,
        savedProfileName: String,
        savedModel: String
    ): Pair<String, String> {
        val selection = resolveSelection(profiles, "", savedProfileName, savedModel) ?: return "" to ""
        return selection.first.name to selection.second
    }

    fun orderKeys(profiles: List<ApiProfile>): List<String> {
        val seen = mutableMapOf<String, Int>()
        return profiles.map { profile ->
            val repeat = seen.getOrDefault(profile.name, 0)
            seen[profile.name] = repeat + 1
            if (repeat == 0) profile.name else "${profile.name}#$repeat"
        }
    }
}
