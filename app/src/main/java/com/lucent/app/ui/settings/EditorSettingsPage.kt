package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun EditorSettingsPage(
    repo: SettingsRepository,
    onRoute: (SettingsRoute) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val scope = rememberCoroutineScope()
    val linksEnabled by repo.linksEnabled.collectAsState(initial = SettingsCache.linksEnabled)
    val globalTextSelectionEnabled by repo.globalTextSelectionEnabled.collectAsState(initial = SettingsCache.globalTextSelectionEnabled)

    BackHeader(onBack = { onRoute(SettingsRoute.Root) })
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.linksTitle, color = onGradient, fontSize = LucentTitles.ItemSize, fontWeight = LucentTitles.ItemWeight)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = linksEnabled,
                onCheckedChange = { checked ->
                    scope.launch { repo.setLinksEnabled(checked) }
                    SettingsCache.linksEnabled = checked
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.globalTextSelectionTitle, color = onGradient, fontSize = LucentTitles.ItemSize, fontWeight = LucentTitles.ItemWeight)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = globalTextSelectionEnabled,
                onCheckedChange = { checked ->
                    scope.launch { repo.setGlobalTextSelectionEnabled(checked) }
                    SettingsCache.globalTextSelectionEnabled = checked
                }
            )
        }
    }
}
