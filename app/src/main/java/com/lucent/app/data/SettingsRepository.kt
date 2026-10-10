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

    val BASE_URL_ENC = stringPreferencesKey("base_url_enc")
    val API_SPEC_ENC = stringPreferencesKey("api_spec_enc")
    val MODEL_ENC = stringPreferencesKey("model_enc")
    val CUSTOM_USER_AGENT_ENC = stringPreferencesKey("custom_user_agent_enc")

    val LEGACY_BASE_URL = stringPreferencesKey("base_url")
    val LEGACY_API_SPEC = stringPreferencesKey("api_spec")
    val LEGACY_MODEL = stringPreferencesKey("model")
    val LEGACY_CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")

    val ATTACHMENTS_MIGRATED = booleanPreferencesKey("attachments_migrated_v1")

    val API_KEY_ENC = stringPreferencesKey("api_key_enc")
    val API_PROFILES_ENC = stringPreferencesKey("api_profiles_json_enc")

    val LEGACY_API_KEY = stringPreferencesKey("api_key")
    val LEGACY_API_PROFILES = stringPreferencesKey("api_profiles_json")

    val API_PROFILE_SELECTED = intPreferencesKey("api_profile_selected")

    val NOTEBOOK_OPENS_ENC = stringPreferencesKey("notebook_opens_enc")
    val NOTEBOOK_OPENS_LEGACY = stringPreferencesKey("notebook_opens")

    val MARKDOWN_ENABLED = booleanPreferencesKey("markdown_enabled")

    val RICH_TEXT_ENABLED = booleanPreferencesKey("rich_text_enabled")

    val LINKS_ENABLED = booleanPreferencesKey("links_enabled")

    val BACKGROUND_ANIMATION_ENABLED = booleanPreferencesKey("background_animation_enabled")


    val STARTUP_LOGGING_ENABLED = booleanPreferencesKey("startup_logging_enabled")

    val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")

    val CRASH_SHIELD_ENABLED = booleanPreferencesKey("crash_shield_enabled")

    val OPEN_LINKS_EXTERNALLY = booleanPreferencesKey("open_links_externally")

    val UPDATE_CHANNEL_ENC = stringPreferencesKey("update_channel_enc")
    val LEGACY_UPDATE_CHANNEL = stringPreferencesKey("update_channel")
    val INSTALLED_PREVIEW_IDENTITY = stringPreferencesKey("installed_preview_identity")
    val STAGED_UPDATE_IDENTITY = stringPreferencesKey("staged_update_identity")
    val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
    val PENDING_UPDATE_VERSION = stringPreferencesKey("pending_update_version")
    val STAGED_UPDATE_TAG = stringPreferencesKey("staged_update_tag")
    val STAGED_UPDATE_FILES = stringPreferencesKey("staged_update_files")

    val GLOBAL_TEXT_SELECTION_ENABLED_ENC = stringPreferencesKey("global_text_selection_enabled_enc")
    val LEGACY_GLOBAL_TEXT_SELECTION_ENABLED = stringPreferencesKey("global_text_selection_enabled")

}

const val DEFAULT_ASSISTANT_STYLE = "lively and friendly, relaxed and natural."

class SettingsRepository(context: Context) {
    private val context: Context = context.applicationContext

    private fun secret(
        prefs: androidx.datastore.preferences.core.Preferences,
        encrypted: androidx.datastore.preferences.core.Preferences.Key<String>,
        legacy: androidx.datastore.preferences.core.Preferences.Key<String>,
        default: String
    ): String {
        val stored = prefs[encrypted] ?: prefs[legacy] ?: return default
        return LocalSecrets.decrypt(stored).ifEmpty { default }
    }

    val baseUrl: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.BASE_URL_ENC, SettingsKeys.LEGACY_BASE_URL, "")
    }
    val apiSpec: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.API_SPEC_ENC, SettingsKeys.LEGACY_API_SPEC, "openai")
    }
    val model: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.MODEL_ENC, SettingsKeys.LEGACY_MODEL, "")
    }
    val customUserAgent: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.CUSTOM_USER_AGENT_ENC, SettingsKeys.LEGACY_CUSTOM_USER_AGENT, "")
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
        val startupLoggingEnabled: Boolean,
        val backgroundAnimationEnabled: Boolean = true,
        val dynamicColor: Boolean = false,
        val baseUrl: String = "",
        val apiSpec: String = "openai",
        val apiKey: String = "",
        val model: String = "",
        val customUserAgent: String = "",
        val apiProfilesJson: String = "",
        val apiProfileSelected: Int = 0,
        val openLinksExternally: Boolean = false,
        val markdownEnabled: Boolean = false,
        val richTextEnabled: Boolean = false,
        val linksEnabled: Boolean = false,
        val reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key,

        val globalTextSelectionEnabled: Boolean = false,
        val updateChannel: String = "stable",
        val installedPreviewIdentity: String = "",
        val stagedUpdateIdentity: String = "",
        val autoUpdateEnabled: Boolean = false,
        val pendingUpdateVersion: String = "",
        val stagedUpdateTag: String = "",
        val stagedUpdateFiles: String = ""
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
            startupLoggingEnabled = prefs[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false,
            backgroundAnimationEnabled = prefs[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true,
            dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            baseUrl = secret(prefs, SettingsKeys.BASE_URL_ENC, SettingsKeys.LEGACY_BASE_URL, ""),
            apiSpec = secret(prefs, SettingsKeys.API_SPEC_ENC, SettingsKeys.LEGACY_API_SPEC, "openai"),
            apiKey = LocalSecrets.decrypt(prefs[SettingsKeys.API_KEY_ENC] ?: prefs[SettingsKeys.LEGACY_API_KEY] ?: ""),
            model = secret(prefs, SettingsKeys.MODEL_ENC, SettingsKeys.LEGACY_MODEL, ""),
            customUserAgent = secret(prefs, SettingsKeys.CUSTOM_USER_AGENT_ENC, SettingsKeys.LEGACY_CUSTOM_USER_AGENT, ""),
            apiProfilesJson = LocalSecrets.decrypt(
                prefs[SettingsKeys.API_PROFILES_ENC] ?: prefs[SettingsKeys.LEGACY_API_PROFILES] ?: ""
            ),
            apiProfileSelected = prefs[SettingsKeys.API_PROFILE_SELECTED] ?: 0,
            openLinksExternally = prefs[SettingsKeys.OPEN_LINKS_EXTERNALLY] ?: false,
            markdownEnabled = prefs[SettingsKeys.MARKDOWN_ENABLED] ?: false,
            richTextEnabled = prefs[SettingsKeys.RICH_TEXT_ENABLED] ?: false,
            linksEnabled = prefs[SettingsKeys.LINKS_ENABLED] ?: false,
            reasoning = prefs[SettingsKeys.REASONING_EFFORT] ?: com.lucent.app.data.ReasoningEffort.DEFAULT.key,

            globalTextSelectionEnabled = secret(prefs, SettingsKeys.GLOBAL_TEXT_SELECTION_ENABLED_ENC, SettingsKeys.LEGACY_GLOBAL_TEXT_SELECTION_ENABLED, "false").toBooleanStrictOrNull() ?: false,
            updateChannel = secret(prefs, SettingsKeys.UPDATE_CHANNEL_ENC, SettingsKeys.LEGACY_UPDATE_CHANNEL, "stable"),
            installedPreviewIdentity = prefs[SettingsKeys.INSTALLED_PREVIEW_IDENTITY] ?: "",
            stagedUpdateIdentity = prefs[SettingsKeys.STAGED_UPDATE_IDENTITY] ?: "",
            autoUpdateEnabled = prefs[SettingsKeys.AUTO_UPDATE_ENABLED] ?: false,
            pendingUpdateVersion = prefs[SettingsKeys.PENDING_UPDATE_VERSION] ?: "",
            stagedUpdateTag = prefs[SettingsKeys.STAGED_UPDATE_TAG] ?: "",
            stagedUpdateFiles = prefs[SettingsKeys.STAGED_UPDATE_FILES] ?: ""
        )
    }

    suspend fun crashShieldEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.CRASH_SHIELD_ENABLED] ?: false

    val openLinksExternally: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.OPEN_LINKS_EXTERNALLY] ?: false }
    suspend fun setOpenLinksExternally(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.OPEN_LINKS_EXTERNALLY] = value }
        SettingsCache.openLinksExternally = value
    }

    val reasoning: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.REASONING_EFFORT] ?: com.lucent.app.data.ReasoningEffort.DEFAULT.key
    }

    val apiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[SettingsKeys.API_KEY_ENC] ?: prefs[SettingsKeys.LEGACY_API_KEY] ?: ""
        LocalSecrets.decrypt(stored)
    }

    val apiProfilesJson: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[SettingsKeys.API_PROFILES_ENC] ?: prefs[SettingsKeys.LEGACY_API_PROFILES] ?: ""
        LocalSecrets.decrypt(stored)
    }

    val apiProfileSelected: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.API_PROFILE_SELECTED] ?: 0 }

    val attachmentsMigrated: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.ATTACHMENTS_MIGRATED] ?: false }

    val updateChannel: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.UPDATE_CHANNEL_ENC, SettingsKeys.LEGACY_UPDATE_CHANNEL, "stable")
    }
    val installedPreviewIdentity: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.INSTALLED_PREVIEW_IDENTITY] ?: "" }
    suspend fun setInstalledPreviewIdentity(value: String) {
        SettingsCache.installedPreviewIdentity = value
        context.settingsDataStore.edit { it[SettingsKeys.INSTALLED_PREVIEW_IDENTITY] = value }
    }

    suspend fun setStagedUpdateIdentity(value: String) {
        SettingsCache.stagedUpdateIdentity = value
        context.settingsDataStore.edit { it[SettingsKeys.STAGED_UPDATE_IDENTITY] = value }
    }

    val autoUpdateEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_UPDATE_ENABLED] ?: false }
    val pendingUpdateVersion: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.PENDING_UPDATE_VERSION] ?: ""
    }
    suspend fun setPendingUpdateVersion(value: String) {
        context.settingsDataStore.edit {
            if (value.isBlank()) it.remove(SettingsKeys.PENDING_UPDATE_VERSION)
            else it[SettingsKeys.PENDING_UPDATE_VERSION] = value
        }
    }

    suspend fun setStagedUpdate(tag: String, files: List<String>) {
        context.settingsDataStore.edit {
            if (tag.isBlank()) {
                it.remove(SettingsKeys.STAGED_UPDATE_TAG)
                it.remove(SettingsKeys.STAGED_UPDATE_FILES)
            } else {
                it[SettingsKeys.STAGED_UPDATE_TAG] = tag
                it[SettingsKeys.STAGED_UPDATE_FILES] = files.joinToString(",")
            }
        }
    }

    val markdownEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.MARKDOWN_ENABLED] ?: false }
    val richTextEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.RICH_TEXT_ENABLED] ?: false }

    val linksEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LINKS_ENABLED] ?: false }

    val backgroundAnimationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true }
    suspend fun setBackgroundAnimationEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] = value }
        SettingsCache.backgroundAnimationEnabled = value
    }

    val startupLoggingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false }
    private val removedPreferenceNames: Set<String> = setOf(
        "app_language",
        "app_lock_credentials_enc",
        "app_lock_enabled",
        "app_lock_preblackout",
        "assistant_confirm_tools",
        "assistant_name",
        "assistant_name_enc",
        "assistant_style",
        "assistant_style_enc",
        "auto_backup_state",
        "backup_password_enc",
        "blackout_enabled",
        "cloud_agent_mode",
        "cloud_auto_backup",
        "cloud_enabled",
        "cloud_folder",
        "cloud_password_enc",
        "cloud_provider",
        "cloud_url",
        "cloud_user",
        "custom_templates",
        "embedding_provider",
        "harness_config_enc",
        "hidden_templates",
        "last_screen",
        "memory_tier",
        "model_recents",
        "note_history_enabled",
        "privileged_enabled",
        "pw_attempt_state",
        "pw_first_round_limit",
        "pw_later_round_limit",
        "pw_self_destruct_enabled",
        "pw_self_destruct_threshold",
        "session_snapshot",
        "small_model_mode",
        "template_draft",
        "terminal_font_size",
        "terminal_font_size_enc",
        "terminal_key_bar_visible",
        "terminal_key_bar_visible_enc",
        "typing_haptics",
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

    private suspend fun putSecret(
        encrypted: androidx.datastore.preferences.core.Preferences.Key<String>,
        legacy: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String
    ) {
        val sealed = LocalSecrets.encrypt(value)
        context.settingsDataStore.edit { prefs ->
            prefs[encrypted] = sealed
            prefs.remove(legacy)
        }
    }

    suspend fun setCustomUserAgent(value: String) {
        val trimmed = value.trim()
        SettingsCache.customUserAgent = trimmed
        putSecret(SettingsKeys.CUSTOM_USER_AGENT_ENC, SettingsKeys.LEGACY_CUSTOM_USER_AGENT, trimmed)
    }

    val globalTextSelectionEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.GLOBAL_TEXT_SELECTION_ENABLED_ENC, SettingsKeys.LEGACY_GLOBAL_TEXT_SELECTION_ENABLED, "false").toBooleanStrictOrNull() ?: false
    }

    suspend fun setGlobalTextSelectionEnabled(value: Boolean) {
        SettingsCache.globalTextSelectionEnabled = value
        putSecret(SettingsKeys.GLOBAL_TEXT_SELECTION_ENABLED_ENC, SettingsKeys.LEGACY_GLOBAL_TEXT_SELECTION_ENABLED, value.toString())
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
    suspend fun setAttachmentsMigrated(value: Boolean) { context.settingsDataStore.edit { it[SettingsKeys.ATTACHMENTS_MIGRATED] = value } }

    suspend fun notebookOpensOnce(): String {
        val prefs = context.settingsDataStore.data.first()
        return prefs[SettingsKeys.NOTEBOOK_OPENS_ENC]?.let { LocalSecrets.decrypt(it) } ?: "{}"
    }
    suspend fun setNotebookOpens(value: String) {
        putSecret(SettingsKeys.NOTEBOOK_OPENS_ENC, SettingsKeys.NOTEBOOK_OPENS_LEGACY, value)
    }
    suspend fun setMarkdownEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.MARKDOWN_ENABLED] = value }
        SettingsCache.markdownEnabled = value
    }

    suspend fun setRichTextEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.RICH_TEXT_ENABLED] = value }
        SettingsCache.richTextEnabled = value
    }
    suspend fun setLinksEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.LINKS_ENABLED] = value }
        SettingsCache.linksEnabled = value
    }

    suspend fun saveApiProfiles(profiles: List<ApiProfile>, selected: Int) {
        val safe = profiles.take(ApiProfiles.MAX)
        val idx = if (safe.isEmpty()) 0 else selected.coerceIn(0, safe.size - 1)
        val active = safe.getOrNull(idx)
        val profilesJson = ApiProfiles.serialize(safe)
        val profilesEnc = LocalSecrets.encrypt(profilesJson)
        SettingsCache.apiProfilesJson = profilesJson
        SettingsCache.apiProfileSelected = idx
        SettingsCache.baseUrl = active?.baseUrl ?: ""
        SettingsCache.apiSpec = active?.spec ?: "openai"
        SettingsCache.model = active?.model ?: ""
        active?.apiKey?.let { SettingsCache.apiKey = it }
        val activeKeyEnc = active?.let { LocalSecrets.encrypt(it.apiKey) }
        val activeBaseUrlEnc = LocalSecrets.encrypt(active?.baseUrl ?: "")
        val activeSpecEnc = LocalSecrets.encrypt(active?.spec ?: "openai")
        val activeModelEnc = LocalSecrets.encrypt(active?.model ?: "")

        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.API_PROFILES_ENC] = profilesEnc
            prefs[SettingsKeys.API_PROFILE_SELECTED] = idx
            prefs.remove(SettingsKeys.LEGACY_API_PROFILES)
            if (active != null) {
                prefs[SettingsKeys.BASE_URL_ENC] = activeBaseUrlEnc
                prefs[SettingsKeys.API_SPEC_ENC] = activeSpecEnc
                prefs[SettingsKeys.MODEL_ENC] = activeModelEnc
                if (activeKeyEnc != null) prefs[SettingsKeys.API_KEY_ENC] = activeKeyEnc
                prefs.remove(SettingsKeys.LEGACY_API_KEY)
                prefs.remove(SettingsKeys.LEGACY_BASE_URL)
                prefs.remove(SettingsKeys.LEGACY_API_SPEC)
                prefs.remove(SettingsKeys.LEGACY_MODEL)
                prefs.remove(SettingsKeys.LEGACY_CUSTOM_USER_AGENT)
            } else {
                prefs.remove(SettingsKeys.BASE_URL_ENC)
                prefs.remove(SettingsKeys.MODEL_ENC)
                prefs.remove(SettingsKeys.API_KEY_ENC)
                prefs.remove(SettingsKeys.CUSTOM_USER_AGENT_ENC)
                prefs.remove(SettingsKeys.LEGACY_API_KEY)
                prefs.remove(SettingsKeys.LEGACY_BASE_URL)
                prefs.remove(SettingsKeys.LEGACY_MODEL)
                prefs.remove(SettingsKeys.LEGACY_CUSTOM_USER_AGENT)
            }
        }
    }

    suspend fun clearAll() { context.settingsDataStore.edit { it.clear() } }
}
