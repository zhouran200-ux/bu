package com.lucent.app.ui

import com.lucent.app.i18n.S
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.chat.UiChatMessage
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal data class PendingChatDelete(
    val message: UiChatMessage,
    val index: Int,
    val viewport: ReadingPosition
)

private const val SWIPE_FRACTION = 0.34f
private const val FLING_VELOCITY = 1200f

@Composable
internal fun DeleteUndoBar(
    messageId: Long,
    label: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(0f) }
    var barWidth by remember { mutableStateOf(0f) }
    val motionOn = LocalBackgroundEnvironment.current.motionEnabled

    LaunchedEffect(messageId) {
        slide.snapTo(0f)
        delay(10_000)
        onDismiss()
    }

    Row(
        modifier = Modifier
            .wrapContentWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .onSizeChanged { barWidth = it.width.toFloat() }
            .graphicsLayer {
                translationX = slide.value
                alpha = (1f - abs(slide.value) / barWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch { slide.snapTo(slide.value + delta) }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val travel = barWidth.coerceAtLeast(1f)
                    val distance = abs(slide.value)
                    val kept = distance < travel * SWIPE_FRACTION && abs(velocity) < FLING_VELOCITY
                    val direction = if (distance > 1f) slide.value else velocity
                    scope.launch {
                        if (kept) {
                            if (motionOn) slide.animateTo(0f, LucentMotion.toSpring(LucentMotion.Settle)) else slide.snapTo(0f)
                        } else {
                            val target = if (direction >= 0f) travel else -travel
                            if (motionOn) {
                                slide.animateTo(target, LucentMotion.toSpring(LucentMotion.Firm))
                            } else {
                                slide.snapTo(target)
                            }
                            onDismiss()
                        }
                    }
                }
            )
            .frostedGlass(cornerRadius = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = LocalOnGradient.current,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 14.dp)
        )
        TextButton(
            onClick = {
                Haptics.tick(context)
                onUndo()
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text(text = S.actionUndo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun EditMessageDialog(
    title: String,
    subtitle: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var text by remember { mutableStateOf(initial) }
    val keepEditing = rememberImeVisibleOnOpen()
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    AlertDialog(
        shape = RoundedCornerShape(24.dp),
        containerColor = glassPanelColor(),
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = LucentTitles.SectionSize, fontWeight = LucentTitles.SectionWeight) },
        text = {
            PreserveEditingKeyboardOnOpen(keepEditing, inputFocus)
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(subtitle, color = onGradientMuted, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(onGradient.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    LucentMultilineField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = S.dialogInputPlaceholder,
                        textStyle = TextStyle(fontSize = 14.sp, color = onGradient, lineHeight = 21.sp),
                        placeholderColor = onGradientMuted.copy(alpha = 0.6f),
                        minLines = 5,
                        maxLines = 10,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocus)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.actionCancel) }
        }
    )
}

internal fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Copied Text", text)
    clipboard.setPrimaryClip(clip)
}
