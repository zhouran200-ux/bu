package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lucent.app.AppScope
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.LockNote
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentPalette
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.PALETTE_CYCLE
import com.lucent.app.ui.PALETTE_RANDOM
import com.lucent.app.ui.PaletteGroup
import com.lucent.app.ui.PaletteSwatch
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberDynamicColorActive
import com.lucent.app.ui.rememberPureWhiteActive
import com.lucent.app.ui.title

@Composable
internal fun BackgroundSettingsPage(repo: SettingsRepository) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val backgroundAnimationEnabled by repo.backgroundAnimationEnabled.collectAsState(
        initial = SettingsCache.backgroundAnimationEnabled
    )
    val savedPalette by repo.palette.collectAsState(initial = SettingsCache.palette)
    val dynamicLocked = rememberDynamicColorActive(repo)
    val pureWhiteLocked = rememberPureWhiteActive(repo)
    val locked = dynamicLocked || pureWhiteLocked
    val lockAlpha = if (locked) 0.5f else 1f
    val motionAlpha = lockAlpha * if (backgroundAnimationEnabled) 1f else 0.38f
    val randomSwatch = remember { LucentPalette.pickerEntries.shuffled().take(4).flatMap { it.colors } }

    fun pickPalette(name: String) {
        if (locked) return
        val motionGated = !backgroundAnimationEnabled && (name == PALETTE_CYCLE || name == PALETTE_RANDOM)
        if (motionGated) return
        AppScope.setPalette(context, repo, name)
    }

    LockNote(if (pureWhiteLocked) S.pureWhiteLockedHint else if (dynamicLocked) S.dynamicThemeLockedHint else "")

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.backgroundAnimationTitle, color = onGradient, fontSize = LucentTitles.ItemSize, fontWeight = LucentTitles.ItemWeight)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = backgroundAnimationEnabled,
                onCheckedChange = { checked ->
                    AppScope.setBackgroundAnimationEnabled(context, repo, checked)
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !locked && backgroundAnimationEnabled) { pickPalette(PALETTE_CYCLE) }
        ) {
            RadioButton(
                selected = savedPalette == PALETTE_CYCLE,
                enabled = !locked && backgroundAnimationEnabled,
                onClick = { pickPalette(PALETTE_CYCLE) }
            )
            Box(modifier = Modifier.alpha(motionAlpha)) {
                PaletteSwatch(LucentPalette.pickerEntries.map { it.colors.first() })
            }
            Text(
                S.paletteCycleAuto,
                color = onGradient.copy(alpha = onGradient.alpha * motionAlpha),
                fontSize = LucentTitles.ItemSize,
                fontWeight = LucentTitles.ItemWeight,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !locked && backgroundAnimationEnabled) { pickPalette(PALETTE_RANDOM) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = savedPalette == PALETTE_RANDOM,
                    enabled = !locked && backgroundAnimationEnabled,
                    onClick = { pickPalette(PALETTE_RANDOM) }
                )
                Box(modifier = Modifier.alpha(motionAlpha)) {
                    PaletteSwatch(randomSwatch)
                }
                Text(
                    S.paletteRandomAuto,
                    color = onGradient.copy(alpha = onGradient.alpha * motionAlpha),
                    fontSize = LucentTitles.ItemSize,
                    fontWeight = LucentTitles.ItemWeight,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }

        PaletteGroup.entries.forEach { group ->
            val heading = group.title()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                heading,
                color = onGradientMuted.copy(alpha = onGradientMuted.alpha * lockAlpha),
                fontSize = LucentTitles.ItemSize,
                fontWeight = LucentTitles.ItemWeight
            )
            LucentPalette.pickerEntries.filter { it.group == group }.forEach { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !locked) { pickPalette(p.name) }
                ) {
                    RadioButton(
                        selected = savedPalette == p.name,
                        enabled = !locked,
                        onClick = { pickPalette(p.name) }
                    )
                    Box(modifier = Modifier.alpha(lockAlpha)) {
                        PaletteSwatch(p.colors)
                    }
                    Text(
                        p.label,
                        color = onGradient.copy(alpha = onGradient.alpha * lockAlpha),
                        fontSize = LucentTitles.ItemSize,
                        fontWeight = LucentTitles.ItemWeight,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
    }
}
