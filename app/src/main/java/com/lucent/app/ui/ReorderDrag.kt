package com.lucent.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.isActive
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

class ReorderDragState internal constructor() {

    var dragging: Boolean by mutableStateOf(false)

    var draggingId: Long? by mutableStateOf(null)

    var dragY: Float by mutableFloatStateOf(0f)

    var moved: Boolean by mutableStateOf(false)

    var gapBeforeId: Long? by mutableStateOf(null)
    var gapAfterId: Long? by mutableStateOf(null)

    var settling: Boolean by mutableStateOf(false)
        private set

    var settleOrder: List<Long>? by mutableStateOf(null)
        private set

    internal var dragToken: Int by mutableStateOf(0)
        private set

    internal var pointerY: Float by mutableFloatStateOf(0f)
    internal var sourceTop: Float by mutableFloatStateOf(0f)
    internal var grabbedY: Float by mutableFloatStateOf(0f)
    internal var draggedSize: IntSize by mutableStateOf(IntSize.Zero)
    internal var overlayDrag: Boolean by mutableStateOf(false)
    private var pointerTravel = 0f

    internal fun grab(id: Long, top: Float, pointer: Float, size: IntSize) {
        begin(id)
        overlayDrag = true
        sourceTop = top
        pointerY = pointer
        grabbedY = pointer - top
        draggedSize = size
    }

    internal fun movePointer(delta: Float, slop: Float) {
        if (!dragging) return
        pointerY += delta
        pointerTravel += delta
        dragY += delta
        if (abs(pointerTravel) > slop) moved = true
    }

    internal fun compensateScroll(consumed: Float) {
        if (!dragging) return
        sourceTop -= consumed
        dragY += consumed
        if (consumed != 0f) moved = true
    }

    internal val overlayTop: Float get() = sourceTop + dragY

    internal fun begin(id: Long) {
        overlayDrag = false
        pointerTravel = 0f
        dragToken += 1
        dragging = true
        settling = false
        settleOrder = null
        draggingId = id
        dragY = 0f
        moved = false
        gapBeforeId = null
        gapAfterId = null
    }

    internal fun finish(order: List<Long>?): Pair<Long?, Long?> {
        val landed = gapBeforeId to gapAfterId
        dragging = false
        if (order != null) {
            settling = true
            settleOrder = order
        } else if (!moved) {
            clear()
        } else {
            settling = true
            settleOrder = null
            gapBeforeId = null
            gapAfterId = null
        }
        return landed
    }

    internal fun cancel() {
        dragging = false
        if (!moved) {
            clear()
        } else {
            settling = true
            settleOrder = null
            gapBeforeId = null
            gapAfterId = null
        }
    }

    internal fun landed() {
        clear()
    }

    private fun clear() {
        dragging = false
        settling = false
        settleOrder = null
        draggingId = null
        overlayDrag = false
        pointerY = 0f
        sourceTop = 0f
        grabbedY = 0f
        draggedSize = IntSize.Zero
        dragY = 0f
        moved = false
        gapBeforeId = null
        gapAfterId = null
    }
}

private fun LazyListState.visibleSlots(): ReorderSlots {
    val info = layoutInfo
    val cards = info.visibleItemsInfo.filter { it.key is Long }
    return ReorderSlots(
        keys = cards.map { it.key as Long },
        offsets = cards.map { IntOffset(0, it.offset) },
        sizes = cards.map { IntSize(info.viewportSize.width, it.size) },
        viewportLeft = 0,
        viewportTop = info.viewportStartOffset,
        viewportRight = info.viewportSize.width,
        viewportBottom = info.viewportEndOffset
    )
}

@Composable
fun rememberListSlots(state: LazyListState): () -> ReorderSlots {
    val cached = remember(state) { derivedStateOf { state.visibleSlots() } }
    return remember(cached) { { cached.value } }
}

private fun jellyPhase(raw: Float, seed: Float): Float {
    val shifted = (raw + seed) % 1f
    val up = if (shifted < 0f) shifted + 1f else shifted
    return if (up <= 0.5f) up * 2f else (1f - up) * 2f
}

@Composable
private fun reorderJelly(active: Boolean): State<Float>? {
    if (!active || !LocalBackgroundEnvironment.current.motionEnabled) return null
    val transition = rememberInfiniteTransition(label = "reorderJelly")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = JELLY_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reorderJellyPhase"
    )
}

@Composable
fun rememberReorderDragState(): ReorderDragState = remember { ReorderDragState() }

fun Modifier.reorderableList(
    listState: LazyListState,
    state: ReorderDragState,
    enabled: Boolean = true,
    onLongPress: () -> Unit,
    onDrop: (beforeId: Long?, afterId: Long?) -> Unit
): Modifier = composed {
    val press by rememberUpdatedState(onLongPress)
    val drop by rememberUpdatedState(onDrop)
    this.pointerInput(listState, state, enabled) {
        if (!enabled) return@pointerInput
        try {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    val info = listState.layoutInfo
                    val y = local.y + info.viewportStartOffset
                    val item = info.visibleItemsInfo.firstOrNull {
                        it.key is Long && y >= it.offset && y < it.offset + it.size
                    }
                    if (item != null) {
                        state.grab(item.key as Long, item.offset.toFloat(), y,
                            IntSize(info.viewportSize.width, item.size))
                        press()
                    }
                },
                onDragEnd = {
                    if (state.dragging) {
                        listState.updateGap(state)
                        val gap = state.finish(if (state.moved) listState.visibleSlots().visibleKeys() else null)
                        drop(gap.first, gap.second)
                    }
                },
                onDragCancel = { if (state.dragging) state.cancel() },
                onDrag = { change, amount ->
                    if (state.dragging) {
                        change.consume()
                        state.movePointer(amount.y, viewConfiguration.touchSlop)
                        listState.updateGap(state)
                    }
                }
            )
        } finally {
            if (state.dragging) state.cancel()
        }
    }
}

private fun LazyListState.updateGap(state: ReorderDragState) {
    val id = state.draggingId ?: return
    val (before, after) = visibleSlots().gapAtY(state.pointerY, id)
    state.gapBeforeId = before
    state.gapAfterId = after
}

@Composable
fun ReorderAutoScrollEffect(listState: LazyListState, state: ReorderDragState) {
    val density = LocalDensity.current
    val zone = with(density) { 72.dp.toPx() }
    val maxSpeed = with(density) { 900.dp.toPx() }
    DisposableEffect(listState, state) {
        onDispose { if (state.dragging) state.cancel() }
    }
    LaunchedEffect(listState, state, state.dragging, state.dragToken, zone, maxSpeed) {
        if (!state.dragging || !state.overlayDrag) return@LaunchedEffect
        val token = state.dragToken
        var previous = withFrameNanos { it }
        while (isActive && state.dragging && state.dragToken == token) {
            val now = withFrameNanos { it }
            if (!state.dragging || state.dragToken != token) break
            val info = listState.layoutInfo
            val velocity = edgeScrollVelocity(state.pointerY, info.viewportStartOffset.toFloat(),
                info.viewportEndOffset.toFloat(), zone, maxSpeed)
            val seconds = ((now - previous).coerceIn(0L, 50_000_000L) / 1_000_000_000f)
            previous = now
            if (velocity != 0f) {
                val consumed = listState.scrollBy(velocity * seconds)
                if (!state.dragging || state.dragToken != token) break
                state.compensateScroll(consumed)
            }
            listState.updateGap(state)
        }
    }
}

fun Modifier.reorderVisuals(
    id: Long,
    state: ReorderDragState,
    slots: () -> ReorderSlots
): Modifier = composed {
    val mine = state.draggingId == id
    val inHand = mine && state.dragging
    val lifted = inHand && state.moved
    val carrying = state.dragging && state.moved
    val active = state.dragging || state.settling
    val motion = LocalBackgroundEnvironment.current.motionEnabled
    val token = state.dragToken
    val jelly = reorderJelly(carrying)
    val wobble by animateFloatAsState(
        targetValue = if (carrying) 1f else 0f,
        animationSpec = tween(durationMillis = JELLY_RAMP_MS),
        label = "reorderWobble"
    )
    val lift by animateFloatAsState(
        targetValue = if (lifted) 1f else 0f,
        animationSpec = if (motion) JELLY_SPRING else JELLY_STILL,
        label = "reorderLift"
    )
    val layout = if (active) slots() else null
    val target = layout?.targetFor(id, state.draggingId, state.gapBeforeId, state.gapAfterId)
    val positioned = target != null
    val targetY = target?.y?.toFloat() ?: 0f
    val at = layout?.offsetOf(id)
    val atY = at?.y?.toFloat() ?: 0f
    val travelY = remember(token) { Animatable(targetY) }
    val returnY = remember(token) { Animatable(0f) }
    val seed = (id % JELLY_SEEDS).toInt() * JELLY_SEED_STEP
    val travelSpec = if (motion) JELLY_SPRING else JELLY_STILL
    val density = LocalDensity.current
    val bob = with(density) { JELLY_BOB.toPx() }
    LaunchedEffect(token, positioned, inHand, mine, targetY, atY) {
        if (!positioned || inHand) return@LaunchedEffect
        if (mine) {
            launch { returnY.animateTo(targetY - atY - state.dragY, travelSpec) }
        } else {
            launch { travelY.animateTo(targetY, travelSpec) }
        }
    }
    this
        .zIndex(if (mine) 2f else if (active) 1f else 0f)
        .graphicsLayer {
            val phase = jelly?.value ?: 0f
            val live = if (positioned) slots() else null
            val here = live?.offsetOf(id)
            if (live != null && here != null) {
                val size = live.sizeOf(id)
                val lowY = (live.viewportTop - here.y).toFloat()
                val highY = (live.viewportBottom - (here.y + (size?.height ?: 0))).toFloat()
                if (inHand) {
                    translationX = 0f
                    translationY = boundedTravel(state.dragY, lowY, highY)
                } else if (mine) {
                    translationX = 0f
                    translationY = boundedTravel(state.dragY + returnY.value, lowY, highY)
                } else {
                    var dy = travelY.value - here.y
                    if (motion) dy += bob * jellyPhase(phase, seed) * wobble * JELLY_WOBBLE_GAIN
                    translationX = 0f
                    translationY = dy
                }
            } else {
                translationX = 0f
                translationY = if (inHand) state.dragY else 0f
            }
            if (mine || lift > JELLY_LIFT_MIN) {
                val stretch = 1f + JELLY_STRETCH * phase
                val liftScale = 1f + (LIFT_SCALE - 1f) * lift
                scaleX = liftScale * stretch
                scaleY = liftScale / stretch
                alpha = 1f - (1f - LIFT_ALPHA) * lift
            } else {
                val breathe = JELLY_BREATHE * phase * wobble * JELLY_WOBBLE_GAIN
                scaleX = 1f + breathe * 0.5f
                scaleY = 1f - breathe
                alpha = 1f
            }
        }
}

@Composable
fun ReorderSettleEffect(state: ReorderDragState, slots: () -> ReorderSlots) {
    if (!state.settling) return
    val id = state.draggingId
    val order = state.settleOrder
    LaunchedEffect(state.settling, id, order) {
        if (id != null && order != null) {
            withTimeoutOrNull(JELLY_SETTLE_TIMEOUT_MS) {
                snapshotFlow { slots().visibleKeys() }.first { it != order }
            }
        } else {
            delay(JELLY_RETURN_MS)
        }
        state.landed()
    }
}

val REORDER_SETTLE: FiniteAnimationSpec<IntOffset> =
    spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))

@Composable
fun rememberReorderPlacementSpec(): FiniteAnimationSpec<IntOffset>? {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        armed = true
    }
    return if (armed) REORDER_SETTLE else null
}

private const val LIFT_SCALE = 1.05f
private const val LIFT_ALPHA = 0.93f
private const val JELLY_PERIOD_MS = 620
private const val JELLY_STRETCH = 0.022f
private const val JELLY_BREATHE = 0.016f
private val JELLY_BOB = 3.5.dp
private const val JELLY_WOBBLE_GAIN = 0.6f
private const val JELLY_RAMP_MS = 260
private const val JELLY_LIFT_MIN = 0.002f
private const val JELLY_SEEDS = 5L
private const val JELLY_SEED_STEP = 0.16f
private const val JELLY_SETTLE_TIMEOUT_MS = 900L
private const val JELLY_RETURN_MS = 480L
private val JELLY_SPRING = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
private val JELLY_STILL = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

private fun boundedTravel(delta: Float, lower: Float, upper: Float): Float {
    val low = if (lower < 0f) lower else 0f
    val high = if (upper > 0f) upper else 0f
    return if (delta < low) low else if (delta > high) high else delta
}
