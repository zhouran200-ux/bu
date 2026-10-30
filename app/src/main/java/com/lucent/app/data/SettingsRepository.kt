package com.lucent.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

internal object SettingsCorruptionSignal {
    @Volatile
    private var backedUp: Boolean? = null

    fun recordBackup(succeeded: Boolean) {
        backedUp = succeeded
    }

    fun consumeBackup(): Boolean? {
        val current = backedUp
        backedUp = null
        return current
    }
}

internal fun settingsPreferencesFile(filesDir: File): File =
    File(filesDir, "datastore/lucent_settings.preferences_pb")

internal fun backupCorruptedSettingsFile(filesDir: File): Boolean = runCatching {
    val source = settingsPreferencesFile(filesDir)
    if (!source.exists()) return@runCatching false
    val backup = File(filesDir, "datastore/lucent_settings.corrupt")
    backup.parentFile?.mkdirs()
    source.copyTo(backup, overwrite = true)
    backup.exists() && backup.length() == source.length()
}.getOrDefault(false)

internal fun createSettingsDataStore(
    filesDir: File,
    onCorruption: (Boolean) -> Unit
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler<Preferences> {
        onCorruption(backupCorruptedSettingsFile(filesDir))
        emptyPreferences()
    },
    produceFile = { settingsPreferencesFile(filesDir) }
)

private object SettingsStoreHolder {
    @Volatile
    private var store: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> {
        store?.let { return it }
        return synchronized(this) {
            store ?: createSettingsDataStore(context.applicationContext.filesDir) { succeeded ->
                SettingsCorruptionSignal.recordBackup(succeeded)
            }.also { store = it }
        }
    }
}

private val Context.settingsDataStore: DataStore<Preferences>
    get() = SettingsStoreHolder.get(this)

private object SettingsKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val PALETTE = stringPreferencesKey("palette")
    val FONT = stringPreferencesKey("font")
    val FONT_SCALE = floatPreferencesKey("font_scale")
    val LINE_SPACING = floatPreferencesKey("line_spacing")
    val LETTER_SPACING = floatPreferencesKey("letter_spacing")
    val PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
    val TYPOGRAPHY_SCOPE = stringPreferencesKey("typography_scope")
    val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")

    val CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")

    val API_PROFILES = stringPreferencesKey("api_profiles_json")

    val API_PROFILE_SELECTED = intPreferencesKey("api_profile_selected")

    val BACKGROUND_ANIMATION_ENABLED = booleanPreferencesKey("background_animation_enabled")
}

class SettingsRepository(context: Context) {
    private val context: Context = context.applicationContext

    internal val recoveredCorruptSettings: Boolean
        get() = SettingsCorruptionSignal.consumeBackup() == true

    private fun setting(
        prefs: androidx.datastore.preferences.core.Preferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        default: String
    ): String = prefs[key]?.ifEmpty { default } ?: default

    val customUserAgent: Flow<String> = context.settingsDataStore.data.map {
        setting(it, SettingsKeys.CUSTOM_USER_AGENT, "")
    }

    val themeMode: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.THEME_MODE] ?: "system" }
    val palette: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.PALETTE] ?: "CYCLE" }
    val font: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.FONT] ?: "system" }
    val fontScale: Flow<Float> = context.settingsDataStore.data.map { it[SettingsKeys.FONT_SCALE] ?: 1.0f }
    val lineSpacing: Flow<Float> = context.settingsDataStore.data.map { it[SettingsKeys.LINE_SPACING] ?: 1.2f }
    val letterSpacing: Flow<Float> = context.settingsDataStore.data.map { it[SettingsKeys.LETTER_SPACING] ?: 0.0f }
    val paragraphSpacing: Flow<Float> = context.settingsDataStore.data.map { it[SettingsKeys.PARAGRAPH_SPACING] ?: 1.0f }
    val typographyScope: Flow<String> = context.settingsDataStore.data.map {
        TypographyScopes.normalize(it[SettingsKeys.TYPOGRAPHY_SCOPE])
    }

    val dynamicColorEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: false }

    data class DisplayPrefs(
        val themeMode: String,
        val palette: String,
        val font: String,
        val fontScale: Float = 1.0f,
        val lineSpacing: Float = 1.2f,
        val letterSpacing: Float = 0.0f,
        val paragraphSpacing: Float = 1.0f,
        val typographyScope: String = TypographyScopes.GLOBAL
    )

    data class StartupPrefs(
        val display: DisplayPrefs,
        val backgroundAnimationEnabled: Boolean = true,
        val dynamicColor: Boolean = false,
        val customUserAgent: String = "",
        val apiProfilesJson: String = "",
        val apiProfileSelected: Int = 0
    )

    suspend fun startupPrefsOnce(): StartupPrefs {
        val prefs = context.settingsDataStore.data.first()
        return StartupPrefs(
            display = DisplayPrefs(
                themeMode = prefs[SettingsKeys.THEME_MODE] ?: "system",
                palette = prefs[SettingsKeys.PALETTE] ?: "CYCLE",
                font = prefs[SettingsKeys.FONT] ?: "system",
                fontScale = prefs[SettingsKeys.FONT_SCALE] ?: 1.0f,
                lineSpacing = prefs[SettingsKeys.LINE_SPACING] ?: 1.2f,
                letterSpacing = prefs[SettingsKeys.LETTER_SPACING] ?: 0.0f,
                paragraphSpacing = prefs[SettingsKeys.PARAGRAPH_SPACING] ?: 1.0f,
                typographyScope = TypographyScopes.normalize(prefs[SettingsKeys.TYPOGRAPHY_SCOPE])
            ),
            backgroundAnimationEnabled = prefs[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true,
            dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            customUserAgent = setting(prefs, SettingsKeys.CUSTOM_USER_AGENT, ""),
            apiProfilesJson = setting(prefs, SettingsKeys.API_PROFILES, ""),
            apiProfileSelected = prefs[SettingsKeys.API_PROFILE_SELECTED] ?: 0
        )
    }

    internal data class ApiProfilePreferences(val json: String, val selectedIndex: Int)

    internal val apiProfilePreferences: Flow<ApiProfilePreferences> = context.settingsDataStore.data.map { prefs ->
        ApiProfilePreferences(prefs[SettingsKeys.API_PROFILES].orEmpty(), prefs[SettingsKeys.API_PROFILE_SELECTED] ?: 0)
    }

    val apiProfilesJson: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.API_PROFILES] ?: ""
    }

    val backgroundAnimationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true }
    suspend fun setBackgroundAnimationEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] = value }
    }

    private val removedPreferenceNames: Set<String> = setOf(
        "api_key",
        "api_key_enc",
        "api_profiles_json_enc",
        "api_spec",
        "api_spec_enc",
        "app_language",
        "app_lock_credentials_enc",
        "app_lock_enabled",
        "app_lock_preblackout",
        "assistant_confirm_tools",
        "assistant_name",
        "assistant_name_enc",
        "assistant_style",
        "assistant_style_enc",
        "attachments_migrated_v1",
        "auto_backup_state",
        "auto_update_enabled",
        "backup_password_enc",
        "base_url",
        "base_url_enc",
        "blackout_enabled",
        "cloud_agent_mode",
        "cloud_auto_backup",
        "cloud_enabled",
        "cloud_folder",
        "cloud_password_enc",
        "cloud_provider",
        "cloud_url",
        "cloud_user",
        "crash_shield_enabled",
        "custom_templates",
        "custom_user_agent_enc",
        "embedding_provider",
        "global_text_selection_enabled",
        "global_text_selection_enabled_enc",
        "harness_config_enc",
        "hidden_templates",
        "installed_preview_identity",
        "last_screen",
        "links_enabled",
        "markdown_enabled",
        "memory_tier",
        "model",
        "model_enc",
        "model_recents",
        "note_history_enabled",
        "notebook_opens",
        "notebook_opens_enc",
        "open_links_externally",
        "pending_update_version",
        "privileged_enabled",
        "pw_attempt_state",
        "pw_first_round_limit",
        "pw_later_round_limit",
        "pw_self_destruct_enabled",
        "pw_self_destruct_threshold",
        "reasoning_effort",
        "rich_text_enabled",
        "session_snapshot",
        "small_model_mode",
        "staged_update_files",
        "staged_update_identity",
        "staged_update_tag",
        "startup_logging_enabled",
        "template_draft",
        "terminal_font_size",
        "terminal_font_size_enc",
        "terminal_key_bar_visible",
        "terminal_key_bar_visible_enc",
        "typing_haptics",
        "update_channel",
        "update_channel_enc",
    )

    suspend fun dropRemovedFeatureKeys() {
        val current = context.settingsDataStore.data.first()
        if (current.asMap().keys.none { it.name in removedPreferenceNames }) return
        context.settingsDataStore.edit { store ->
            removedPreferenceNames.forEach { name ->
                store.remove(stringPreferencesKey(name))
                store.remove(booleanPreferencesKey(name))
                store.remove(intPreferencesKey(name))
                store.remove(floatPreferencesKey(name))
            }
        }
    }

    suspend fun setCustomUserAgent(value: String) {
        val trimmed = value.trim()
        context.settingsDataStore.edit { it[SettingsKeys.CUSTOM_USER_AGENT] = trimmed }
        SettingsCache.customUserAgent = trimmed
    }

    suspend fun setThemeMode(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.THEME_MODE] = value }
    }
    suspend fun setPalette(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.PALETTE] = value }
    }
    suspend fun setFont(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.FONT] = value }
    }
    suspend fun setFontScale(value: Float) {
        context.settingsDataStore.edit { it[SettingsKeys.FONT_SCALE] = value }
    }
    suspend fun setLineSpacing(value: Float) {
        context.settingsDataStore.edit { it[SettingsKeys.LINE_SPACING] = value }
    }
    suspend fun setLetterSpacing(value: Float) {
        context.settingsDataStore.edit { it[SettingsKeys.LETTER_SPACING] = value }
    }
    suspend fun setParagraphSpacing(value: Float) {
        context.settingsDataStore.edit { it[SettingsKeys.PARAGRAPH_SPACING] = value }
    }
    suspend fun setTypographyScope(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.TYPOGRAPHY_SCOPE] = TypographyScopes.normalize(value) }
    }
    suspend fun resetTypographyDefaults() {
        context.settingsDataStore.edit {
            it[SettingsKeys.FONT_SCALE] = 1.0f
            it[SettingsKeys.LINE_SPACING] = 1.2f
            it[SettingsKeys.LETTER_SPACING] = 0.0f
            it[SettingsKeys.PARAGRAPH_SPACING] = 1.0f
        }
    }
    suspend fun setDynamicColorEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.DYNAMIC_COLOR_ENABLED] = value }
    }

    internal suspend fun ensureApiProfilesInitialized() {
        val committed = context.settingsDataStore.edit { prefs ->
            if (prefs[SettingsKeys.API_PROFILES].isNullOrBlank()) {
                prefs[SettingsKeys.API_PROFILES] = ApiProfiles.serialize(ApiProfiles.DEFAULT_PROFILES)
                prefs[SettingsKeys.API_PROFILE_SELECTED] = 0
            }
        }
        SettingsCache.updateApiProfiles(committed[SettingsKeys.API_PROFILES].orEmpty(), committed[SettingsKeys.API_PROFILE_SELECTED] ?: 0)
    }

    internal suspend fun mutateApiProfiles(command: ApiProfileMutation) {
        val committed = context.settingsDataStore.edit { prefs ->
            val stored = prefs[SettingsKeys.API_PROFILES].orEmpty()
            val profiles = ApiProfiles.parseStored(stored)
                ?: if (stored.isBlank()) {
                    ApiProfiles.DEFAULT_PROFILES
                } else {
                    error("API 配置无法解析，已阻止覆盖写入")
                }
            when (command) {
                is ApiProfileMutation.Add -> check(profiles.none { it.id == command.profile.id }) { "方案ID已存在" }
                is ApiProfileMutation.Update -> check(profiles.any { it.id == command.profile.id }) { "方案已删除，无法保存" }
                is ApiProfileMutation.Select -> check(profiles.any { it.id == command.id }) { "方案已删除，无法切换" }
                else -> Unit
            }
            val selected = prefs[SettingsKeys.API_PROFILE_SELECTED] ?: 0
            val next = ApiProfileSnapshot(profiles, profiles.getOrNull(selected)?.id).mutate(command)
            prefs[SettingsKeys.API_PROFILES] = ApiProfiles.serialize(next.profiles)
            prefs[SettingsKeys.API_PROFILE_SELECTED] = next.selectedIndex
        }
        SettingsCache.updateApiProfiles(committed[SettingsKeys.API_PROFILES].orEmpty(), committed[SettingsKeys.API_PROFILE_SELECTED] ?: 0)
    }

    suspend fun saveApiProfiles(profiles: List<ApiProfile>, selected: Int) {
        val idx = if (profiles.isEmpty()) 0 else selected.coerceIn(0, profiles.size - 1)
        val profilesJson = ApiProfiles.serialize(profiles)
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.API_PROFILES] = profilesJson
            prefs[SettingsKeys.API_PROFILE_SELECTED] = idx
        }
        SettingsCache.updateApiProfiles(profilesJson, idx)
    }
}
