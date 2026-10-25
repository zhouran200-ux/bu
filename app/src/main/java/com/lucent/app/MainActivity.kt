package com.lucent.app

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lucent.app.AppScope
import com.lucent.app.chat.ChatSessions
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.TrashCleanup
import com.lucent.app.ui.LocalBackgroundEnvironment
import com.lucent.app.ui.rememberBackgroundEnvironment
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LocalBackdropColor
import com.lucent.app.ui.LocalPureWhite
import com.lucent.app.ui.LucentGlass
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.lucentTypography
import kotlinx.coroutines.launch

enum class Screen {
    Notebooks, Settings
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepo = SettingsRepository(applicationContext)
        val cachedSettings = com.lucent.app.data.SettingsCache.snapshot()
        val startup = SettingsRepository.StartupPrefs(
            display = SettingsRepository.DisplayPrefs(
                cachedSettings.themeMode,
                cachedSettings.palette,
                cachedSettings.font,
                cachedSettings.fontScale,
                cachedSettings.lineSpacing,
                cachedSettings.letterSpacing
            ),
            dynamicColor = cachedSettings.dynamicColor,
            backgroundAnimationEnabled = cachedSettings.backgroundAnimationEnabled
        )
        val display = startup.display
        val initialThemeMode = display.themeMode
        val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themeChoice = com.lucent.app.ui.LucentThemeMode.fromKey(initialThemeMode)
        val isPureWhite = themeChoice == com.lucent.app.ui.LucentThemeMode.PURE_WHITE
        val isDarkTheme = !isPureWhite && themeChoice.isDark(systemDark)
        val dynamicActive = !isPureWhite && startup.dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        val initialBackdropColor = if (isPureWhite) {
            LucentGlass.PureWhitePage
        } else if (dynamicActive) {
            val scheme = if (isDarkTheme) androidx.compose.material3.dynamicDarkColorScheme(applicationContext) else androidx.compose.material3.dynamicLightColorScheme(applicationContext)
            scheme.background
        } else {
            themeChoice.backdrop(systemDark)
        }
        window.setBackgroundDrawable(initialBackdropColor.toArgb().toDrawable())

        applySystemBarStyle(isDarkTheme)

        val initialPalette = display.palette
        val initialFont = display.font

        setContent {
            LaunchedEffect(settingsRepo) {
                try {
                    val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { settingsRepo.startupPrefsOnce() }
                    com.lucent.app.data.SettingsCache.seed(loaded)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    LucentToast.show(applicationContext, "读取设置失败，暂时使用默认设置")
                }
            }
            var databaseReady by remember { mutableStateOf(false) }
            var databaseError by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val db = com.lucent.app.data.AppDatabase.getInstance(applicationContext)
                        db.openHelper.writableDatabase
                    }
                    databaseReady = true
                    AppScope.io.launch {
                        TrashCleanup.cleanupLegacyArtifacts(applicationContext)
                        settingsRepo.dropRemovedFeatureKeys()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    databaseError = "无法打开资料库，原文件未主动删除。请勿清除应用数据或卸载。错误：${e.message ?: e.javaClass.simpleName}"
                }
            }
            val themeMode by settingsRepo.themeMode.collectAsState(initial = initialThemeMode)
            val paletteName by settingsRepo.palette.collectAsState(initial = initialPalette)
            val fontKey by settingsRepo.font.collectAsState(initial = initialFont)
            val fontScale by settingsRepo.fontScale.collectAsState(initial = com.lucent.app.data.SettingsCache.fontScale)
            val lineSpacing by settingsRepo.lineSpacing.collectAsState(initial = com.lucent.app.data.SettingsCache.lineSpacing)
            val letterSpacing by settingsRepo.letterSpacing.collectAsState(initial = com.lucent.app.data.SettingsCache.letterSpacing)
            val backgroundAnimated by settingsRepo.backgroundAnimationEnabled.collectAsState(
                initial = startup.backgroundAnimationEnabled
            )
            val backgroundEnvironment = rememberBackgroundEnvironment()
            val appBackgroundAnimated = backgroundAnimated

            val dynamicColorOn by settingsRepo.dynamicColorEnabled.collectAsState(
                initial = startup.dynamicColor
            )

            val systemDark = isSystemInDarkTheme()
            val themeChoice = com.lucent.app.ui.LucentThemeMode.fromKey(themeMode)
            val pureWhite = themeChoice == com.lucent.app.ui.LucentThemeMode.PURE_WHITE
            val isDarkTheme = !pureWhite && themeChoice.isDark(systemDark)
            LaunchedEffect(isDarkTheme) { applySystemBarStyle(isDarkTheme) }

            val themeColors = com.lucent.app.ui.resolveAppThemeColors(
                themeChoice, systemDark, pureWhite, isDarkTheme, dynamicColorOn,
                paletteName
            )
            val colors = themeColors.scheme
            val onGradient = themeColors.ink
            val backdropColor = themeColors.backdrop
            val paletteColors = themeColors.palette
            val onGradientMuted = onGradient.copy(alpha = 0.65f)
            LaunchedEffect(backdropColor) {
                window.setBackgroundDrawable(backdropColor.toArgb().toDrawable())
            }

            val currentDensity = LocalDensity.current
            val appDensity = remember(currentDensity, fontScale) {
                Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale * fontScale
                )
            }

            MaterialTheme(
                colorScheme = colors,
                typography = lucentTypography(
                    fontKey = fontKey,
                    fontScale = fontScale,
                    lineSpacing = lineSpacing,
                    letterSpacing = letterSpacing
                )
            ) {
                CompositionLocalProvider(
                    LocalDensity provides appDensity,
                    LocalOnGradient provides onGradient,
                    LocalOnGradientMuted provides onGradientMuted,
                    LocalBackdropColor provides backdropColor,
                    LocalPureWhite provides pureWhite,
                    LocalBackgroundEnvironment provides backgroundEnvironment
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (databaseReady) {
                            LucentApp(
                                paletteColors = paletteColors,
                                backdropColor = backdropColor,
                                backgroundAnimated = appBackgroundAnimated,
                                pureWhite = pureWhite,
                                paletteName = paletteName
                            )
                        } else {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(databaseError ?: "正在打开资料库…", color = onGradient)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLifecycleState.onForeground()
    }

    override fun onStop() {
        ChatSessions.checkpointAll()
        AppLifecycleState.onBackground()
        super.onStop()
    }

    private fun applySystemBarStyle(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
