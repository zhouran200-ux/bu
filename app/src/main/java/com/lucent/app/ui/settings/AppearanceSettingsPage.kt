package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.DynamicColorRow
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun AppearanceSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    BackHeader(onBack = { onRoute(SettingsRoute.Root) })

    DynamicColorRow(repo)

    NavCard(depth = 2, title = S.fontTypographyTitle, subtitle = S.fontTypographySub) { onRoute(SettingsRoute.Font) }
    Spacer(modifier = Modifier.height(12.dp))

    NavCard(depth = 2, title = S.settingsThemeTitle, subtitle = S.settingsThemeSub) { onRoute(SettingsRoute.Theme) }
    Spacer(modifier = Modifier.height(12.dp))

    NavCard(depth = 2, title = S.settingsBackgroundTitle, subtitle = S.settingsBackgroundSub) { onRoute(SettingsRoute.Background) }
}
