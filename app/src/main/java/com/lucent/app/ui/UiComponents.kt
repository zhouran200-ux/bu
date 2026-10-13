package com.lucent.app.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CapsuleLabelLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val onGradient = LocalOnGradient.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = RoundedCornerShape(percent = 50)
    val glassDark = isDarkGlass()

    val padH = 14.dp
    val padV = 8.dp
    val iconSize = 15.dp
    val iconGap = 6.dp
    val labelSize = 13.sp

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = LucentMotion.toSpring(LucentMotion.Settle),
        label = "glassButtonScale"
    )

    val pureWhite = LocalPureWhite.current
    val baseFill = when {
        pureWhite -> LucentGlass.PureWhiteControlFill
        glassDark -> Color.White.copy(alpha = LucentGlass.CARD_FILL_DARK)
        else -> Color.White.copy(alpha = LucentGlass.CARD_FILL_LIGHT)
    }
    val accent = MaterialTheme.colorScheme.primary
    val fill = when {
        pureWhite && pressed && enabled -> LucentGlass.PureWhiteControlFillPressed
        pressed && enabled -> accent.copy(alpha = if (glassDark) 0.22f else 0.16f)
        else -> baseFill
    }
    val label = onGradient
    val fade = if (enabled) 1f else 0.38f

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill.copy(alpha = fill.alpha * fade))
            .border(1.dp, lucentGlassRim(strong = true), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) {
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



