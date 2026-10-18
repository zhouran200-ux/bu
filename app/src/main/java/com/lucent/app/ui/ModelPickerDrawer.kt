package com.lucent.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ModalBottomSheetProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
internal val DrawerSheetProperties = ModalBottomSheetProperties(
    shouldDismissOnBackPress = true,
    shouldDismissOnClickOutside = true
)

internal fun shouldDismissModelDrawer(distance: Float, velocity: Float, threshold: Float): Boolean =
    distance >= threshold || (distance > 0f && velocity >= 1000f)

@OptIn(ExperimentalMaterial3Api::class)
internal class ModelPickerDrawer(
    val modifier: Modifier,
    val handle: @Composable () -> Unit,
    val dragModifier: Modifier,
    val close: (() -> Unit) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberModelPickerDrawer(
    sheetState: SheetState,
    onDismiss: () -> Unit
): ModelPickerDrawer {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    var travel by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    var rebound by remember { mutableStateOf<Job?>(null) }
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val context = LocalContext.current
    val dragInteraction = remember { MutableInteractionSource() }
    val dragged by dragInteraction.collectIsDraggedAsState()
    var handlePressed by remember { mutableStateOf(false) }
    val handleScale by animateFloatAsState(
        targetValue = if (handlePressed || dragged) 1.15f else 1f,
        animationSpec = if (LocalBackgroundEnvironment.current.motionEnabled) tween(120) else snap(),
        label = "drawerHandlePress"
    )
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
    val dragModifier = Modifier.draggable(
        state = drag,
        orientation = Orientation.Vertical,
        enabled = !closing,
        interactionSource = dragInteraction,
        onDragStarted = {
            rebound?.cancel()
            Haptics.tick(context)
        },
        onDragStopped = { velocity ->
            if (shouldDismissModelDrawer(travel, velocity, threshold)) {
                close {}
            } else {
                rebound = scope.launch { animate(travel, 0f) { value, _ -> travel = value } }
            }
        }
    )
    return ModelPickerDrawer(
        modifier = Modifier.graphicsLayer { translationY = travel },
        dragModifier = dragModifier,
        handle = {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(32.dp)
                    .then(dragModifier)
                    .then(Modifier.pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            handlePressed = true
                            try { tryAwaitRelease() } finally { handlePressed = false }
                        })
                    }),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.fillMaxWidth(0.4f).height(5.dp)
                        .graphicsLayer { scaleY = handleScale }
                        .shadow(2.dp, CircleShape)
                        .background(Color.White, CircleShape)
                )
            }
        },
        close = close
    )
}
