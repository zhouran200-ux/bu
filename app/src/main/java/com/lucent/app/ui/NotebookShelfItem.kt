package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.Notebook
import com.lucent.app.i18n.S

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotebookShelfItem(
    notebook: Notebook,
    sourceCount: Int,
    itemModifier: Modifier,
    interactive: Boolean = true,
    listScrolling: Boolean = false,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = interactive && menuOpen) { menuOpen = false }
    LaunchedEffect(interactive) { if (!interactive) menuOpen = false }
    LaunchedEffect(listScrolling) { if (listScrolling) menuOpen = false }
    val title = notebook.title.ifBlank { S.notebookEmptyTitle }
    val modifiedAt = remember(notebook.updatedAt) { notebookModifiedAtLabel(notebook.updatedAt) }
    val rowInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Row(
        modifier = itemModifier
            .fillMaxWidth()
            .frostedGlass(cornerRadius = 22.dp)
            .clickable(
                interactionSource = rowInteraction,
                indication = rememberScaleIndication(),
                enabled = interactive
            ) {
                Haptics.tick(context)
                onOpen()
            }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = LucentTitles.titleInk(onGradient),
                fontSize = LucentTitles.EntrySize,
                fontWeight = LucentTitles.EntryWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (sourceCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(LucentStatusColors.SourcePresent, CircleShape)
                            .semantics { contentDescription = S.notebookHasSource }
                    )
                }
                Text(
                    text = modifiedAt,
                    color = onGradientMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            IconButton(
                onClick = {
                    Haptics.tick(context)
                    menuOpen = true
                },
                enabled = interactive,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = S.a11yMoreOptions,
                    tint = onGradientMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                modifier = Modifier.widthIn(min = 176.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = glassPanelColor(),
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = glassPopupBorder(),
                expanded = interactive && menuOpen,
                onDismissRequest = { menuOpen = false },
                properties = NonEditingPopupProperties
            ) {
                DropdownMenuItem(
                    text = { Text(S.notebookRename) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        Haptics.tick(context)
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text(S.actionDelete) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        Haptics.tick(context)
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}
