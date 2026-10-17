package com.lucent.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import com.lucent.app.ui.LastScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.lucent.app.ui.FluidGlassBackground
import com.lucent.app.ui.LocalBottomBarInset
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LucentGlass
import com.lucent.app.ui.foregroundGlass
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.SettingsRoute
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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
    LaunchedEffect(currentScreen) {
        LastScreen.remember(currentScreen)
    }
    val glassBackdrop = rememberHazeState()
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

    CompositionLocalProvider(
        com.lucent.app.ui.LocalGlassBackdrop provides glassBackdrop
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FluidGlassBackground(
                palette = paletteColors,
                backdropColor = backdropColor,
                animated = backgroundAnimated,
                pureWhite = pureWhite,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = glassBackdrop)
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
                        val glassDark = onGradient.luminance() > 0.5f
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
                                    .foregroundGlass(cornerRadius = 38.dp)
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
