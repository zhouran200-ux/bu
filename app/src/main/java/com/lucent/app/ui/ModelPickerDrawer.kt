package com.lucent.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun shouldDismissModelDrawer(distance: Float, velocity: Float, threshold: Float): Boolean =
    distance >= threshold || (distance > 0f && velocity >= 1000f)

@OptIn(ExperimentalMaterial3Api::class)
internal class ModelPickerDrawer(
    val modifier: Modifier,
    val handle: @Composable () -> Unit,
    val close: (() -> Unit) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberModelPickerDrawer(sheetState: SheetState, onDismiss: () -> Unit): ModelPickerDrawer {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    var travel by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    var rebound by remember { mutableStateOf<Job?>(null) }
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val close: (() -> Unit) -> Unit = { afterHide ->
        if (!closing) {
            closing = true
            rebound?.cancel()
            scope.launch {
                try {
                    sheetState.hide()
                    afterHide()
                    dismiss()
                } finally {
                    closing = false
                    travel = 0f
                }
            }
        }
    }
    val drag = rememberDraggableState { delta -> travel = (travel + delta).coerceAtLeast(0f) }
    return ModelPickerDrawer(
        modifier = Modifier.graphicsLayer { translationY = travel },
        handle = {
            Box(
                modifier = Modifier.width(72.dp).height(40.dp).draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    enabled = !closing,
                    onDragStarted = { rebound?.cancel() },
                    onDragStopped = { velocity ->
                        if (shouldDismissModelDrawer(travel, velocity, threshold)) {
                            close {}
                        } else {
                            rebound = scope.launch { animate(travel, 0f) { value, _ -> travel = value } }
                        }
                    }
                ),
                contentAlignment = Alignment.Center
            ) { BottomSheetDefaults.DragHandle() }
        },
        close = close
    )
}
