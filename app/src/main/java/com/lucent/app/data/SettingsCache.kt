package com.lucent.app.data

object SettingsCache {

    @Volatile
    var assistantName: String? = null

    @Volatile
    var sessionSnapshot: String? = null

    @Volatile
    var appLockEnabled: Boolean = false

    @Volatile
    var startupLoggingEnabled: Boolean = false

    @Volatile
    var appLanguage: String = "zh"

    @Volatile
    var themeMode: String = "system"

    @Volatile
    var palette: String = "CYCLE"

    @Volatile
    var font: String = "system"

    @Volatile
    var fontScale: Float = 1.0f

    @Volatile
    var lineSpacing: Float = 1.2f

    @Volatile
    var letterSpacing: Float = 0.0f

    @Volatile
    var dynamicColor: Boolean = false

    @Volatile
    var backgroundAnimationEnabled: Boolean = true

    @Volatile
    var assistantStyle: String = ""

    @Volatile
    var baseUrl: String = ""

    @Volatile
    var apiSpec: String = "openai"

    @Volatile
    var apiKey: String = ""

    @Volatile
    var model: String = ""

    @Volatile
    var customUserAgent: String = ""

    @Volatile
    var apiProfilesJson: String = ""

    @Volatile
    var apiProfileSelected: Int = 0

    @Volatile
    var noteHistoryEnabled: Boolean = true

    @Volatile
    var crashShieldEnabled: Boolean = false

    @Volatile
    var blackoutEnabled: Boolean = false

    @Volatile
    var pwSelfDestructEnabled: Boolean = false

    @Volatile
    var pwFirstRoundLimit: Int = 5

    @Volatile
    var pwLaterRoundLimit: Int = 3

    @Volatile
    var pwSelfDestructThreshold: Int = 25

    @Volatile
    var passwordAttemptState: String = ""

    @Volatile
    var closeToTray: Boolean = true

    @Volatile
    var openLinksExternally: Boolean = false

    @Volatile
    var markdownEnabled: Boolean = false

    @Volatile
    var richTextEnabled: Boolean = false

    @Volatile
    var linksEnabled: Boolean = false

    @Volatile
    var assistantConfirmToolsEnabled: Boolean = true

    @Volatile
    var agentMode: Boolean = true

    @Volatile
    var reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key

    @Volatile
    var smallModelModeEnabled: Boolean = false

    @Volatile
    var memoryTier: String = MemoryTier.DEFAULT.key

    @Volatile
    var embeddingProvider: String = "local"

    @Volatile
    var updateChannel: String = "stable"

    @Volatile
    var installedPreviewIdentity: String = ""

    @Volatile
    var stagedUpdateIdentity: String = ""

    @Volatile
    var autoUpdateEnabled: Boolean = false

    @Volatile
    var privilegedEnabled: Boolean = false


    @Volatile
    var terminalFontSize: Float? = null

    @Volatile
    var terminalKeyBarVisible: Boolean = true

    @Volatile
    var globalTextSelectionEnabled: Boolean = false

    fun seed(prefs: SettingsRepository.StartupPrefs) {
        assistantName = prefs.assistantName
        sessionSnapshot = prefs.sessionSnapshot.ifBlank { null }
        appLockEnabled = prefs.appLockEnabled
        startupLoggingEnabled = prefs.startupLoggingEnabled
        appLanguage = prefs.appLanguage
        themeMode = prefs.display.themeMode
        palette = prefs.display.palette
        font = prefs.display.font
        fontScale = prefs.display.fontScale
        lineSpacing = prefs.display.lineSpacing
        letterSpacing = prefs.display.letterSpacing
        dynamicColor = prefs.dynamicColor
        backgroundAnimationEnabled = prefs.backgroundAnimationEnabled
        assistantStyle = prefs.assistantStyle
        baseUrl = prefs.baseUrl
        apiSpec = prefs.apiSpec
        apiKey = prefs.apiKey
        model = prefs.model
        customUserAgent = prefs.customUserAgent
        apiProfilesJson = prefs.apiProfilesJson
        apiProfileSelected = prefs.apiProfileSelected
        noteHistoryEnabled = prefs.noteHistoryEnabled
        crashShieldEnabled = prefs.crashShieldEnabled
        blackoutEnabled = prefs.blackoutEnabled
        pwSelfDestructEnabled = prefs.pwSelfDestructEnabled
        pwFirstRoundLimit = prefs.pwFirstRoundLimit
        pwLaterRoundLimit = prefs.pwLaterRoundLimit
        pwSelfDestructThreshold = prefs.pwSelfDestructThreshold
        passwordAttemptState = prefs.passwordAttemptState
        closeToTray = prefs.closeToTray
        openLinksExternally = prefs.openLinksExternally
        markdownEnabled = prefs.markdownEnabled
        richTextEnabled = prefs.richTextEnabled
        linksEnabled = prefs.linksEnabled
        assistantConfirmToolsEnabled = prefs.assistantConfirmToolsEnabled
        agentMode = prefs.agentMode
        reasoning = prefs.reasoning
        smallModelModeEnabled = prefs.smallModelModeEnabled
        memoryTier = prefs.memoryTier
        embeddingProvider = prefs.embeddingProvider

        terminalFontSize = prefs.terminalFontSize
        terminalKeyBarVisible = prefs.terminalKeyBarVisible
        globalTextSelectionEnabled = prefs.globalTextSelectionEnabled

        updateChannel = prefs.updateChannel
        installedPreviewIdentity = prefs.installedPreviewIdentity
        stagedUpdateIdentity = prefs.stagedUpdateIdentity
        autoUpdateEnabled = prefs.autoUpdateEnabled
        privilegedEnabled = prefs.privilegedEnabled
    }
}
