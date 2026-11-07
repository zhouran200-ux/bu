package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lucent.app.i18n.S

/** Reader-only controls; document mutations and reading-position state stay with their owners. */
@Composable
internal fun SourceReaderActions(
    enabled: Boolean,
    previewEnabled: Boolean,
    onReplace: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ink = LocalOnGradient.current
    val muted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val hasManageActions = onReplace != null || onRemove != null
    var menuOpen by remember(enabled, hasManageActions) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .testTag("source_reader_actions")
            .foregroundGlass(cornerRadius = 22.dp, reading = true)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasManageActions) {
            Box {
                IconButton(
                    onClick = {
                        Haptics.tick(context)
                        menuOpen = !menuOpen
                    },
                    enabled = enabled,
                    modifier = Modifier.size(48.dp)
                        .background(
                            if (menuOpen) accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = S.sourcesManage,
                        tint = if (enabled) ink else muted.copy(alpha = 0.38f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Anchoring the popup to the bottom control lets it open above when space is limited.
                DropdownMenu(
                    expanded = menuOpen && enabled,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.width(200.dp),
                    shape = RoundedCornerShape(20.dp),
                    containerColor = glassPanelColor(),
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = glassPopupBorder()
                ) {
                    if (onReplace != null) {
                        DropdownMenuItem(
                            text = { Text(S.sourcesReplace, color = ink, fontSize = LucentTitles.ItemSize) },
                            leadingIcon = {
                                Icon(Icons.Rounded.FindReplace, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                menuOpen = false
                                Haptics.tick(context)
                                onReplace()
                            }
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text(S.sourcesRemove, color = MaterialTheme.colorScheme.error, fontSize = LucentTitles.ItemSize) },
                            leadingIcon = {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                menuOpen = false
                                Haptics.tick(context)
                                onRemove()
                            }
                        )
                    }
                }
            }
            Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(22.dp).background(ink.copy(alpha = 0.12f)))
        }
        IconButton(
            onClick = {
                Haptics.tick(context)
                onOpenPreview()
            },
            enabled = enabled && previewEnabled,
            modifier = Modifier.size(48.dp)
                .background(accent.copy(alpha = if (enabled && previewEnabled) 0.12f else 0.04f), RoundedCornerShape(16.dp))
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = S.sourceIndexPreviewOpen,
                tint = if (enabled && previewEnabled) accent else muted.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
