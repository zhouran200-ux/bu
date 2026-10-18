package com.lucent.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.StepperSlider
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberLucentFont
import kotlin.math.roundToInt



@Composable
internal fun FontTypographySection(
    fontKey: String,
    fontScale: Float,
    lineSpacing: Float,
    letterSpacing: Float,
    onFontScaleChange: (Float) -> Unit,
    onFontScaleCommit: () -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onLineSpacingCommit: () -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onLetterSpacingCommit: () -> Unit,
    onResetDefaults: () -> Unit
): Unit {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = S.typographyPreviewTitle,
                    color = onGradientMuted,
                    fontSize = LucentTitles.SectionSize,
                    fontWeight = LucentTitles.SectionWeight
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = onGradient.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, onGradient.copy(alpha = 0.16f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onResetDefaults()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = onGradient,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = S.resetTypographyDefaults,
                        color = onGradient,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val previewFamily = rememberLucentFont(fontKey)
        val previewFontSize = (14.sp.value * fontScale).sp
        val previewLineHeight = (previewFontSize.value * 1.35f * lineSpacing).sp
        val previewLetterSpacing = letterSpacing.sp

        Text(
            text = S.typographyPreviewSample,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = previewFamily,
                fontSize = previewFontSize,
                lineHeight = previewLineHeight,
                letterSpacing = previewLetterSpacing
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = onGradient
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        StepperSlider(
            compact = true,
            title = S.fontSizeScaleTitle,
            valueLabel = if (kotlin.math.abs(fontScale - 1f) < 0.005f) S.fontValueDefault else "${(fontScale * 100).roundToInt()}%",
            value = fontScale,
            valueRange = 0.85f..1.30f,
            step = 0.05f,
            onValueChange = onFontScaleChange,
            onValueChangeFinished = {
                onFontScaleCommit()

            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = onGradient.copy(alpha = 0.08f)
        )

        StepperSlider(
            compact = true,
            title = S.lineSpacingTitle,
            valueLabel = if (kotlin.math.abs(lineSpacing - 1.2f) < 0.005f) S.fontValueDefault else String.format("%.2fx", lineSpacing),
            value = lineSpacing,
            valueRange = 1.0f..1.8f,
            step = 0.05f,
            onValueChange = onLineSpacingChange,
            onValueChangeFinished = {
                onLineSpacingCommit()

            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = onGradient.copy(alpha = 0.08f)
        )

        StepperSlider(
            compact = true,
            title = S.letterSpacingTitle,
            valueLabel = if (kotlin.math.abs(letterSpacing) < 0.005f) S.fontValueDefault else String.format("%+.1fsp", letterSpacing),
            value = letterSpacing,
            valueRange = -0.5f..2.0f,
            step = 0.1f,
            onValueChange = onLetterSpacingChange,
            onValueChangeFinished = {
                onLetterSpacingCommit()

            }
        )
    }
}
