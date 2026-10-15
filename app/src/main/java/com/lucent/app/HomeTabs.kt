package com.lucent.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.NotebooksScreen
import com.lucent.app.ui.SettingsScreen

enum class HomeTab {
    Notebooks, Settings;

    val label: String
        get() = when (this) {
            Notebooks -> com.lucent.app.i18n.S.screenNotebooks
            Settings -> com.lucent.app.i18n.S.tabSettings
        }

    fun screen(): Screen = when (this) {
        Notebooks -> Screen.Notebooks
        Settings -> Screen.Settings
    }

    companion object {
        fun of(screen: Screen): HomeTab = when (screen) {
            Screen.Notebooks -> Notebooks
            Screen.Settings -> Settings
        }
    }
}

internal fun capsuleNavLabelStyle(base: TextStyle): TextStyle = base.copy(
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

@Composable
internal fun CapsuleNavItem(
    tab: HomeTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val animTint by animateColorAsState(
        targetValue = if (selected) onGradient else onGradientMuted,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "tabTint"
    )
    val animBgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.14f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "tabBgAlpha"
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .background(onGradient.copy(alpha = animBgAlpha))
            .clickable {
                com.lucent.app.ui.Haptics.tick(context)
                onClick()
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            iconFor(tab),
            contentDescription = tab.label,
            tint = animTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            tab.label,
            color = animTint,
            style = capsuleNavLabelStyle(MaterialTheme.typography.labelSmall),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

fun iconFor(tab: HomeTab): ImageVector = when (tab) {
    HomeTab.Notebooks -> com.lucent.app.ui.LucentIcons.Notebook
    HomeTab.Settings -> com.lucent.app.ui.LucentIcons.Settings
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeepAliveTabs(active: Screen, pagerState: PagerState, modifier: Modifier = Modifier) {
    val tabs = HomeTab.entries
    val activeTab = HomeTab.of(active)

    LaunchedEffect(activeTab) {
        val targetPage = tabs.indexOf(activeTab)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage, animationSpec = tween(280, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != tabs.indexOf(activeTab)) {
            AppNavigation.requestScreen(tabs[pagerState.currentPage].screen())
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = tabs.lastIndex,
        userScrollEnabled = !AppNavigation.innerBackActive,
        modifier = modifier
    ) { page ->
        val tab = tabs[page]
        val isActive = tab == activeTab
        key(tab) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    HomeTab.Notebooks -> NotebooksScreen(active = isActive)
                    HomeTab.Settings -> SettingsScreen(active = isActive)
                }
            }
        }
    }
}
