package com.lucent.app.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val SWIPE_EXIT_MS = 160

const val SWIPE_ENTER_MS = 220

const val SWIPE_RESIST = 0.33f

private val CapsuleLabelLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

@Composable
fun GlassCapsuleButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onGradient = LocalOnGradient.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = RoundedCornerShape(percent = 50)
    val glassDark = isDarkGlass()
    val fill = Color.White.copy(
        alpha = if (glassDark) LucentGlass.CARD_FILL_DARK else LucentGlass.CARD_FILL_LIGHT
    )
    val rim = lucentGlassRim(strong = true)
    Row(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, rim, shape)
            .clickable {
                Haptics.tick(context)
                onClick()
            }
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = onGradient)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            color = onGradient,
            fontSize = 16.sp,
            style = LocalTextStyle.current.copy(lineHeightStyle = CapsuleLabelLineHeight)
        )
    }
}

val COMPOSER_ACTION_HEIGHT = 52.dp

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    danger: Boolean = false,
    compact: Boolean = false
) {
    val onGradient = LocalOnGradient.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = RoundedCornerShape(percent = 50)
    val glassDark = isDarkGlass()

    val padH = if (compact) 14.dp else 22.dp
    val padV = if (compact) 8.dp else 13.dp
    val iconSize = if (compact) 15.dp else 18.dp
    val iconGap = if (compact) 6.dp else 8.dp
    val labelSize = if (compact) 13.sp else 15.sp

    val dangerFill = DANGER_RED
    val dangerRim = DANGER_RED_RIM
    val fill = when {
        danger -> dangerFill
        glassDark -> Color.White.copy(alpha = LucentGlass.CARD_FILL_DARK)
        else -> Color.White.copy(alpha = LucentGlass.CARD_FILL_LIGHT)
    }
    val label = if (danger) Color.White else onGradient
    val fade = if (enabled) 1f else 0.38f

    Row(
        modifier = modifier
            .clip(shape)
            .background(fill.copy(alpha = fill.alpha * fade))
            .then(
                if (danger) Modifier.border(1.dp, dangerRim.copy(alpha = dangerRim.alpha * fade), shape)
                else Modifier.border(1.dp, lucentGlassRim(strong = true), shape)
            )
            .clickable(enabled = enabled) {
                Haptics.tick(context)
                onClick()
            }
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = label.copy(alpha = label.alpha * fade), modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(iconGap))
        }
        Text(
            text,
            color = label.copy(alpha = label.alpha * fade),
            fontSize = labelSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(lineHeightStyle = CapsuleLabelLineHeight)
        )
    }
}

@Composable
fun AttachmentSection(
    attachments: List<com.lucent.app.data.Attachment>,
    onPick: () -> Unit,
    onRemove: (com.lucent.app.data.Attachment) -> Unit,
    modifier: Modifier = Modifier,
    onRename: ((com.lucent.app.data.Attachment, String) -> Unit)? = null,
    onReorder: ((Int, Int) -> Unit)? = null
) {
    val onGradientMuted = LocalOnGradientMuted.current
    Column(modifier = modifier.fillMaxWidth()) {
        ComposerRow(
            icon = Icons.Default.AttachFile,
            label = com.lucent.app.i18n.S.attachFile,
            onClick = onPick
        )
        if (attachments.isNotEmpty()) {
            PendingAttachmentChips(attachments, onGradientMuted, onRemove, onRename, onReorder)
        }
    }
}

@Composable
fun CompletedCheckbox(modifier: Modifier = Modifier, boxSize: Dp = 22.dp) {
    val boxColor = LocalOnGradient.current
    val checkColor = if (boxColor.luminance() > 0.5f) Color(0xFF20202B) else Color.White
    Canvas(modifier = modifier.size(boxSize)) {
        val s = size.minDimension
        val radius = s * 0.28f
        drawRoundRect(
            color = boxColor,
            topLeft = Offset.Zero,
            size = Size(s, s),
            cornerRadius = CornerRadius(radius, radius)
        )
        val check = Path().apply {
            moveTo(s * 0.24f, s * 0.53f)
            lineTo(s * 0.42f, s * 0.72f)
            lineTo(s * 0.77f, s * 0.30f)
        }
        drawPath(
            path = check,
            color = checkColor,
            style = Stroke(width = s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun NewItemButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val onGradient = LocalOnGradient.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = RoundedCornerShape(percent = 50)
    val plusDark = isDarkGlass()
    val plusFill = Color.White.copy(alpha = if (plusDark) 0.13f else 0.30f)
    val plusRim = if (plusDark) Color.White.copy(alpha = 0.24f) else onGradient.copy(alpha = 0.20f)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(plusFill)
            .border(1.dp, plusRim, shape)
            .clickable {
                Haptics.tick(context)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Add, contentDescription = contentDescription, tint = onGradient)
    }
}

@Composable
fun EmptyState(
    isFiltered: Boolean,
    emptyMessage: String,
    noMatchMessage: String,
    modifier: Modifier = Modifier
) {
    val onGradientMuted = LocalOnGradientMuted.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(24.dp)
    ) {
        Text(
            if (isFiltered) noMatchMessage else emptyMessage,
            color = onGradientMuted,
            fontSize = 14.sp
        )
    }
}



@Composable
fun ScrollEdgeJumpButtons(
    canUp: Boolean,
    canDown: Boolean,
    tint: Color,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!canUp && !canDown) return

    val jumpUp = canUp
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            .clickable(onClick = if (jumpUp) onUp else onDown),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (jumpUp) Icons.Default.KeyboardDoubleArrowUp else Icons.Default.KeyboardDoubleArrowDown,
            contentDescription = if (jumpUp) com.lucent.app.i18n.S.a11yScrollToTop else com.lucent.app.i18n.S.a11yScrollToBottom,
            tint = tint
        )
    }
}


@Composable
fun LucentExpandedInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    title: String,
    onCollapse: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCollapse,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .background(
                    if (onGradient.luminance() > 0.5f) Color(0xFF20202B) else Color(0xFFF4F4F8)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, color = onGradient, fontSize = 18.sp)
                androidx.compose.material3.IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(32.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = com.lucent.app.i18n.S.collapseTextBox,
                        tint = onGradientMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = onGradientMuted) },
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = onGradient),
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}
