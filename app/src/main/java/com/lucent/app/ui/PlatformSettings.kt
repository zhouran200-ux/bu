package com.lucent.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import kotlinx.coroutines.launch

private val dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun DynamicColorRow(repo: SettingsRepository) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val dynamicColorOn by repo.dynamicColorEnabled.collectAsState(
        initial = SettingsCache.dynamicColor
    )
    val themeMode by repo.themeMode.collectAsState(initial = SettingsCache.themeMode)
    val pureWhiteOn = themeMode == LucentThemeMode.PURE_WHITE.key

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.dynamicColorTitle, color = LucentTitles.titleInk(onGradient), fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight)
                if (!dynamicColorSupported) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(S.dynamicColorUnsupported, color = onGradientMuted, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = dynamicColorOn && !pureWhiteOn,
                enabled = dynamicColorSupported && !pureWhiteOn,
                onCheckedChange = { on ->
                    SettingsCache.dynamicColor = on
                    AppScope.io.launch { repo.setDynamicColorEnabled(on) }
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(onGradientMuted.copy(alpha = 0.18f))
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.pureWhiteTitle, color = LucentTitles.titleInk(onGradient), fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight)
                Spacer(modifier = Modifier.height(4.dp))
                Text(S.pureWhiteSubtitle, color = onGradientMuted, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = pureWhiteOn,
                enabled = !(dynamicColorOn && dynamicColorSupported),
                onCheckedChange = { on ->
                    val next = if (on) LucentThemeMode.PURE_WHITE.key else LucentThemeMode.SYSTEM.key
                    SettingsCache.themeMode = next
                    AppScope.io.launch { repo.setThemeMode(next) }
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
fun rememberDynamicColorActive(repo: SettingsRepository): Boolean {
    val dynamicColorOn by repo.dynamicColorEnabled.collectAsState(
        initial = SettingsCache.dynamicColor && dynamicColorSupported
    )
    return dynamicColorOn && dynamicColorSupported
}

@Composable
fun rememberPureWhiteActive(repo: SettingsRepository): Boolean {
    val themeMode by repo.themeMode.collectAsState(initial = SettingsCache.themeMode)
    return themeMode == LucentThemeMode.PURE_WHITE.key
}
