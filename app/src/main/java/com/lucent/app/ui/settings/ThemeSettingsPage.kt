package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.Haptics
import com.lucent.app.ui.LockNote
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentThemeMode
import com.lucent.app.ui.LucentThemeSection
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.PaletteSwatch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import com.lucent.app.ui.selectedCard
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberDynamicColorActive
import com.lucent.app.ui.rememberPureWhiteActive
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun ThemeSettingsPage(repo: SettingsRepository) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val savedTheme by repo.themeMode.collectAsState(initial = SettingsCache.themeMode)
    val dynamicLocked = rememberDynamicColorActive(repo)
    val pureWhiteLocked = rememberPureWhiteActive(repo)
    val locked = dynamicLocked || pureWhiteLocked

    LockNote(if (pureWhiteLocked) S.pureWhiteLockedHint else if (dynamicLocked) S.dynamicThemeLockedHint else "")

    val offered = LucentThemeMode.pickerEntries
    LucentThemeSection.entries.forEachIndexed { index, section ->
        if (index > 0) Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(section.label, color = onGradient, fontSize = LucentTitles.ItemSize, fontWeight = LucentTitles.ItemWeight)
            Spacer(modifier = Modifier.height(8.dp))
            offered.filter { it.section == section }.forEach { mode ->
                ThemeModeRow(mode, savedTheme, locked) {
                    Haptics.tick(context)
                    AppScope.setThemeMode(context, repo, mode.key)
                }
            }
        }
    }
}

@Composable
internal fun ThemeModeRow(
    mode: LucentThemeMode,
    savedTheme: String,
    locked: Boolean,
    onSelect: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val lockAlpha = if (locked) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme-mode-${mode.key}")
            .clip(RoundedCornerShape(12.dp))
            .selectedCard(savedTheme == mode.key, 12.dp)
            .selectable(selected = savedTheme == mode.key, enabled = !locked, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = savedTheme == mode.key,
            enabled = !locked,
            modifier = Modifier.testTag("theme-radio-${mode.key}"),
            onClick = null
        )
        Box(modifier = Modifier.alpha(lockAlpha)) {
            PaletteSwatch(mode.swatch())
        }
        Column(modifier = Modifier.padding(start = 10.dp).alpha(lockAlpha)) {
            Text(mode.label, color = onGradient, fontSize = LucentTitles.ItemSize, fontWeight = LucentTitles.ItemWeight)
            Text(mode.detail, color = onGradientMuted, fontSize = 12.sp)
        }
    }
}
