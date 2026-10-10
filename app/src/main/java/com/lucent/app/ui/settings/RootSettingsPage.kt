package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.i18n.S
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun RootSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    NavCard(S.settingsAppearanceTitle, S.settingsAppearanceSub) { onRoute(SettingsRoute.Appearance) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsEditorTitle, S.settingsEditorSub) { onRoute(SettingsRoute.Editor) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsApiConfigTitle, "选择与连接") { onRoute(SettingsRoute.Api) }
}
