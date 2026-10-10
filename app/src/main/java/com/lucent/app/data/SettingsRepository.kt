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

    val BASE_URL = stringPreferencesKey("base_url")
    val API_SPEC = stringPreferencesKey("api_spec")
    val MODEL = stringPreferencesKey("model")
    val CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")

    val API_KEY = stringPreferencesKey("api_key")
    val API_PROFILES = stringPreferencesKey("api_profiles_json")

    val API_PROFILE_SELECTED = intPreferencesKey("api_profile_selected")

    val NOTEBOOK_OPENS = stringPreferencesKey("notebook_opens")



    val BACKGROUND_ANIMATION_ENABLED = booleanPreferencesKey("background_animation_enabled")



    val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")
}

const val DEFAULT_ASSISTANT_STYLE = "lively and friendly, relaxed and natural."

class SettingsRepository(context: Context) {
    private val context: Context = context.applicationContext

    private fun setting(
        prefs: androidx.datastore.preferences.core.Preferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        default: String
    ): String = prefs[key]?.ifEmpty { default } ?: default

    val baseUrl: Flow<String> = context.settingsDataStore.data.map {
        setting(it, SettingsKeys.BASE_URL, "")
    }
    val apiSpec: Flow<String> = context.settingsDataStore.data.map {
        setting(it, SettingsKeys.API_SPEC, "openai")
    }
    val model: Flow<String> = context.settingsDataStore.data.map {
        setting(it, SettingsKeys.MODEL, "")
    }
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
        val baseUrl: String = "",
        val apiSpec: String = "openai",
        val apiKey: String = "",
        val model: String = "",
        val customUserAgent: String = "",
        val apiProfilesJson: String = "",
        val apiProfileSelected: Int = 0,
        val reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key
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
            baseUrl = setting(prefs, SettingsKeys.BASE_URL, ""),
            apiSpec = setting(prefs, SettingsKeys.API_SPEC, "openai"),
            apiKey = setting(prefs, SettingsKeys.API_KEY, ""),
            model = setting(prefs, SettingsKeys.MODEL, ""),
            customUserAgent = setting(prefs, SettingsKeys.CUSTOM_USER_AGENT, ""),
            apiProfilesJson = setting(prefs, SettingsKeys.API_PROFILES, ""),
            apiProfileSelected = prefs[SettingsKeys.API_PROFILE_SELECTED] ?: 0,
            reasoning = prefs[SettingsKeys.REASONING_EFFORT] ?: com.lucent.app.data.ReasoningEffort.DEFAULT.key,
        )
    }

    val reasoning: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.REASONING_EFFORT] ?: com.lucent.app.data.ReasoningEffort.DEFAULT.key
    }

    val apiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.API_KEY] ?: ""
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
        "api_key_enc",
        "api_profiles_json_enc",
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
        "model_enc",
        "model_recents",
        "note_history_enabled",
        "notebook_opens_enc",
        "open_links_externally",
        "pending_update_version",
        "privileged_enabled",
        "pw_attempt_state",
        "pw_first_round_limit",
        "pw_later_round_limit",
        "pw_self_destruct_enabled",
        "pw_self_destruct_threshold",
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
        SettingsCache.customUserAgent = trimmed
        context.settingsDataStore.edit { it[SettingsKeys.CUSTOM_USER_AGENT] = trimmed }
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
        SettingsCache.fontScale = 1.0f
        SettingsCache.lineSpacing = 1.2f
        SettingsCache.letterSpacing = 0.0f
    }
    suspend fun setDynamicColorEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.DYNAMIC_COLOR_ENABLED] = value }
        SettingsCache.dynamicColor = value
    }

    suspend fun notebookOpensOnce(): String {
        val prefs = context.settingsDataStore.data.first()
        return prefs[SettingsKeys.NOTEBOOK_OPENS] ?: "{}"
    }
    suspend fun setNotebookOpens(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.NOTEBOOK_OPENS] = value }
    }

    suspend fun saveApiProfiles(profiles: List<ApiProfile>, selected: Int) {
        val safe = profiles.take(ApiProfiles.MAX)
        val idx = if (safe.isEmpty()) 0 else selected.coerceIn(0, safe.size - 1)
        val active = safe.getOrNull(idx)
        val profilesJson = ApiProfiles.serialize(safe)
        SettingsCache.apiProfilesJson = profilesJson
        SettingsCache.apiProfileSelected = idx
        SettingsCache.baseUrl = active?.baseUrl ?: ""
        SettingsCache.apiSpec = active?.spec ?: "openai"
        SettingsCache.model = active?.model ?: ""
        active?.apiKey?.let { SettingsCache.apiKey = it }

        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.API_PROFILES] = profilesJson
            prefs[SettingsKeys.API_PROFILE_SELECTED] = idx
            if (active != null) {
                prefs[SettingsKeys.BASE_URL] = active.baseUrl
                prefs[SettingsKeys.API_SPEC] = active.spec
                prefs[SettingsKeys.MODEL] = active.model
                prefs[SettingsKeys.API_KEY] = active.apiKey
            } else {
                prefs.remove(SettingsKeys.BASE_URL)
                prefs.remove(SettingsKeys.MODEL)
                prefs.remove(SettingsKeys.API_KEY)
                prefs.remove(SettingsKeys.CUSTOM_USER_AGENT)
            }
        }
    }
}
