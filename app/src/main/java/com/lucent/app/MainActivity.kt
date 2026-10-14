package com.lucent.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import com.lucent.app.ui.LastScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.lucent.app.AppScope
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.TrashCleanup
import com.lucent.app.ui.FluidGlassBackground
import com.lucent.app.ui.LocalBackgroundEnvironment
import com.lucent.app.ui.rememberBackgroundEnvironment
import com.lucent.app.ui.LocalHazeState
import com.lucent.app.ui.LocalBottomBarInset
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LocalBackdropColor
import com.lucent.app.ui.LocalPureWhite
import com.lucent.app.ui.LucentGlass
import com.lucent.app.ui.lucentGlassRim
import com.lucent.app.ui.lucentScheme
import com.lucent.app.ui.pureWhiteScheme
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.lucentTypography
import com.lucent.app.ui.LucentPalette
import com.lucent.app.ui.PALETTE_CYCLE
import com.lucent.app.ui.rememberCyclingPaletteColors
import com.lucent.app.ui.SettingsRoute
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class Screen {
    Notebooks, Settings
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepo = SettingsRepository(applicationContext)
        val startup = try {
            runBlocking { settingsRepo.startupPrefsOnce() }
                .also { com.lucent.app.data.SettingsCache.seed(it) }
        } catch (t: Throwable) {
            SettingsRepository.StartupPrefs(
                display = SettingsRepository.DisplayPrefs("system", "SUNSET", "system")
            )
        }
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
                        TrashCleanup.purgeExpired(applicationContext)
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

            val dynamicActive = !pureWhite && dynamicColorOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val colors: ColorScheme
            val onGradient: Color
            val backdropColor: Color
            val paletteColors: List<Color>
            if (pureWhite) {
                val baseScheme = lightColorScheme()
                colors = pureWhiteScheme(baseScheme)
                onGradient = LucentGlass.PureWhiteInk
                backdropColor = LucentGlass.PureWhitePage
                paletteColors = listOf(LucentGlass.PureWhitePage, LucentGlass.PureWhiteSurface)
            } else if (dynamicActive) {
                val scheme = if (isDarkTheme) {
                    dynamicDarkColorScheme(this@MainActivity)
                } else {
                    dynamicLightColorScheme(this@MainActivity)
                }
                colors = scheme
                onGradient = scheme.onBackground
                backdropColor = scheme.background
                paletteColors = com.lucent.app.ui.dynamicBlobPalette(scheme)
            } else {
                onGradient = if (isDarkTheme) Color.White else Color(0xFF20202B)
                backdropColor = themeChoice.backdrop(systemDark)
                paletteColors = if (paletteName == com.lucent.app.ui.PALETTE_RANDOM) {
                    com.lucent.app.ui.rememberRandomPaletteColors(
                        animated = backgroundAnimated,
                        environment = backgroundEnvironment
                    )
                } else if (paletteName == PALETTE_CYCLE) {
                    rememberCyclingPaletteColors(
                        LucentPalette.pickerEntries.map { it.colors },
                        animated = backgroundAnimated,
                        environment = backgroundEnvironment
                    )
                } else {
                    LucentPalette.entries.firstOrNull { it.name == paletteName }?.colors
                        ?: LucentPalette.SUNSET.colors
                }
                val baseScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
                colors = lucentScheme(baseScheme, paletteColors, onGradient, backdropColor)
            }
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
                                pureWhite = pureWhite
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

    override fun onDestroy() {
        if (isFinishing) com.lucent.app.chat.ChatSessions.stopAll()
        super.onDestroy()
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun LucentApp(paletteColors: List<Color>, backdropColor: Color, backgroundAnimated: Boolean = true, pureWhite: Boolean = false) {
    var currentScreen by rememberSaveable { mutableStateOf(LastScreen.current) }
    val tabs = HomeTab.entries
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(HomeTab.of(currentScreen)),
        pageCount = { tabs.size }
    )
    val currentTab by remember(pagerState, tabs) {
        derivedStateOf { tabs[pagerState.currentPage.coerceIn(tabs.indices)] }
    }
    val tabScope = rememberCoroutineScope()
    LaunchedEffect(currentScreen) {
        LastScreen.remember(currentScreen)
    }
    val hazeState = rememberHazeState()
    val onGradient = LocalOnGradient.current
    val context = LocalContext.current

    var backArmed by remember { mutableStateOf(false) }
    LaunchedEffect(backArmed) {
        if (backArmed) {
            delay(2000)
            backArmed = false
        }
    }

    fun finishActivity() {
        var c: android.content.Context = context
        while (c is android.content.ContextWrapper && c !is Activity) c = c.baseContext
        (c as? Activity)?.finish()
    }

    LaunchedEffect(AppNavigation.requestedScreen) {
        AppNavigation.consumeScreen()?.let { target ->
            if (target != currentScreen) currentScreen = target
        }
    }

    BackHandler(enabled = !AppNavigation.innerBackActive) {
        when {
            currentScreen == Screen.Settings && AppNavigation.settingsRoute == SettingsRoute.Root -> {
                AppNavigation.resetSettingsRoute()
                currentScreen = Screen.Notebooks
            }
            !backArmed -> {
                backArmed = true
                LucentToast.show(context, com.lucent.app.i18n.S.pressBackAgainToExit)
            }
            else -> finishActivity()
        }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize()) {
            FluidGlassBackground(
                palette = paletteColors,
                backdropColor = backdropColor,
                animated = backgroundAnimated,
                pureWhite = pureWhite,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .clearAndSetSemantics { }
            )
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (AppNavigation.activeNotebookId == null || currentScreen == Screen.Settings) {
                        TopAppBar(
                            title = {
                                if (currentTab == HomeTab.Settings) {
                                    com.lucent.app.ui.SettingsBreadcrumb(
                                        route = AppNavigation.settingsRoute,
                                        onNavigate = { SettingsNav.go(it) },
                                        modifier = Modifier.padding(end = 16.dp)
                                    )
                                } else {
                                    Text(
                                        currentTab.label,
                                        color = com.lucent.app.ui.LucentTitles.titleInk(onGradient),
                                        fontSize = com.lucent.app.ui.LucentTitles.ScreenSize,
                                        fontWeight = com.lucent.app.ui.LucentTitles.ScreenWeight
                                    )
                                }
                            },
                            actions = {
                                if (currentTab == HomeTab.Notebooks) {
                                    IconButton(
                                        onClick = { AppNavigation.requestCreateNotebook() },
                                        modifier = Modifier
                                            .padding(end = 10.dp)
                                            .size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = com.lucent.app.ui.LucentIcons.Create,
                                            contentDescription = com.lucent.app.i18n.S.notebookNew,
                                            tint = onGradient,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            )
                        )
                    }
                },
                bottomBar = {
                    if (AppNavigation.activeNotebookId == null &&
                        AppNavigation.settingsRoute == SettingsRoute.Root
                    ) {
                        val capsuleShape = RoundedCornerShape(percent = 50)
                        val glassDark = onGradient.luminance() > 0.5f
                        val capsuleFill = if (pureWhite) {
                            LucentGlass.PureWhiteSurface
                        } else {
                            Color.White.copy(
                                alpha = if (glassDark) com.lucent.app.ui.LucentGlass.BLURRED_FILL_DARK
                                else com.lucent.app.ui.LucentGlass.BLURRED_FILL_LIGHT
                            )
                        }
                        val capsuleRim = lucentGlassRim(strong = true)
                        val capsuleDivider = if (pureWhite) LucentGlass.PureWhiteDivider
                            else if (glassDark) Color.White.copy(alpha = 0.08f) else onGradient.copy(alpha = 0.10f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(top = 12.dp, bottom = 26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.875f)
                                    .height(76.dp)
                                    .shadow(
                                        elevation = if (pureWhite) 10.dp else if (glassDark) 18.dp else 12.dp,
                                        shape = capsuleShape,
                                        clip = false,
                                        ambientColor = if (pureWhite) LucentGlass.PureWhiteShadowAmbient else if (glassDark) Color.Black.copy(alpha = 0.35f) else Color(0xFF2A2A3A).copy(alpha = 0.26f),
                                        spotColor = if (pureWhite) LucentGlass.PureWhiteShadowSpot else if (glassDark) Color.Black.copy(alpha = 0.45f) else Color(0xFF2A2A3A).copy(alpha = 0.34f)
                                    )
                                    .clip(capsuleShape)
                                    .then(
                                        if (pureWhite) Modifier
                                        else Modifier.hazeEffect(
                                            state = hazeState,
                                            style = HazeMaterials.ultraThin(
                                                if (glassDark) com.lucent.app.ui.LucentGlass.HazeContainerDark
                                                else com.lucent.app.ui.LucentGlass.HazeContainerLight
                                            )
                                        )
                                    )
                                    .background(capsuleFill)
                                    .border(
                                        1.5.dp,
                                        capsuleRim,
                                        capsuleShape
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    tabs.forEachIndexed { index, tab ->
                                        if (index > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(24.dp)
                                                    .background(capsuleDivider)
                                            )
                                        }
                                        CapsuleNavItem(
                                            tab = tab,
                                            selected = currentTab == tab,
                                            onClick = {
                                                currentScreen = tab.screen()
                                                tabScope.launch {
                                                    pagerState.animateScrollToPage(
                                                        tabs.indexOf(tab),
                                                        animationSpec = androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                val topPad = if (AppNavigation.activeNotebookId == null || currentScreen == Screen.Settings) padding.calculateTopPadding() else 0.dp
                val bottomInset = if (AppNavigation.activeNotebookId == null) padding.calculateBottomPadding() else 0.dp
                val contentModifier = Modifier.padding(top = topPad).fillMaxSize()
                CompositionLocalProvider(LocalBottomBarInset provides bottomInset) {
                    KeepAliveTabs(active = currentScreen, pagerState = pagerState, modifier = contentModifier)
                }
            }
        }
    }
}
