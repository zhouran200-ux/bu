package com.lucent.app.ui

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ReasoningPreset

@Composable
internal fun BottomGlassComposer(
    modifier: Modifier = Modifier,
    inputText: String,
    onInputChange: (String) -> Unit,
    showReasoningMenu: Boolean,
    onReasoningMenuChange: (Boolean) -> Unit,
    onReasoningButtonBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    reasoningPreset: ReasoningPreset,
    onSelectReasoning: (ReasoningPreset) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDialogueSettings: () -> Unit,
    selectedProfileName: String,
    onOpenModelPicker: () -> Unit,
    onModelPickerWidth: (Int) -> Unit,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val context = LocalContext.current
    androidx.activity.compose.BackHandler(enabled = showReasoningMenu) { onReasoningMenuChange(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        modifier = modifier.foregroundGlass(cornerRadius = 24.dp, reading = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                textStyle = TextStyle(fontSize = 15.sp, color = onGradient),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp, max = 130.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "输入问题或针对笔记本提问...",
                            color = onGradientMuted.copy(alpha = 0.65f),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        val isHighIntensity = reasoningPreset == ReasoningPreset.DEEP || reasoningPreset == ReasoningPreset.MAXIMUM
                        val isThinkingActive = reasoningPreset != ReasoningPreset.AUTO
                        val thinkingBg = if (isHighIntensity) {
                            accent.copy(alpha = 0.85f)
                        } else if (isThinkingActive) {
                            accent.copy(alpha = 0.25f)
                        } else {
                            onGradient.copy(alpha = 0.08f)
                        }
                        val thinkingTint = if (isHighIntensity) {
                            onAccent
                        } else if (isThinkingActive) {
                            accent
                        } else {
                            onGradientMuted
                        }

                        Surface(
                            shape = CircleShape,
                            color = thinkingBg,
                            modifier = Modifier
                                .size(width = 36.dp, height = 24.dp)
                                .clip(CircleShape)
                                .onGloballyPositioned { onReasoningButtonBounds(it.boundsInWindow()) }
                                .clickable { onReasoningMenuChange(!showReasoningMenu) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    LucentIcons.presetIcon(reasoningPreset),
                                    contentDescription = "思考程度",
                                    tint = thinkingTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showReasoningMenu,
                            onDismissRequest = { onReasoningMenuChange(false) },
                            properties = ReasoningPopupProperties,
                            modifier = Modifier.widthIn(min = 176.dp),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = glassPanelColor(),
                            tonalElevation = 0.dp,
                            shadowElevation = 8.dp,
                            border = glassPopupBorder(),
                        ) {
                            Text(
                                text = "思考强度",
                                fontSize = LucentTitles.SectionSize,
                                fontWeight = LucentTitles.SectionWeight,
                                color = onGradientMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            ReasoningPreset.entries.forEach { preset ->
                                val isSelected = reasoningPreset == preset
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = LucentIcons.presetIcon(preset),
                                            contentDescription = null,
                                            tint = if (isSelected) accent else onGradientMuted,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = preset.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) accent else onGradient,
                                                fontSize = LucentTitles.ItemSize
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectReasoning(preset)
                                        onReasoningMenuChange(false)
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = glassControlColor(),
                        modifier = Modifier
                            .size(width = 36.dp, height = 24.dp)
                            .clip(CircleShape)
                            .clickable {
                                Haptics.tick(context)
                                onOpenHistory()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = "对话记录",
                                tint = onGradient,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = glassControlColor(),
                        modifier = Modifier
                            .size(width = 36.dp, height = 24.dp)
                            .clip(CircleShape)
                            .clickable { onOpenDialogueSettings() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "对话设置",
                                tint = onGradient,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = glassControlColor(selected = true),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .onSizeChanged { onModelPickerWidth(it.width) }
                            .clickable { onOpenModelPicker() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accent,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        LucentIcons.Model,
                                        contentDescription = null,
                                        tint = onAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedProfileName.ifBlank { "选择方案" },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "展开模型选择",
                                tint = accent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = when {
                        streaming || inputText.isNotBlank() -> accent
                        else -> onGradient.copy(alpha = 0.15f)
                    },
                    modifier = Modifier
                        .size(width = 44.dp, height = (44f * 2f / 3f).dp)
                        .clip(CircleShape)
                        .clickable(enabled = streaming || inputText.isNotBlank()) {
                            if (streaming) onStop() else onSend()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (streaming) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "停止生成",
                                tint = onAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (inputText.isNotBlank()) onAccent else onGradient.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
