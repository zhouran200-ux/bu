package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.DynamicColorRow
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun RootSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    DynamicColorRow(repo)

    NavCard(S.settingsApiConfigTitle, S.settingsApiConfigDescribe) { onRoute(SettingsRoute.Api) }
    Spacer(modifier = Modifier.height(12.dp))

    NavCard(S.fontTypographyTitle, S.fontTypographySub) { onRoute(SettingsRoute.Font) }
    Spacer(modifier = Modifier.height(12.dp))

    NavCard(S.settingsThemeTitle, S.settingsThemeSub) { onRoute(SettingsRoute.Theme) }
    Spacer(modifier = Modifier.height(12.dp))

    NavCard(S.settingsBackgroundTitle, S.settingsBackgroundSub) { onRoute(SettingsRoute.Background) }
}
