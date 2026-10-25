package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.IndexedSource
import com.lucent.app.i18n.S

@Composable
internal fun SourceManageEntry(
    enabled: Boolean,
    onReplace: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ink = LocalOnGradient.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .pressableGlass(
                    onClick = {
                        Haptics.tick(context)
                        menuOpen = true
                    },
                    cornerRadius = 12.dp,
                    enabled = enabled,
                    reading = true
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = S.a11yMoreOptions,
                tint = ink.copy(alpha = if (enabled) 0.9f else 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = menuOpen && enabled,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = glassPanelColor(),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = glassPopupBorder()
        ) {
            if (onReplace != null) {
                DropdownMenuItem(
                    text = { Text(S.sourcesReplace, color = ink, fontSize = LucentTitles.ItemSize) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = ink,
                            modifier = Modifier.size(19.dp)
                        )
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
                    text = {
                        Text(
                            S.sourcesRemove,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = LucentTitles.ItemSize
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp)
                        )
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
}

@Composable
internal fun SourceIndexPreviewEntry(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tint = LocalOnGradient.current
    Box(
        modifier = modifier
            .size(44.dp)
            .pressableGlass(
                onClick = {
                    Haptics.tick(context)
                    onClick()
                },
                cornerRadius = 12.dp,
                enabled = enabled,
                reading = true
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.DataObject,
            contentDescription = S.sourceIndexPreviewOpen,
            tint = tint.copy(alpha = if (enabled) 0.9f else 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun SourceIndexPreview(
    source: IndexedSource,
    rows: List<SourcePreviewRow>,
    listState: LazyListState,
    active: Boolean,
    onBack: () -> Unit
) {
    val ink = LocalOnGradient.current
    val muted = LocalOnGradientMuted.current
    val context = LocalContext.current
    val reading = LocalReadingTypography.current
    val rowKeys = remember(rows) { rows.map { it.key } }
    val chapterIndices = remember(rows) { rows.indices.filter { rows[it].isChapter } }
    val meta = S.sourceIndexPreviewMeta(
        source.tagged.chapters.size,
        source.tagged.slices.size,
        source.tagged.text.length
    )
    val sourceLine = listOf(meta, source.title.trim()).filter { it.isNotBlank() }.joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lucentPanelBackground())
            .testTag("source_model_page")
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = active,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = S.sourceIndexPreviewBack,
                        tint = ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = S.sourceIndexPreviewTitle,
                        color = ink,
                        fontSize = 15.sp,
                        fontWeight = LucentTitles.EntryWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = sourceLine,
                        color = muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { copyPlainTextWithToast(context, source.tagged.text) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = S.sourceIndexPreviewCopy,
                        tint = ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        if (rows.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = S.sourceIndexPreviewEmpty, color = muted, fontSize = LucentTitles.ItemSize)
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("source_model_list")
                            .sourceReadingPosition(listState, rowKeys),
                        contentPadding = PaddingValues(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp * reading.paragraphSpacing)
                    ) {
                        items(rows, key = { it.key }) { row ->
                            ScopedReadingTypography {
                                Text(
                                    text = row.text,
                                    color = ink,
                                    fontSize = 13.sp,
                                    lineHeight = (20f * reading.lineFactor).sp,
                                    letterSpacing = reading.letterSpacing.sp
                                )
                            }
                        }
                    }
                }
                DocumentScrollCapsule(
                    listState = listState,
                    itemKeys = rowKeys,
                    chapterIndices = chapterIndices,
                    enabled = active,
                    label = S.sourceIndexPreviewFastScroll,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}
