package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lucent.app.AppScope
import com.lucent.app.collapseExcessBlankLines
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.UserAgentPresets
import com.lucent.app.i18n.S
import com.lucent.app.ui.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException

@Composable
internal fun GlobalUserAgentSection(repo: SettingsRepository) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val savedUserAgent by repo.customUserAgent.collectAsState(initial = SettingsCache.customUserAgent)

    var isTavernSelected by remember(savedUserAgent) {
        mutableStateOf(savedUserAgent == UserAgentPresets.SILLY_TAVERN_NODE_FETCH)
    }
    var customInput by remember(savedUserAgent) {
        mutableStateOf(if (savedUserAgent == UserAgentPresets.SILLY_TAVERN_NODE_FETCH) "" else savedUserAgent)
    }

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = S.userAgentSectionTitle,
                color = onGradient,
                fontSize = LucentTitles.SectionSize,
                fontWeight = LucentTitles.SectionWeight,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassButton(
                text = S.userAgentSaveButton,
                onClick = {
                    Haptics.tick(context)
                    focusManager.clearFocus()
                    val target = if (isTavernSelected) UserAgentPresets.SILLY_TAVERN_NODE_FETCH else customInput.trim()
                    AppScope.io.launch {
                        try {
                            repo.setCustomUserAgent(target)
                            withContext(Dispatchers.Main) { LucentToast.show(context, S.userAgentSavedToast) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) { LucentToast.show(context, "保存标识失败，请重试") }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        ChipRow(
            options = listOf(
                "default" to S.userAgentPresetDefault,
                "tavern" to S.userAgentPresetSillyTavern
            ),
            selectedKey = if (isTavernSelected) "tavern" else "default",
            onSelect = { picked ->
                Haptics.tick(context)
                isTavernSelected = picked == "tavern"
                if (isTavernSelected) focusManager.clearFocus()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        CompactField(
            value = if (isTavernSelected) UserAgentPresets.SILLY_TAVERN_NODE_FETCH else customInput,
            onValueChange = {
                if (!isTavernSelected) {
                    customInput = collapseExcessBlankLines(it)
                }
            },
            label = "标识",
            placeholder = S.fieldUserAgent,
            readOnly = isTavernSelected,
            trailing = {
                if (isTavernSelected) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = onGradientMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )
    }
}
