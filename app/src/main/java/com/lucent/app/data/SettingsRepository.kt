package com.lucent.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "lucent_settings")

private object SettingsKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val PALETTE = stringPreferencesKey("palette")
    val FONT = stringPreferencesKey("font")
    val FONT_SCALE = floatPreferencesKey("font_scale")
    val LINE_SPACING = floatPreferencesKey("line_spacing")
    val LETTER_SPACING = floatPreferencesKey("letter_spacing")
    val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")

    val CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")

    val API_PROFILES = stringPreferencesKey("api_profiles_json")

    val API_PROFILE_SELECTED = intPreferencesKey("api_profile_selected")

    val BACKGROUND_ANIMATION_ENABLED = booleanPreferencesKey("background_animation_enabled")
}

class SettingsRepository(context: Context) {
    private val context: Context = context.applicationContext

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

    val dynamicColorEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: false }

    data class DisplayPrefs(
        val themeMode: String,
        val palette: String,
        val font: String,
        val fontScale: Float = 1.0f,
        val lineSpacing: Float = 1.2f,
        val letterSpacing: Float = 0.0f
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
                letterSpacing = prefs[SettingsKeys.LETTER_SPACING] ?: 0.0f
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

    val apiProfileSelected: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.API_PROFILE_SELECTED] ?: 0 }

    val backgroundAnimationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true }
    suspend fun setBackgroundAnimationEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] = value }
        SettingsCache.backgroundAnimationEnabled = value
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
        SettingsCache.themeMode = value
    }
    suspend fun setPalette(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.PALETTE] = value }
        SettingsCache.palette = value
    }
    suspend fun setFont(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.FONT] = value }
        SettingsCache.font = value
    }
    suspend fun setFontScale(value: Float) {
        context.settingsDataStore.edit { it[SettingsKeys.FONT_SCALE] = value }
        SettingsCache.fontScale = value
    }
    suspend fun setLineSpacing(value: Float) {
        context.settingsDataStore.edit { it[SettingsKeys.LINE_SPACING] = value }
        SettingsCache.lineSpacing = value
    }
    suspend fun setLetterSpacing(value: Float) {
        context.settingsDataStore.edit { it[SettingsKeys.LETTER_SPACING] = value }
        SettingsCache.letterSpacing = value
    }
    suspend fun resetTypographyDefaults() {
        context.settingsDataStore.edit {
            it[SettingsKeys.FONT_SCALE] = 1.0f
            it[SettingsKeys.LINE_SPACING] = 1.2f
            it[SettingsKeys.LETTER_SPACING] = 0.0f
        }
        SettingsCache.resetTypography()
    }
    suspend fun setDynamicColorEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.DYNAMIC_COLOR_ENABLED] = value }
        SettingsCache.dynamicColor = value
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
            val profiles = ApiProfiles.parseStored(prefs[SettingsKeys.API_PROFILES].orEmpty()) ?: ApiProfiles.DEFAULT_PROFILES
            when (command) {
                is ApiProfileMutation.Add -> {
                    check(profiles.size < ApiProfiles.MAX) { "方案数量已达上限" }
                    check(profiles.none { it.id == command.profile.id }) { "方案ID已存在" }
                }
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
        val safe = profiles.take(ApiProfiles.MAX)
        val idx = if (safe.isEmpty()) 0 else selected.coerceIn(0, safe.size - 1)
        val profilesJson = ApiProfiles.serialize(safe)
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.API_PROFILES] = profilesJson
            prefs[SettingsKeys.API_PROFILE_SELECTED] = idx
        }
        SettingsCache.updateApiProfiles(profilesJson, idx)
    }
}
