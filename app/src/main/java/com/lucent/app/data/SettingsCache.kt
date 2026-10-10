package com.lucent.app.data

object SettingsCache {

    @Volatile
    var startupLoggingEnabled: Boolean = false

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
    var openLinksExternally: Boolean = false

    @Volatile
    var markdownEnabled: Boolean = false

    @Volatile
    var richTextEnabled: Boolean = false

    @Volatile
    var linksEnabled: Boolean = false

    @Volatile
    var reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key

    @Volatile
    var updateChannel: String = "stable"

    @Volatile
    var installedPreviewIdentity: String = ""

    @Volatile
    var stagedUpdateIdentity: String = ""


    @Volatile
    var globalTextSelectionEnabled: Boolean = false

    fun seed(prefs: SettingsRepository.StartupPrefs) {
        startupLoggingEnabled = prefs.startupLoggingEnabled
        themeMode = prefs.display.themeMode
        palette = prefs.display.palette
        font = prefs.display.font
        fontScale = prefs.display.fontScale
        lineSpacing = prefs.display.lineSpacing
        letterSpacing = prefs.display.letterSpacing
        dynamicColor = prefs.dynamicColor
        backgroundAnimationEnabled = prefs.backgroundAnimationEnabled
        baseUrl = prefs.baseUrl
        apiSpec = prefs.apiSpec
        apiKey = prefs.apiKey
        model = prefs.model
        customUserAgent = prefs.customUserAgent
        apiProfilesJson = prefs.apiProfilesJson
        apiProfileSelected = prefs.apiProfileSelected
        openLinksExternally = prefs.openLinksExternally
        markdownEnabled = prefs.markdownEnabled
        richTextEnabled = prefs.richTextEnabled
        linksEnabled = prefs.linksEnabled
        reasoning = prefs.reasoning

        globalTextSelectionEnabled = prefs.globalTextSelectionEnabled

        updateChannel = prefs.updateChannel
        installedPreviewIdentity = prefs.installedPreviewIdentity
        stagedUpdateIdentity = prefs.stagedUpdateIdentity
    }
}
