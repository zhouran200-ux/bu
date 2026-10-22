package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.LucentBuild
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.DynamicColorRow
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun RootSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    if (LucentBuild.isSecondary) {
        Text(
            text = S.settingsSecondaryBuild,
            color = LocalOnGradientMuted.current,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    DynamicColorRow(repo)

    NavCard(S.settingsApiConfigTitle, S.settingsApiConfigDescribe) { onRoute(SettingsRoute.Api) }
    Spacer(modifier = Modifier.height(12.dp))

    NavCard(S.fontTypographyTitle, S.fontTypographySub) { onRoute(SettingsRoute.Font) }
    Spacer(modifier = Modifier.height(12.dp))

    NavCard(S.settingsThemeTitle, S.settingsThemeSub) { onRoute(SettingsRoute.Theme) }
    Spacer(modifier = Modifier.height(12.dp))

    NavCard(S.settingsBackgroundTitle, S.settingsBackgroundSub) { onRoute(SettingsRoute.Background) }
}
