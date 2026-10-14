package com.lucent.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsCacheSeedTest {

    private data class Snapshot(
        val themeMode: String,
        val palette: String,
        val font: String,
        val fontScale: Float,
        val lineSpacing: Float,
        val letterSpacing: Float,
        val dynamicColor: Boolean,
        val backgroundAnimationEnabled: Boolean,
        val customUserAgent: String,
        val apiProfilesJson: String,
        val apiProfileSelected: Int
    )

    private lateinit var saved: Snapshot

    @Before
    fun captureExistingCache() {
        saved = Snapshot(
            themeMode = SettingsCache.themeMode,
            palette = SettingsCache.palette,
            font = SettingsCache.font,
            fontScale = SettingsCache.fontScale,
            lineSpacing = SettingsCache.lineSpacing,
            letterSpacing = SettingsCache.letterSpacing,
            dynamicColor = SettingsCache.dynamicColor,
            backgroundAnimationEnabled = SettingsCache.backgroundAnimationEnabled,
            customUserAgent = SettingsCache.customUserAgent,
            apiProfilesJson = SettingsCache.apiProfilesJson,
            apiProfileSelected = SettingsCache.apiProfileSelected
        )
    }

    @After
    fun restoreExistingCache() {
        SettingsCache.themeMode = saved.themeMode
        SettingsCache.palette = saved.palette
        SettingsCache.font = saved.font
        SettingsCache.fontScale = saved.fontScale
        SettingsCache.lineSpacing = saved.lineSpacing
        SettingsCache.letterSpacing = saved.letterSpacing
        SettingsCache.dynamicColor = saved.dynamicColor
        SettingsCache.backgroundAnimationEnabled = saved.backgroundAnimationEnabled
        SettingsCache.customUserAgent = saved.customUserAgent
        SettingsCache.apiProfilesJson = saved.apiProfilesJson
        SettingsCache.apiProfileSelected = saved.apiProfileSelected
    }

    @Test
    fun seedCopiesEveryDisplayFieldFromStartupPrefs() {
        SettingsCache.seed(
            SettingsRepository.StartupPrefs(
                display = SettingsRepository.DisplayPrefs(
                    themeMode = "pure_white",
                    palette = "OCEAN",
                    font = "custom-font-id",
                    fontScale = 1.35f,
                    lineSpacing = 1.6f,
                    letterSpacing = 0.05f
                ),
                backgroundAnimationEnabled = false,
                dynamicColor = true,
                customUserAgent = "node-fetch (+https://github.com/node-fetch/node-fetch)",
                apiProfilesJson = """[{"id":"a","name":"方案"}]""",
                apiProfileSelected = 2
            )
        )

        assertEquals("pure_white", SettingsCache.themeMode)
        assertEquals("OCEAN", SettingsCache.palette)
        assertEquals("custom-font-id", SettingsCache.font)
        assertEquals(1.35f, SettingsCache.fontScale, 0.0001f)
        assertEquals(1.6f, SettingsCache.lineSpacing, 0.0001f)
        assertEquals(0.05f, SettingsCache.letterSpacing, 0.0001f)
        assertEquals(true, SettingsCache.dynamicColor)
        assertEquals(false, SettingsCache.backgroundAnimationEnabled)
        assertEquals("node-fetch (+https://github.com/node-fetch/node-fetch)", SettingsCache.customUserAgent)
        assertEquals("""[{"id":"a","name":"方案"}]""", SettingsCache.apiProfilesJson)
        assertEquals(2, SettingsCache.apiProfileSelected)
    }

    @Test
    fun seedIsIdempotentForTheSameStartupPrefs() {
        val prefs = SettingsRepository.StartupPrefs(
            display = SettingsRepository.DisplayPrefs(
                themeMode = "dark",
                palette = "CYCLE",
                font = "system"
            )
        )
        SettingsCache.seed(prefs)
        val first = SettingsCache.themeMode to SettingsCache.apiProfileSelected
        SettingsCache.seed(prefs)
        assertEquals(first, SettingsCache.themeMode to SettingsCache.apiProfileSelected)
        assertEquals("dark", SettingsCache.themeMode)
        assertEquals(0, SettingsCache.apiProfileSelected)
    }

    @Test
    fun startupPrefsCarryTheSameDefaultsTheCacheStartsWith() {
        val defaults = SettingsRepository.StartupPrefs(
            display = SettingsRepository.DisplayPrefs(
                themeMode = "system",
                palette = "CYCLE",
                font = "system"
            )
        )
        assertEquals("system", defaults.display.themeMode)
        assertEquals("CYCLE", defaults.display.palette)
        assertEquals("system", defaults.display.font)
        assertEquals(1.0f, defaults.display.fontScale, 0.0001f)
        assertEquals(1.2f, defaults.display.lineSpacing, 0.0001f)
        assertEquals(0.0f, defaults.display.letterSpacing, 0.0001f)
        assertEquals(true, defaults.backgroundAnimationEnabled)
        assertEquals(false, defaults.dynamicColor)
        assertEquals("", defaults.customUserAgent)
        assertEquals("", defaults.apiProfilesJson)
        assertEquals(0, defaults.apiProfileSelected)
    }
}