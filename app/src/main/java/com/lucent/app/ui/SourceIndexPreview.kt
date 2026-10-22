package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lucent.app.data.IndexedSource
import com.lucent.app.i18n.S

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
internal fun SourceIndexPreview(source: IndexedSource, onDismiss: () -> Unit) {
    val ink = LocalOnGradient.current
    val muted = LocalOnGradientMuted.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    DisposableEffect(Unit) {
        BackgroundMotionHold.acquire()
        onDispose { BackgroundMotionHold.release() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(lucentPanelBackground())
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = S.sourceIndexPreviewClose,
                        tint = ink
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = S.sourceIndexPreviewTitle,
                        color = ink,
                        fontSize = LucentTitles.EntrySize,
                        fontWeight = LucentTitles.EntryWeight
                    )
                    Text(
                        text = source.title,
                        color = muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { copyPlainTextWithToast(context, source.tagged.text) }) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = S.sourceIndexPreviewCopy,
                        tint = ink
                    )
                }
            }
            Text(
                text = S.sourceIndexPreviewMeta(
                    source.tagged.chapters.size,
                    source.tagged.slices.size,
                    source.tagged.text.length
                ),
                color = muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            Text(
                text = S.sourceIndexPreviewSubtitle,
                color = muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (source.tagged.text.isBlank()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = S.sourceIndexPreviewEmpty, color = muted, fontSize = LucentTitles.ItemSize)
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SelectionContainer(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(start = 18.dp, end = 18.dp, bottom = 24.dp)
                        ) {
                            Text(
                                text = source.tagged.text,
                                color = ink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    ScrollStateCapsule(
                        scrollState = scrollState,
                        enabled = true,
                        label = S.sourceIndexPreviewFastScroll,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}
