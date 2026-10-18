package com.lucent.app.data

internal data class SettingsSnapshot(
    val themeMode: String = "system",
    val palette: String = "CYCLE",
    val font: String = "system",
    val fontScale: Float = 1.0f,
    val lineSpacing: Float = 1.2f,
    val letterSpacing: Float = 0.0f,
    val dynamicColor: Boolean = false,
    val backgroundAnimationEnabled: Boolean = true,
    val customUserAgent: String = "",
    val apiProfilesJson: String = "",
    val apiProfileSelected: Int = 0
)

object SettingsCache {
    private val state = java.util.concurrent.atomic.AtomicReference(SettingsSnapshot())

    var themeMode: String
        get() = state.get().themeMode
        set(value) { state.updateAndGet { it.copy(themeMode = value) } }

    var palette: String
        get() = state.get().palette
        set(value) { state.updateAndGet { it.copy(palette = value) } }

    var font: String
        get() = state.get().font
        set(value) { state.updateAndGet { it.copy(font = value) } }

    var fontScale: Float
        get() = state.get().fontScale
        set(value) { state.updateAndGet { it.copy(fontScale = value) } }

    var lineSpacing: Float
        get() = state.get().lineSpacing
        set(value) { state.updateAndGet { it.copy(lineSpacing = value) } }

    var letterSpacing: Float
        get() = state.get().letterSpacing
        set(value) { state.updateAndGet { it.copy(letterSpacing = value) } }

    var dynamicColor: Boolean
        get() = state.get().dynamicColor
        set(value) { state.updateAndGet { it.copy(dynamicColor = value) } }

    var backgroundAnimationEnabled: Boolean
        get() = state.get().backgroundAnimationEnabled
        set(value) { state.updateAndGet { it.copy(backgroundAnimationEnabled = value) } }

    var customUserAgent: String
        get() = state.get().customUserAgent
        set(value) { state.updateAndGet { it.copy(customUserAgent = value) } }

    var apiProfilesJson: String
        get() = state.get().apiProfilesJson
        set(value) { state.updateAndGet { it.copy(apiProfilesJson = value) } }

    var apiProfileSelected: Int
        get() = state.get().apiProfileSelected
        set(value) { state.updateAndGet { it.copy(apiProfileSelected = value) } }

    internal fun snapshot(): SettingsSnapshot = state.get()

    internal fun updateApiProfiles(json: String, selected: Int) {
        state.updateAndGet { it.copy(apiProfilesJson = json, apiProfileSelected = selected) }
    }

    internal fun resetTypography() {
        state.updateAndGet { it.copy(fontScale = 1.0f, lineSpacing = 1.2f, letterSpacing = 0.0f) }
    }

    fun seed(prefs: SettingsRepository.StartupPrefs) {
        val seeded = SettingsSnapshot(
            themeMode = prefs.display.themeMode, palette = prefs.display.palette, font = prefs.display.font,
            fontScale = prefs.display.fontScale, lineSpacing = prefs.display.lineSpacing,
            letterSpacing = prefs.display.letterSpacing, dynamicColor = prefs.dynamicColor,
            backgroundAnimationEnabled = prefs.backgroundAnimationEnabled, customUserAgent = prefs.customUserAgent,
            apiProfilesJson = prefs.apiProfilesJson, apiProfileSelected = prefs.apiProfileSelected
        )
        while (true) {
            val current = state.get()
            if (current != SettingsSnapshot()) return
            if (state.compareAndSet(current, seeded)) return
        }
    }
}
