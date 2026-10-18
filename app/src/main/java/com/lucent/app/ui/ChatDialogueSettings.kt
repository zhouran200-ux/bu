package com.lucent.app.ui

import com.lucent.app.i18n.S
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DialogueSettingsSheet(
    initialPrompt: String,
    initialHistoryRounds: Int,
    onSave: (prompt: String, historyRounds: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by remember { mutableStateOf(initialPrompt) }
    var historyRounds by remember { mutableIntStateOf(initialHistoryRounds) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drawer = rememberModelPickerDrawer(sheetState, onDismiss = onDismiss)
    val promptScroll = rememberScrollState()
    val keepEditing = rememberImeVisibleOnOpen()
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val ink = LocalOnGradient.current
    val muted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassPanelColor(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp,
        modifier = drawer.modifier,
        properties = DrawerSheetProperties,
        sheetGesturesEnabled = false,
        dragHandle = null
    ) {
        PreserveEditingKeyboardOnOpen(keepEditing, inputFocus)
        drawer.handle()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .heightIn(max = sheetContentHeightLimit())
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .then(drawer.dragModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(64.dp))
                Text(
                    text = S.dialogueTitle,
                    fontSize = LucentTitles.ItemSize,
                    fontWeight = LucentTitles.ItemWeight,
                    color = ink,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
                TextButton(
                    onClick = { onSave(prompt.trim(), historyRounds) },
                    modifier = Modifier.width(64.dp)
                ) {
                    Text(
                        text = S.userAgentSaveButton,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = S.dialogueSystemPrompt,
                    fontSize = 13.sp,
                    color = muted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassControlColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        BasicTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            textStyle = TextStyle(fontSize = 15.sp, color = ink, lineHeight = 22.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 88.dp, max = 188.dp)
                                .verticalScroll(promptScroll)
                                .focusRequester(inputFocus)
                                .semantics { contentDescription = S.dialogueSystemPrompt },
                            decorationBox = { innerTextField ->
                                if (prompt.isEmpty()) {
                                    Text(
                                        text = S.dialoguePromptHint,
                                        color = ink.copy(alpha = 0.40f),
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = S.dialogueCharCount(prompt.length),
                            fontSize = 12.sp,
                            color = ink.copy(alpha = 0.45f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = S.dialogueContext,
                    fontSize = 13.sp,
                    color = muted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassControlColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        StepperSlider(
                            title = S.dialogueHistoryRounds,
                            valueLabel = if (historyRounds == 10) S.dialogueRoundsDefault else S.dialogueRoundsValue(historyRounds),
                            value = historyRounds.toFloat(),
                            valueRange = 1f..20f,
                            step = 1f,
                            onValueChange = { historyRounds = it.toInt() }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = {
                            prompt = ""
                            historyRounds = 10
                        }
                    ) {
                        Text(
                            text = S.resetTypographyDefaults,
                            fontSize = 13.sp,
                            color = muted
                        )
                    }
                }
            }
        }
    }
}
