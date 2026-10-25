package com.lucent.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal enum class ApiProfileImportMode { Merge, Replace }

internal data class ApiProfileTxtParse(
    val profiles: List<ApiProfile>,
    val invalidLines: Int
)

internal data class ApiProfileImportPlan(
    val profiles: List<ApiProfile>,
    val selectedId: String?,
    val added: Int,
    val updated: Int,
    val dropped: Int
)

internal object ApiProfileTxt {
    const val FILE_NAME_PREFIX = "lucent-api-profiles"
    const val MAX_FILE_BYTES = 512 * 1024
    private const val RECORD = "[方案]"
    private const val RECORD_WORD = "方案"
    private const val SPEC_OPENAI = "openai"
    private const val NAME_PREFIX = "方案"
    private val SPECS = setOf("openai", "anthropic", "google", "custom")

    fun fileName(now: Long, zone: TimeZone): String =
        FILE_NAME_PREFIX + "-" + stamp("yyyyMMdd-HHmm", now, zone) + ".txt"

    fun encode(profiles: List<ApiProfile>, now: Long, zone: TimeZone): String = buildString {
        appendLine("# Lucent API 方案备份")
        appendLine("# format: lucent.api-profiles")
        appendLine("# version: 1")
        appendLine("# exported: " + stamp("yyyy-MM-dd'T'HH:mm:ssXXX", now, zone))
        appendLine("# count: " + profiles.size)
        appendLine("# 每段以 " + RECORD + " 开头，字段写成「键: 值」。")
        appendLine("# models 每行一个模型名，可以写多行。")
        appendLine("# 文件包含明文 API Key，请妥善保管。")
        profiles.forEach { profile ->
            appendLine()
            appendLine(RECORD)
            appendLine("name: " + oneLine(profile.name))
            appendLine("spec: " + oneLine(profile.spec))
            appendLine("baseUrl: " + oneLine(profile.baseUrl))
            appendLine("apiKey: " + oneLine(profile.apiKey))
            profile.selectedModels.forEach { appendLine("models: " + oneLine(it)) }
        }
    }

    fun decode(text: String): Result<ApiProfileTxtParse> {
        val records = mutableListOf<MutableMap<String, MutableList<String>>>()
        var current: MutableMap<String, MutableList<String>>? = null
        var invalid = 0
        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                val label = line.substring(1, line.length - 1).trim()
                val known = label == RECORD_WORD || label.equals("profile", true) || label.equals("api", true)
                if (known) {
                    val record = mutableMapOf<String, MutableList<String>>()
                    records += record
                    current = record
                } else {
                    invalid++
                }
                return@forEach
            }
            val colon = line.indexOf(':')
            val equals = line.indexOf('=')
            val separator = when {
                colon > 0 -> colon
                equals > 0 -> equals
                else -> -1
            }
            if (separator <= 0) {
                invalid++
                return@forEach
            }
            val key = line.substring(0, separator).trim().lowercase(Locale.US)
            if (key.isEmpty()) {
                invalid++
                return@forEach
            }
            val target = current ?: mutableMapOf<String, MutableList<String>>().also {
                records += it
                current = it
            }
            target.getOrPut(key) { mutableListOf() }.add(line.substring(separator + 1).trim())
        }
        val parsed = records.mapIndexedNotNull { index, record -> toProfile(record, index) }
        if (parsed.isEmpty()) return Result.failure(IllegalArgumentException("没有可识别的方案"))
        return Result.success(ApiProfileTxtParse(profiles = parsed, invalidLines = invalid))
    }

    private fun stamp(pattern: String, now: Long, zone: TimeZone): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(Date(now))

    private fun oneLine(value: String): String =
        value.replace('\r', ' ').replace('\n', ' ').trim()

    private fun toProfile(record: Map<String, List<String>>, index: Int): ApiProfile? {
        val name = last(record, "name")
        val baseUrl = last(record, "baseurl")
        val apiKey = last(record, "apikey")
        val legacyModel = last(record, "model")
        val models = record["models"].orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
        if (legacyModel.isNotBlank() && legacyModel !in models) models.add(legacyModel)
        if (name.isBlank() && baseUrl.isBlank() && apiKey.isBlank() && models.isEmpty()) return null
        val spec = last(record, "spec").lowercase(Locale.US)
        return ApiProfile(
            name = name.ifBlank { NAME_PREFIX + " " + (index + 1) },
            spec = if (spec in SPECS) spec else SPEC_OPENAI,
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModels = models.distinct()
        )
    }

    private fun last(record: Map<String, List<String>>, key: String): String =
        record[key].orEmpty().lastOrNull().orEmpty().trim()
}

internal fun planApiProfileImport(
    existing: List<ApiProfile>,
    existingSelectedId: String?,
    imported: List<ApiProfile>,
    mode: ApiProfileImportMode
): ApiProfileImportPlan {
    if (mode == ApiProfileImportMode.Replace) {
        val taken = mutableSetOf<String>()
        val replacement = imported.take(ApiProfiles.MAX).map { it.copy(id = freshProfileId(taken)) }
        return ApiProfileImportPlan(
            profiles = replacement,
            selectedId = replacement.firstOrNull()?.id,
            added = replacement.size,
            updated = 0,
            dropped = (imported.size - replacement.size).coerceAtLeast(0)
        )
    }
    val result = existing.toMutableList()
    val taken = result.map { it.id }.toMutableSet()
    var added = 0
    var updated = 0
    var dropped = 0
    imported.forEach { incoming ->
        val slot = result.indexOfFirst { sameProfileIdentity(it, incoming) }
        if (slot >= 0) {
            val current = result[slot]
            val merged = current.copy(
                apiKey = incoming.apiKey.ifBlank { current.apiKey },
                selectedModels = if (incoming.selectedModels.isEmpty()) current.selectedModels else incoming.selectedModels
            )
            if (merged != current) {
                result[slot] = merged
                updated++
            }
        } else if (result.size < ApiProfiles.MAX) {
            result += incoming.copy(id = freshProfileId(taken), name = uniqueProfileName(result, incoming.name))
            added++
        } else {
            dropped++
        }
    }
    val selected = existingSelectedId?.takeIf { id -> result.any { it.id == id } } ?: result.firstOrNull()?.id
    return ApiProfileImportPlan(result, selected, added, updated, dropped)
}

private fun sameProfileIdentity(left: ApiProfile, right: ApiProfile): Boolean =
    left.name.trim() == right.name.trim() &&
        left.spec.trim().lowercase(Locale.US) == right.spec.trim().lowercase(Locale.US) &&
        left.baseUrl.trim().trimEnd('/') == right.baseUrl.trim().trimEnd('/')

private fun freshProfileId(taken: MutableSet<String>): String {
    var id = UUID.randomUUID().toString()
    while (!taken.add(id)) id = UUID.randomUUID().toString()
    return id
}

private fun uniqueProfileName(existing: List<ApiProfile>, base: String): String {
    val trimmed = base.trim().ifBlank { "方案" }
    if (existing.none { it.name == trimmed }) return trimmed
    var suffix = 1
    while (true) {
        val candidate = if (suffix == 1) trimmed + " (导入)" else trimmed + " (导入 " + suffix + ")"
        if (existing.none { it.name == candidate }) return candidate
        suffix++
    }
}
