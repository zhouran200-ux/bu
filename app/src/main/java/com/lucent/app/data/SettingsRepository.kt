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
    val ASSISTANT_NAME_ENC = stringPreferencesKey("assistant_name_enc")
    val ASSISTANT_STYLE_ENC = stringPreferencesKey("assistant_style_enc")

    val LEGACY_BASE_URL = stringPreferencesKey("base_url")
    val LEGACY_API_SPEC = stringPreferencesKey("api_spec")
    val LEGACY_MODEL = stringPreferencesKey("model")
    val LEGACY_CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")
    val LEGACY_ASSISTANT_NAME = stringPreferencesKey("assistant_name")
    val LEGACY_ASSISTANT_STYLE = stringPreferencesKey("assistant_style")

    val ATTACHMENTS_MIGRATED = booleanPreferencesKey("attachments_migrated_v1")

    val API_KEY_ENC = stringPreferencesKey("api_key_enc")
    val API_PROFILES_ENC = stringPreferencesKey("api_profiles_json_enc")

    val BACKUP_PASSWORD_ENC = stringPreferencesKey("backup_password_enc")

    val LEGACY_API_KEY = stringPreferencesKey("api_key")
    val LEGACY_API_PROFILES = stringPreferencesKey("api_profiles_json")

    val API_PROFILE_SELECTED = intPreferencesKey("api_profile_selected")

    val SESSION_SNAPSHOT = stringPreferencesKey("session_snapshot")
    val NOTEBOOK_OPENS_ENC = stringPreferencesKey("notebook_opens_enc")
    val NOTEBOOK_OPENS_LEGACY = stringPreferencesKey("notebook_opens")

    val AUTO_BACKUP = stringPreferencesKey("auto_backup_state")

    val NOTE_HISTORY_ENABLED = booleanPreferencesKey("note_history_enabled")

    val ASSISTANT_CONFIRM_TOOLS = booleanPreferencesKey("assistant_confirm_tools")
    val TYPING_HAPTICS = booleanPreferencesKey("typing_haptics")

    val MARKDOWN_ENABLED = booleanPreferencesKey("markdown_enabled")

    val RICH_TEXT_ENABLED = booleanPreferencesKey("rich_text_enabled")
    val CUSTOM_TEMPLATES = stringPreferencesKey("custom_templates")
    val TEMPLATE_DRAFT = stringPreferencesKey("template_draft")
    val HIDDEN_TEMPLATES = stringPreferencesKey("hidden_templates")
    val CLOUD_ENABLED = booleanPreferencesKey("cloud_enabled")
    val CLOUD_PROVIDER = stringPreferencesKey("cloud_provider")
    val CLOUD_URL = stringPreferencesKey("cloud_url")
    val CLOUD_USER = stringPreferencesKey("cloud_user")
    val CLOUD_PASSWORD_ENC = stringPreferencesKey("cloud_password_enc")
    val EMBEDDING_PROVIDER = stringPreferencesKey("embedding_provider")
    val CLOUD_FOLDER = stringPreferencesKey("cloud_folder")
    val CLOUD_AUTO_BACKUP = booleanPreferencesKey("cloud_auto_backup")

    val LINKS_ENABLED = booleanPreferencesKey("links_enabled")

    val BACKGROUND_ANIMATION_ENABLED = booleanPreferencesKey("background_animation_enabled")

    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val APP_LOCK_CREDENTIALS_ENC = stringPreferencesKey("app_lock_credentials_enc")

    val STARTUP_LOGGING_ENABLED = booleanPreferencesKey("startup_logging_enabled")

    val APP_LANGUAGE = stringPreferencesKey("app_language")

    val AGENT_MODE = booleanPreferencesKey("cloud_agent_mode")

    val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")

    val MODEL_RECENTS = stringPreferencesKey("model_recents")

    val SMALL_MODEL_MODE = booleanPreferencesKey("small_model_mode")

    val LAST_SCREEN = stringPreferencesKey("last_screen")

    val BLACKOUT_ENABLED = booleanPreferencesKey("blackout_enabled")
    val APP_LOCK_PREBLACKOUT = booleanPreferencesKey("app_lock_preblackout")

    val CRASH_SHIELD_ENABLED = booleanPreferencesKey("crash_shield_enabled")

    val PW_ATTEMPT_STATE = stringPreferencesKey("pw_attempt_state")
    val PW_FIRST_ROUND_LIMIT = intPreferencesKey("pw_first_round_limit")
    val PW_LATER_ROUND_LIMIT = intPreferencesKey("pw_later_round_limit")
    val PW_SELF_DESTRUCT_ENABLED = booleanPreferencesKey("pw_self_destruct_enabled")
    val PW_SELF_DESTRUCT_THRESHOLD = intPreferencesKey("pw_self_destruct_threshold")

    val OPEN_LINKS_EXTERNALLY = booleanPreferencesKey("open_links_externally")

    val UPDATE_CHANNEL_ENC = stringPreferencesKey("update_channel_enc")
    val LEGACY_UPDATE_CHANNEL = stringPreferencesKey("update_channel")
    val INSTALLED_PREVIEW_IDENTITY = stringPreferencesKey("installed_preview_identity")
    val STAGED_UPDATE_IDENTITY = stringPreferencesKey("staged_update_identity")
    val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
    val PENDING_UPDATE_VERSION = stringPreferencesKey("pending_update_version")
    val STAGED_UPDATE_TAG = stringPreferencesKey("staged_update_tag")
    val STAGED_UPDATE_FILES = stringPreferencesKey("staged_update_files")

    val PRIVILEGED_ENABLED = booleanPreferencesKey("privileged_enabled")

    val HARNESS_CONFIG_ENC = stringPreferencesKey("harness_config_enc")

    val TERMINAL_FONT_SIZE_ENC = stringPreferencesKey("terminal_font_size_enc")
    val LEGACY_TERMINAL_FONT_SIZE = stringPreferencesKey("terminal_font_size")
    val TERMINAL_KEY_BAR_VISIBLE_ENC = stringPreferencesKey("terminal_key_bar_visible_enc")
    val LEGACY_TERMINAL_KEY_BAR_VISIBLE = stringPreferencesKey("terminal_key_bar_visible")

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
    val assistantName: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.ASSISTANT_NAME_ENC, SettingsKeys.LEGACY_ASSISTANT_NAME, "Lucent")
    }
    val assistantStyle: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.ASSISTANT_STYLE_ENC, SettingsKeys.LEGACY_ASSISTANT_STYLE, "")
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

    suspend fun displayPrefsOnce(): DisplayPrefs {
        val prefs = context.settingsDataStore.data.first()
        return DisplayPrefs(
            themeMode = prefs[SettingsKeys.THEME_MODE] ?: "system",
            palette = prefs[SettingsKeys.PALETTE] ?: "CYCLE",
            font = prefs[SettingsKeys.FONT] ?: "system",
            fontScale = prefs[SettingsKeys.FONT_SCALE] ?: 1.0f,
            lineSpacing = prefs[SettingsKeys.LINE_SPACING] ?: 1.2f,
            letterSpacing = prefs[SettingsKeys.LETTER_SPACING] ?: 0.0f
        )
    }

    data class StartupPrefs(
        val display: DisplayPrefs,
        val appLockEnabled: Boolean,
        val startupLoggingEnabled: Boolean,
        val appLanguage: String = "zh",
        val assistantName: String = "Lucent",
        val backgroundAnimationEnabled: Boolean = true,
        val dynamicColor: Boolean = false,
        val sessionSnapshot: String = "",
        val assistantStyle: String = "",
        val baseUrl: String = "",
        val apiSpec: String = "openai",
        val apiKey: String = "",
        val model: String = "",
        val customUserAgent: String = "",
        val apiProfilesJson: String = "",
        val apiProfileSelected: Int = 0,
        val noteHistoryEnabled: Boolean = true,
        val crashShieldEnabled: Boolean = false,
        val blackoutEnabled: Boolean = false,
        val pwSelfDestructEnabled: Boolean = false,
        val pwFirstRoundLimit: Int = 5,
        val pwLaterRoundLimit: Int = 3,
        val pwSelfDestructThreshold: Int = 25,
        val passwordAttemptState: String = "",
        val closeToTray: Boolean = true,
        val openLinksExternally: Boolean = false,
        val markdownEnabled: Boolean = false,
        val richTextEnabled: Boolean = false,
        val linksEnabled: Boolean = false,
        val assistantConfirmToolsEnabled: Boolean = true,
        val agentMode: Boolean = true,
        val reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key,
        val smallModelModeEnabled: Boolean = false,
        val embeddingProvider: String = "local",
        val cloudEnabled: Boolean = false,
        val cloudProvider: String = "Nutstore",
        val cloudUrl: String = "",
        val cloudUser: String = "",
        val cloudFolder: String = "Lucent",
        val cloudAutoBackup: Boolean = false,

        val terminalFontSize: Float? = null,
        val terminalKeyBarVisible: Boolean = true,
        val globalTextSelectionEnabled: Boolean = false,
        val cloudPasswordEnc: String = "",
        val updateChannel: String = "stable",
        val installedPreviewIdentity: String = "",
        val stagedUpdateIdentity: String = "",
        val autoUpdateEnabled: Boolean = false,
        val privilegedEnabled: Boolean = false,
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
            appLockEnabled = prefs[SettingsKeys.APP_LOCK_ENABLED] ?: false,
            startupLoggingEnabled = prefs[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false,
            appLanguage = "zh",
            assistantName = secret(prefs, SettingsKeys.ASSISTANT_NAME_ENC, SettingsKeys.LEGACY_ASSISTANT_NAME, "Lucent"),
            backgroundAnimationEnabled = prefs[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true,
            dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            sessionSnapshot = prefs[SettingsKeys.SESSION_SNAPSHOT] ?: "",
            assistantStyle = secret(prefs, SettingsKeys.ASSISTANT_STYLE_ENC, SettingsKeys.LEGACY_ASSISTANT_STYLE, ""),
            baseUrl = secret(prefs, SettingsKeys.BASE_URL_ENC, SettingsKeys.LEGACY_BASE_URL, ""),
            apiSpec = secret(prefs, SettingsKeys.API_SPEC_ENC, SettingsKeys.LEGACY_API_SPEC, "openai"),
            apiKey = LocalSecrets.decrypt(prefs[SettingsKeys.API_KEY_ENC] ?: prefs[SettingsKeys.LEGACY_API_KEY] ?: ""),
            model = secret(prefs, SettingsKeys.MODEL_ENC, SettingsKeys.LEGACY_MODEL, ""),
            customUserAgent = secret(prefs, SettingsKeys.CUSTOM_USER_AGENT_ENC, SettingsKeys.LEGACY_CUSTOM_USER_AGENT, ""),
            apiProfilesJson = LocalSecrets.decrypt(
                prefs[SettingsKeys.API_PROFILES_ENC] ?: prefs[SettingsKeys.LEGACY_API_PROFILES] ?: ""
            ),
            apiProfileSelected = prefs[SettingsKeys.API_PROFILE_SELECTED] ?: 0,
            noteHistoryEnabled = prefs[SettingsKeys.NOTE_HISTORY_ENABLED] ?: true,
            crashShieldEnabled = prefs[SettingsKeys.CRASH_SHIELD_ENABLED] ?: false,
            blackoutEnabled = prefs[SettingsKeys.BLACKOUT_ENABLED] ?: false,
            pwSelfDestructEnabled = prefs[SettingsKeys.PW_SELF_DESTRUCT_ENABLED] ?: false,
            pwFirstRoundLimit = prefs[SettingsKeys.PW_FIRST_ROUND_LIMIT] ?: 5,
            pwLaterRoundLimit = prefs[SettingsKeys.PW_LATER_ROUND_LIMIT] ?: 3,
            pwSelfDestructThreshold = prefs[SettingsKeys.PW_SELF_DESTRUCT_THRESHOLD] ?: 25,
            passwordAttemptState = prefs[SettingsKeys.PW_ATTEMPT_STATE] ?: "",
            closeToTray = true,
            openLinksExternally = prefs[SettingsKeys.OPEN_LINKS_EXTERNALLY] ?: false,
            markdownEnabled = prefs[SettingsKeys.MARKDOWN_ENABLED] ?: false,
            richTextEnabled = prefs[SettingsKeys.RICH_TEXT_ENABLED] ?: false,
            linksEnabled = prefs[SettingsKeys.LINKS_ENABLED] ?: false,
            assistantConfirmToolsEnabled = prefs[SettingsKeys.ASSISTANT_CONFIRM_TOOLS] ?: true,
            agentMode = prefs[SettingsKeys.AGENT_MODE] ?: true,
            reasoning = prefs[SettingsKeys.REASONING_EFFORT] ?: com.lucent.app.data.ReasoningEffort.DEFAULT.key,
            smallModelModeEnabled = prefs[SettingsKeys.SMALL_MODEL_MODE] ?: false,
            embeddingProvider = prefs[SettingsKeys.EMBEDDING_PROVIDER] ?: "local",
            cloudEnabled = prefs[SettingsKeys.CLOUD_ENABLED] ?: false,
            cloudProvider = prefs[SettingsKeys.CLOUD_PROVIDER] ?: "Nutstore",
            cloudUrl = prefs[SettingsKeys.CLOUD_URL] ?: "",
            cloudUser = prefs[SettingsKeys.CLOUD_USER] ?: "",
            cloudFolder = prefs[SettingsKeys.CLOUD_FOLDER] ?: "Lucent",
            cloudAutoBackup = prefs[SettingsKeys.CLOUD_AUTO_BACKUP] ?: false,

            terminalFontSize = secret(prefs, SettingsKeys.TERMINAL_FONT_SIZE_ENC, SettingsKeys.LEGACY_TERMINAL_FONT_SIZE, "").toFloatOrNull(),
            terminalKeyBarVisible = secret(prefs, SettingsKeys.TERMINAL_KEY_BAR_VISIBLE_ENC, SettingsKeys.LEGACY_TERMINAL_KEY_BAR_VISIBLE, "true").toBooleanStrictOrNull() ?: true,
            globalTextSelectionEnabled = secret(prefs, SettingsKeys.GLOBAL_TEXT_SELECTION_ENABLED_ENC, SettingsKeys.LEGACY_GLOBAL_TEXT_SELECTION_ENABLED, "false").toBooleanStrictOrNull() ?: false,
            cloudPasswordEnc = prefs[SettingsKeys.CLOUD_PASSWORD_ENC] ?: "",
            updateChannel = secret(prefs, SettingsKeys.UPDATE_CHANNEL_ENC, SettingsKeys.LEGACY_UPDATE_CHANNEL, "stable"),
            installedPreviewIdentity = prefs[SettingsKeys.INSTALLED_PREVIEW_IDENTITY] ?: "",
            stagedUpdateIdentity = prefs[SettingsKeys.STAGED_UPDATE_IDENTITY] ?: "",
            autoUpdateEnabled = prefs[SettingsKeys.AUTO_UPDATE_ENABLED] ?: false,
            privilegedEnabled = prefs[SettingsKeys.PRIVILEGED_ENABLED] ?: false,
            pendingUpdateVersion = prefs[SettingsKeys.PENDING_UPDATE_VERSION] ?: "",
            stagedUpdateTag = prefs[SettingsKeys.STAGED_UPDATE_TAG] ?: "",
            stagedUpdateFiles = prefs[SettingsKeys.STAGED_UPDATE_FILES] ?: ""
        )
    }


    val lastScreen: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.LAST_SCREEN] ?: "" }
    suspend fun lastScreenOnce(): String = context.settingsDataStore.data.first()[SettingsKeys.LAST_SCREEN] ?: ""
    suspend fun setLastScreen(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.LAST_SCREEN] = value }
    }

    val blackoutEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.BLACKOUT_ENABLED] ?: false }
    suspend fun blackoutEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.BLACKOUT_ENABLED] ?: false

    suspend fun setBlackoutEnabled(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val wasEnabled = prefs[SettingsKeys.BLACKOUT_ENABLED] ?: false
            prefs[SettingsKeys.BLACKOUT_ENABLED] = value
            if (value) {
                if (!wasEnabled) {
                    prefs[SettingsKeys.APP_LOCK_PREBLACKOUT] = prefs[SettingsKeys.APP_LOCK_ENABLED] ?: false
                }
            } else {
                prefs.remove(SettingsKeys.APP_LOCK_PREBLACKOUT)
            }
        }
        SettingsCache.blackoutEnabled = value
    }

    suspend fun appLockWasOnBeforeBlackout(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.APP_LOCK_PREBLACKOUT] ?: false

    val crashShieldEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.CRASH_SHIELD_ENABLED] ?: false }
    suspend fun crashShieldEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.CRASH_SHIELD_ENABLED] ?: false

    suspend fun setCrashShieldEnabled(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.CRASH_SHIELD_ENABLED] = value
            if (value) prefs[SettingsKeys.STARTUP_LOGGING_ENABLED] = true
        }
        SettingsCache.crashShieldEnabled = value
    }

    val passwordAttemptState: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.PW_ATTEMPT_STATE] ?: "" }
    suspend fun passwordAttemptStateOnce(): String =
        context.settingsDataStore.data.first()[SettingsKeys.PW_ATTEMPT_STATE] ?: ""
    suspend fun setPasswordAttemptState(json: String) {
        SettingsCache.passwordAttemptState = json
        context.settingsDataStore.edit { it[SettingsKeys.PW_ATTEMPT_STATE] = json }
    }

    val pwFirstRoundLimit: Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsKeys.PW_FIRST_ROUND_LIMIT] ?: 5
    }
    val pwLaterRoundLimit: Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsKeys.PW_LATER_ROUND_LIMIT] ?: 3
    }
    suspend fun setPwFirstRoundLimit(value: Int) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PW_FIRST_ROUND_LIMIT] = value.coerceIn(1..10)
        }
        SettingsCache.pwFirstRoundLimit = value.coerceIn(1..10)
    }
    suspend fun setPwLaterRoundLimit(value: Int) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PW_LATER_ROUND_LIMIT] = value.coerceIn(1..10)
        }
        SettingsCache.pwLaterRoundLimit = value.coerceIn(1..10)
    }

    val pwSelfDestructEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.PW_SELF_DESTRUCT_ENABLED] ?: false }
    val pwSelfDestructThreshold: Flow<Int> = context.settingsDataStore.data.map {
        it[SettingsKeys.PW_SELF_DESTRUCT_THRESHOLD] ?: 25
    }
    suspend fun setPwSelfDestructEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.PW_SELF_DESTRUCT_ENABLED] = value }
        SettingsCache.pwSelfDestructEnabled = value
    }
    suspend fun setPwSelfDestructThreshold(value: Int) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PW_SELF_DESTRUCT_THRESHOLD] = value.coerceIn(10..200)
        }
        SettingsCache.pwSelfDestructThreshold = value.coerceIn(10..200)
    }

    val openLinksExternally: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.OPEN_LINKS_EXTERNALLY] ?: false }
    suspend fun setOpenLinksExternally(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.OPEN_LINKS_EXTERNALLY] = value }
        SettingsCache.openLinksExternally = value
    }

    val appLanguage: Flow<String> = kotlinx.coroutines.flow.flowOf("zh")
    suspend fun setAppLanguage(value: String) {}
    suspend fun appLanguageOnce(): String = "zh"

    val agentMode: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AGENT_MODE] ?: true }
    suspend fun setAgentMode(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.AGENT_MODE] = value }
        SettingsCache.agentMode = value
    }

    val reasoning: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.REASONING_EFFORT] ?: com.lucent.app.data.ReasoningEffort.DEFAULT.key
    }
    suspend fun setReasoning(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.REASONING_EFFORT] = value }
        SettingsCache.reasoning = value
    }


    val smallModelModeEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.SMALL_MODEL_MODE] ?: false }
    suspend fun setSmallModelModeEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.SMALL_MODEL_MODE] = value }
        SettingsCache.smallModelModeEnabled = value
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

    val backupPassword: Flow<String> = context.settingsDataStore.data.map { prefs ->
        LocalSecrets.decrypt(prefs[SettingsKeys.BACKUP_PASSWORD_ENC] ?: "")
    }

    val noteHistoryEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SettingsKeys.NOTE_HISTORY_ENABLED] ?: true }
    suspend fun setNoteHistoryEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.NOTE_HISTORY_ENABLED] = value }
        SettingsCache.noteHistoryEnabled = value
    }

    val assistantConfirmToolsEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[SettingsKeys.ASSISTANT_CONFIRM_TOOLS] ?: true
    }

    suspend fun setAssistantConfirmTools(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.ASSISTANT_CONFIRM_TOOLS] = value }
        SettingsCache.assistantConfirmToolsEnabled = value
    }

    val updateChannel: Flow<String> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.UPDATE_CHANNEL_ENC, SettingsKeys.LEGACY_UPDATE_CHANNEL, "stable")
    }
    suspend fun setUpdateChannel(value: String) {
        SettingsCache.updateChannel = value
        putSecret(SettingsKeys.UPDATE_CHANNEL_ENC, SettingsKeys.LEGACY_UPDATE_CHANNEL, value)
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
    suspend fun setAutoUpdateEnabled(value: Boolean) {
        SettingsCache.autoUpdateEnabled = value
        context.settingsDataStore.edit { it[SettingsKeys.AUTO_UPDATE_ENABLED] = value }
    }

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

    val privilegedEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.PRIVILEGED_ENABLED] ?: false }
    suspend fun setPrivilegedEnabled(value: Boolean) {
        SettingsCache.privilegedEnabled = value
        context.settingsDataStore.edit { it[SettingsKeys.PRIVILEGED_ENABLED] = value }
    }

    val markdownEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.MARKDOWN_ENABLED] ?: false }
    val richTextEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.RICH_TEXT_ENABLED] ?: false }


    val customTemplatesJson: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CUSTOM_TEMPLATES] ?: "[]" }
    suspend fun setCustomTemplatesJson(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.CUSTOM_TEMPLATES] = json }
    }
    val templateDraftJson: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.TEMPLATE_DRAFT] ?: "" }
    suspend fun setTemplateDraftJson(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.TEMPLATE_DRAFT] = json }
    }
    val hiddenTemplatesJson: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.HIDDEN_TEMPLATES] ?: "" }
    suspend fun setHiddenTemplatesJson(json: String) {
        context.settingsDataStore.edit { it[SettingsKeys.HIDDEN_TEMPLATES] = json }
    }
    val embeddingProvider: Flow<String> = context.settingsDataStore.data.map {
        it[SettingsKeys.EMBEDDING_PROVIDER] ?: "local"
    }
    suspend fun setEmbeddingProvider(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.EMBEDDING_PROVIDER] = value }
        SettingsCache.embeddingProvider = value
    }

    val linksEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.LINKS_ENABLED] ?: false }

    val backgroundAnimationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] ?: true }
    suspend fun setBackgroundAnimationEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.BACKGROUND_ANIMATION_ENABLED] = value }
        SettingsCache.backgroundAnimationEnabled = value
    }

    val appLockEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.APP_LOCK_ENABLED] ?: false }
    val appLockCredentials: Flow<String> = context.settingsDataStore.data.map { prefs ->
        LocalSecrets.decrypt(prefs[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] ?: "")
    }

    suspend fun appLockEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.APP_LOCK_ENABLED] ?: false
    suspend fun appLockCredentialsOnce(): String =
        LocalSecrets.decrypt(context.settingsDataStore.data.first()[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] ?: "")
    suspend fun startupLoggingEnabledOnce(): Boolean =
        context.settingsDataStore.data.first()[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false

    suspend fun setAppLock(enabled: Boolean, credentialsJson: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.APP_LOCK_ENABLED] = enabled
            if (enabled && credentialsJson.isNotEmpty()) {
                prefs[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] = LocalSecrets.encrypt(credentialsJson)
            } else if (!enabled) {
                prefs.remove(SettingsKeys.APP_LOCK_CREDENTIALS_ENC)
            }
        }
    }

    suspend fun setAppLockCredentials(credentialsJson: String) {
        context.settingsDataStore.edit { it[SettingsKeys.APP_LOCK_CREDENTIALS_ENC] = LocalSecrets.encrypt(credentialsJson) }
    }

    val startupLoggingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.STARTUP_LOGGING_ENABLED] ?: false }
    suspend fun setStartupLoggingEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.STARTUP_LOGGING_ENABLED] = value }
        SettingsCache.startupLoggingEnabled = value
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

    suspend fun setBaseUrl(value: String) {
        SettingsCache.baseUrl = value
        putSecret(SettingsKeys.BASE_URL_ENC, SettingsKeys.LEGACY_BASE_URL, value)
    }
    suspend fun setApiSpec(value: String) {
        SettingsCache.apiSpec = value
        putSecret(SettingsKeys.API_SPEC_ENC, SettingsKeys.LEGACY_API_SPEC, value)
    }
    suspend fun setModel(value: String) {
        SettingsCache.model = value
        putSecret(SettingsKeys.MODEL_ENC, SettingsKeys.LEGACY_MODEL, value)
    }
    suspend fun setCustomUserAgent(value: String) {
        val trimmed = value.trim()
        SettingsCache.customUserAgent = trimmed
        putSecret(SettingsKeys.CUSTOM_USER_AGENT_ENC, SettingsKeys.LEGACY_CUSTOM_USER_AGENT, trimmed)
    }


    val modelRecents: Flow<List<String>> =
        context.settingsDataStore.data.map { ModelRecents.parse(it[SettingsKeys.MODEL_RECENTS]) }

    suspend fun setActiveModel(value: String) {
        val model = value.trim()
        if (model.isBlank()) return
        val prefs = context.settingsDataStore.data.first()
        val profilesJson = LocalSecrets.decrypt(
            prefs[SettingsKeys.API_PROFILES_ENC] ?: prefs[SettingsKeys.LEGACY_API_PROFILES] ?: ""
        )
        val profiles = ApiProfiles.parse(profilesJson)
        val selected = (prefs[SettingsKeys.API_PROFILE_SELECTED] ?: 0)
        val profilesEnc = if (profiles.isEmpty()) null else {
            val idx = selected.coerceIn(0, profiles.size - 1)
            LocalSecrets.encrypt(
                ApiProfiles.serialize(profiles.mapIndexed { i, p -> if (i == idx) p.copy(model = model) else p })
            )
        }
        val modelEnc = LocalSecrets.encrypt(model)
        val recents = ModelRecents.add(prefs[SettingsKeys.MODEL_RECENTS], model)

        context.settingsDataStore.edit { p ->
            p[SettingsKeys.MODEL_ENC] = modelEnc
            p.remove(SettingsKeys.LEGACY_MODEL)
            if (profilesEnc != null) {
                p[SettingsKeys.API_PROFILES_ENC] = profilesEnc
                p.remove(SettingsKeys.LEGACY_API_PROFILES)
            }
            p[SettingsKeys.MODEL_RECENTS] = recents
        }
        SettingsCache.model = model
    }

    val terminalFontSize: Flow<Float?> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.TERMINAL_FONT_SIZE_ENC, SettingsKeys.LEGACY_TERMINAL_FONT_SIZE, "").toFloatOrNull()
    }
    val terminalKeyBarVisible: Flow<Boolean> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.TERMINAL_KEY_BAR_VISIBLE_ENC, SettingsKeys.LEGACY_TERMINAL_KEY_BAR_VISIBLE, "true").toBooleanStrictOrNull() ?: true
    }
    val globalTextSelectionEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        secret(it, SettingsKeys.GLOBAL_TEXT_SELECTION_ENABLED_ENC, SettingsKeys.LEGACY_GLOBAL_TEXT_SELECTION_ENABLED, "false").toBooleanStrictOrNull() ?: false
    }

    suspend fun setTerminalFontSize(value: Float) {
        SettingsCache.terminalFontSize = value
        putSecret(SettingsKeys.TERMINAL_FONT_SIZE_ENC, SettingsKeys.LEGACY_TERMINAL_FONT_SIZE, value.toString())
    }
    suspend fun setTerminalKeyBarVisible(value: Boolean) {
        SettingsCache.terminalKeyBarVisible = value
        putSecret(SettingsKeys.TERMINAL_KEY_BAR_VISIBLE_ENC, SettingsKeys.LEGACY_TERMINAL_KEY_BAR_VISIBLE, value.toString())
    }
    suspend fun setGlobalTextSelectionEnabled(value: Boolean) {
        SettingsCache.globalTextSelectionEnabled = value
        putSecret(SettingsKeys.GLOBAL_TEXT_SELECTION_ENABLED_ENC, SettingsKeys.LEGACY_GLOBAL_TEXT_SELECTION_ENABLED, value.toString())
    }

    suspend fun setAssistantName(value: String) {
        SettingsCache.assistantName = value
        putSecret(SettingsKeys.ASSISTANT_NAME_ENC, SettingsKeys.LEGACY_ASSISTANT_NAME, value)
    }
    suspend fun setAssistantStyle(value: String) {
        SettingsCache.assistantStyle = value
        putSecret(SettingsKeys.ASSISTANT_STYLE_ENC, SettingsKeys.LEGACY_ASSISTANT_STYLE, value)
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
    suspend fun setBackupPassword(value: String) {
        context.settingsDataStore.edit { prefs ->
            if (value.isEmpty()) prefs.remove(SettingsKeys.BACKUP_PASSWORD_ENC)
            else prefs[SettingsKeys.BACKUP_PASSWORD_ENC] = LocalSecrets.encrypt(value)
        }
    }


    suspend fun setSessionSnapshot(value: String) {
        context.settingsDataStore.edit { it[SettingsKeys.SESSION_SNAPSHOT] = value }
    }

    suspend fun sessionSnapshotOnce(): String =
        context.settingsDataStore.data.first()[SettingsKeys.SESSION_SNAPSHOT] ?: ""
    val notebookOpens: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.NOTEBOOK_OPENS_ENC]?.let { LocalSecrets.decrypt(it) } ?: "{}"
    }
    suspend fun notebookOpensOnce(): String = notebookOpens.first()
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

    suspend fun setTypingHapticsEnabled(value: Boolean) {}

    suspend fun setApiKey(value: String) {
        SettingsCache.apiKey = value
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.API_KEY_ENC] = LocalSecrets.encrypt(value)
            prefs.remove(SettingsKeys.LEGACY_API_KEY)
        }
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
