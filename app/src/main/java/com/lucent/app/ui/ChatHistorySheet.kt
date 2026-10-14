package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatHistorySheet(
    messages: List<UiChatMessage>,
    currentMessageId: Long?,
    streamingAnswerId: Long?,
    onMessageClick: (Long) -> Unit,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ink = LocalOnGradient.current
    val muted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()
    val hasRecords = messages.isNotEmpty()
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val listMaxHeight = with(density) { (windowHeightPx * 0.5f).toDp() }

    LaunchedEffect(Unit) {
        val index = messages.indexOfFirst { it.id == currentMessageId }
        if (index > 0) listState.scrollToItem(index)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lucentPanelBackground(),
        sheetGesturesEnabled = true,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "对话记录",
                    fontSize = LucentTitles.SectionSize,
                    fontWeight = LucentTitles.SectionWeight,
                    color = ink
                )
                Spacer(modifier = Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "共 ${messages.size} 条",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.size(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                JumpChip(
                    icon = Icons.Default.KeyboardArrowUp,
                    label = "跳到顶部",
                    hint = "首条",
                    enabled = hasRecords,
                    modifier = Modifier.weight(1f),
                    onClick = onTop
                )
                JumpChip(
                    icon = Icons.Default.KeyboardArrowDown,
                    label = "跳到底部",
                    hint = "最新",
                    enabled = hasRecords,
                    modifier = Modifier.weight(1f),
                    onClick = onBottom
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            if (!hasRecords) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "暂无对话记录", fontSize = 13.sp, color = muted)
                }
            } else {
                var userSeq = 0
                var aiSeq = 0
                val numbered = messages.map { m ->
                    if (m.role == "user") {
                        userSeq += 1
                        Triple(m, "问 $userSeq", true)
                    } else {
                        aiSeq += 1
                        Triple(m, "答 $aiSeq", false)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(numbered, key = { it.first.id }) { (m, tag, isUser) ->
                        val isCurrent = m.id == currentMessageId
                        val summary = when {
                            m.id == streamingAnswerId && m.content.isBlank() -> "正在回答…"
                            m.content.isNotBlank() -> previewExcerpt(m.content.take(1024), limit = 70)
                            !m.reasoningText.isNullOrBlank() -> previewExcerpt(m.reasoningText.take(1024), limit = 70)
                            else -> "暂无正文"
                        }
                        HistoryRow(
                            tag = tag,
                            isUser = isUser,
                            summary = summary,
                            current = isCurrent,
                            accent = accent,
                            ink = ink,
                            muted = muted,
                            onClick = { onMessageClick(m.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hint: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val ink = LocalOnGradient.current
    val fade = if (enabled) 1f else 0.4f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ink.copy(alpha = 0.06f * fade))
            .border(1.dp, lucentGlassRim(), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = ink.copy(alpha = 0.8f * fade), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ink.copy(alpha = fade))
        Spacer(modifier = Modifier.size(4.dp))
        Text(text = hint, fontSize = 11.sp, color = ink.copy(alpha = 0.45f * fade))
    }
}

@Composable
private fun HistoryRow(
    tag: String,
    isUser: Boolean,
    summary: String,
    current: Boolean,
    accent: Color,
    ink: Color,
    muted: Color,
    onClick: () -> Unit
) {
    val bg = if (current) accent.copy(alpha = 0.12f) else ink.copy(alpha = 0.045f)
    val border = if (current) accent.copy(alpha = 0.5f) else Color.Transparent
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (isUser) ink.copy(alpha = 0.08f) else accent.copy(alpha = 0.16f))
        ) {
            Text(
                text = tag,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (isUser) ink.copy(alpha = 0.75f) else accent
            )
        }
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isUser) "提问" else "回答",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ink.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = summary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
