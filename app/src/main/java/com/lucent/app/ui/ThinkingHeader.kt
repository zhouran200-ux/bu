package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S

internal fun thinkingTitle(streaming: Boolean, hasThought: Boolean, answerSeconds: Int): String = when {
    streaming -> S.thinkingStreaming
    !hasThought -> S.thinkingUnsupported
    answerSeconds > 0 -> S.thinkingDoneSeconds(answerSeconds)
    else -> S.thinkingDone
}

@Composable
internal fun ThinkingHeader(
    title: String,
    expanded: Boolean,
    canExpand: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val motionOn = LocalBackgroundEnvironment.current.motionEnabled
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motionOn) LucentMotion.toSpring(LucentMotion.Control) else snap(),
        label = "thinkingHeaderArrow"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .then(
                if (canExpand) {
                    Modifier.clickable {
                        Haptics.tick(context)
                        onToggle()
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(text = title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (canExpand) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) S.thinkingCollapse else S.thinkingExpand,
                tint = accent,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
internal fun ThinkingFullExpandButton(
    fullExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .clickable {
                Haptics.tick(context)
                onToggle()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = if (fullExpanded) S.thinkingReturnWindow else S.thinkingFullExpand,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
