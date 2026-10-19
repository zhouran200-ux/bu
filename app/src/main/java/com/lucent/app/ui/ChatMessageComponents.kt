package com.lucent.app.ui

import com.lucent.app.i18n.S
import com.lucent.app.chat.UiChatMessage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.CitationRef

@Composable
internal fun UserMessageCard(
    modifier: Modifier = Modifier,
    message: UiChatMessage,
    actionsEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val textColor = LocalOnGradient.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            modifier = Modifier
                .frostedGlass(cornerRadius = 20.dp)
        ) {
            CollapsibleUserText(
                text = message.content,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                modifier = Modifier.padding(14.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BubbleAction(
                icon = Icons.Rounded.ContentCopy,
                label = S.msgCopyFull,
                tint = textColor,
                onClick = { copyTextToClipboard(context, message.content) }
            )
            BubbleAction(
                icon = Icons.Rounded.Edit,
                label = S.msgEdit,
                tint = textColor,
                enabled = actionsEnabled,
                onClick = onEdit
            )
            BubbleAction(
                icon = Icons.Rounded.Delete,
                label = S.actionDelete,
                tint = textColor,
                enabled = actionsEnabled,
                onClick = onDelete
            )
        }
    }
}

@Composable
internal fun AiMessageCard(
    modifier: Modifier = Modifier,
    message: UiChatMessage,
    readingKey: String,
    streaming: Boolean = false,
    liveThought: State<String>? = null,
    thinkingState: ThinkingLaneState = rememberThinkingLaneState(readingKey, streaming),
    onThinkingToggle: () -> Unit = {},
    actionsEnabled: Boolean = true,
    minHeightReservePx: Int? = null,
    onRegenerate: () -> Unit = {},
    onEditAnswer: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCitationClick: ((CitationRef) -> Unit)? = null
) {
    val context = LocalContext.current
    val reserveMin = minHeightReservePx?.let { px -> with(LocalDensity.current) { px.toDp() } }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 12.dp)
            .then(if (reserveMin != null) Modifier.heightIn(min = reserveMin) else Modifier)
    ) {
        ThinkingLane(
            readingKey = readingKey,
            liveThought = liveThought,
            storedThought = message.reasoningText.orEmpty(),
            tag = message.reasoningTag,
            streaming = streaming,
            answerSeconds = message.answerSeconds,
            lane = thinkingState,
            onToggle = onThinkingToggle
        )

        if (message.content.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .foregroundGlass(cornerRadius = 18.dp, reading = true)
            ) {
                SelectionContainer {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MarkdownText(
                            text = message.content,
                            streaming = streaming,
                            modifier = Modifier.fillMaxWidth(),
                            onCitationClick = onCitationClick,
                            sourceNoteId = message.sourceNoteId,
                            sourceRevision = message.sourceRevision
                        )
                    }
                }
            }

            if (!streaming) {
                val chars by rememberChatBodyCharCount(message.content)
                val stats = remember(
                    chars, message.answerSeconds, message.cachedTokens, message.promptTokens,
                    message.firstTokenMillis, message.generationMillis,
                    message.cachedTokensKnown, message.timestamp
                ) {
                    chars?.let { count ->
                        ChatStats.summary(
                            seconds = message.answerSeconds,
                            cachedTokens = message.cachedTokens,
                            promptTokens = message.promptTokens,
                            chars = count,
                            firstTokenMillis = message.firstTokenMillis,
                            generationMillis = message.generationMillis,
                            cachedTokensKnown = message.cachedTokensKnown,
                            timestamp = message.timestamp
                        )
                    }.orEmpty()
                }
                Column(modifier = Modifier.padding(top = 2.dp, start = 2.dp)) {
                    if (stats.isNotEmpty()) {
                        Text(
                            text = stats,
                            color = LocalOnGradientMuted.current.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            softWrap = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BubbleAction(
                            icon = Icons.Rounded.ContentCopy,
                            label = S.msgCopy,
                            tint = LocalOnGradient.current,
                            onClick = { copyTextToClipboard(context, message.content) }
                        )
                        BubbleAction(
                            icon = Icons.Rounded.Refresh,
                            label = S.msgRegenerate,
                            tint = LocalOnGradient.current,
                            enabled = actionsEnabled,
                            onClick = onRegenerate
                        )
                        BubbleAction(
                            icon = Icons.Rounded.Edit,
                            label = S.msgEdit,
                            tint = LocalOnGradient.current,
                            enabled = actionsEnabled,
                            onClick = onEditAnswer
                        )
                        BubbleAction(
                            icon = Icons.Rounded.Delete,
                            label = S.actionDelete,
                            tint = LocalOnGradient.current,
                            enabled = actionsEnabled,
                            onClick = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BubbleAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motionOn = LocalBackgroundEnvironment.current.motionEnabled
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.30f
            pressed -> 1f
            else -> 0.62f
        },
        animationSpec = tween(130),
        label = "bubbleActionAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && motionOn) 0.96f else 1f,
        animationSpec = LucentMotion.toSpring(LucentMotion.Control),
        label = "bubbleActionScale"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) {
                Haptics.tick(context)
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
