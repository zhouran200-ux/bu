package com.lucent.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.TypographyScopes
import com.lucent.app.i18n.S
import com.lucent.app.ui.DEFAULT_READING_LINE_SPACING
import com.lucent.app.ui.Haptics
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.StepperSlider
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberLucentFont
import java.util.Locale
import kotlin.math.roundToInt



@Composable
internal fun FontTypographySection(
    fontKey: String,
    fontScale: Float,
    appliedFontScale: Float,
    lineSpacing: Float,
    letterSpacing: Float,
    paragraphSpacing: Float,
    typographyScope: String,
    onFontScaleChange: (Float) -> Unit,
    onFontScaleCommit: () -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onLineSpacingCommit: () -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onLetterSpacingCommit: () -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onParagraphSpacingCommit: () -> Unit,
    onTypographyScopeChange: (String) -> Unit,
    onResetDefaults: () -> Unit
): Unit {
    val context = LocalContext.current
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
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    fontWeight = LucentTitles.SectionWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TypographyScopeToggle(
                    scope = typographyScope,
                    onScopeChange = onTypographyScopeChange
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = onGradient.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, onGradient.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .heightIn(min = 44.dp)
                        .clickable {
                            Haptics.tick(context)
                            onResetDefaults()
                        }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
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
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val previewFamily = rememberLucentFont(fontKey)
        val previewFontSize = 14.sp
        val density = LocalDensity.current
        val previewDensity = Density(
            density.density,
            previewScopedScale(
                inheritedScale = density.fontScale,
                appliedScale = appliedFontScale,
                draftScale = fontScale,
                textOnly = typographyScope == TypographyScopes.TEXT
            )
        )
        val previewLineHeight = (previewFontSize.value * DEFAULT_READING_LINE_SPACING *
            (lineSpacing / DEFAULT_READING_LINE_SPACING)).sp
        val previewLetterSpacing = letterSpacing.sp
        val previewParagraphGap = 6.dp * paragraphSpacing
        val previewStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = previewFamily,
            fontSize = previewFontSize,
            lineHeight = previewLineHeight,
            letterSpacing = previewLetterSpacing
        )

        CompositionLocalProvider(LocalDensity provides previewDensity) {
            Column(verticalArrangement = Arrangement.spacedBy(previewParagraphGap)) {
                Text(
                    text = S.typographyPreviewSample,
                    style = previewStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = onGradient
                )
                Text(
                    text = S.typographyPreviewSampleSecond,
                    style = previewStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onGradient
                )
            }
        }
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
            valueLabel = if (kotlin.math.abs(lineSpacing - 1.2f) < 0.005f) S.fontValueDefault else String.format(Locale.ROOT, "%.2fx", lineSpacing),
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
            valueLabel = if (kotlin.math.abs(letterSpacing) < 0.005f) S.fontValueDefault else String.format(Locale.ROOT, "%+.1fsp", letterSpacing),
            value = letterSpacing,
            valueRange = -0.5f..2.0f,
            step = 0.1f,
            onValueChange = onLetterSpacingChange,
            onValueChangeFinished = {
                onLetterSpacingCommit()

            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = onGradient.copy(alpha = 0.08f)
        )

        StepperSlider(
            compact = true,
            title = S.paragraphSpacingTitle,
            valueLabel = if (kotlin.math.abs(paragraphSpacing - 1f) < 0.005f) S.fontValueDefault else String.format(Locale.ROOT, "%.2fx", paragraphSpacing),
            value = paragraphSpacing,
            valueRange = 0.5f..2.0f,
            step = 0.05f,
            onValueChange = onParagraphSpacingChange,
            onValueChangeFinished = {
                onParagraphSpacingCommit()

            }
        )
    }
}

@Composable
private fun TypographyScopeToggle(
    scope: String,
    onScopeChange: (String) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = onGradient.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, onGradient.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(2.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TypographyScopeOption(
                label = S.typographyScopeGlobal,
                selected = scope != TypographyScopes.TEXT,
                accent = accent,
                muted = onGradientMuted,
                onClick = { onScopeChange(TypographyScopes.GLOBAL) }
            )
            TypographyScopeOption(
                label = S.typographyScopeText,
                selected = scope == TypographyScopes.TEXT,
                accent = accent,
                muted = onGradientMuted,
                onClick = { onScopeChange(TypographyScopes.TEXT) }
            )
        }
    }
}

@Composable
private fun TypographyScopeOption(
    label: String,
    selected: Boolean,
    accent: Color,
    muted: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.20f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton) {
                Haptics.tick(context)
                onClick()
            }
            .heightIn(min = 44.dp)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else muted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
