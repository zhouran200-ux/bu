package com.lucent.app.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key as itemKey
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles
import com.lucent.app.i18n.S
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.Haptics
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.edgeScrollVelocity
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberScaleIndication
import com.lucent.app.ui.selectedCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ApiProfileListSection(
    dragState: androidx.compose.runtime.MutableState<ProfileDrag?>,
    profiles: List<ApiProfile>,
    selectedProfileIdx: Int,
    models: List<String>,
    active: Boolean,
    scrollState: ScrollState,
    viewportBounds: Rect?,
    onRequestDeleteProfile: (Int) -> Unit,
    onSelectProfile: (Int) -> Unit,
    onReorderProfiles: (List<ApiProfile>, Int, (Result<Unit>) -> Unit) -> Unit,
    onAddProfile: () -> Unit,
    headerActions: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var drag by dragState
    var rowStride by remember { mutableFloatStateOf(0f) }
    var rootPointer by remember { mutableStateOf<Offset?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val liveViewport = rememberUpdatedState(viewportBounds)
    val density = LocalDensity.current
    val edgeZone = with(density) { 72.dp.toPx() }
    val maxEdgeSpeed = with(density) { 900.dp.toPx() }
    val baseKeys = remember(profiles) { ApiProfiles.orderKeys(profiles) }
    val shownProfiles = drag?.order ?: profiles
    val shownKeys = drag?.keys ?: baseKeys
    val activeProfileIdx = drag?.selected ?: selectedProfileIdx
    val searchActive = searchQuery.trim().isNotEmpty()
    val displayedProfiles = remember(shownProfiles, searchQuery) {
        val text = searchQuery.trim()
        shownProfiles.withIndex().filter {
            text.isEmpty() || it.value.name.contains(text, ignoreCase = true)
        }
    }
    val canReorder = shownProfiles.size > 1 && !searchActive
    val settled = drag == null
    val liveProfiles = rememberUpdatedState(profiles)
    val liveSelected = rememberUpdatedState(selectedProfileIdx)
    val liveReorder = rememberUpdatedState(onReorderProfiles)
    val pageCount = profilePageCount(displayedProfiles.size)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedProfileIdx / PROFILES_PER_PAGE).coerceIn(0, pageCount - 1)
    )
    val currentPage by remember(pageCount) {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, pageCount - 1) }
    }
    val snapBehavior = rememberSnapFlingBehavior(listState)

    LaunchedEffect(selectedProfileIdx, pageCount) {
        if (drag != null || searchActive) return@LaunchedEffect
        val target = (selectedProfileIdx / PROFILES_PER_PAGE).coerceIn(0, pageCount - 1)
        if (listState.firstVisibleItemIndex != target) listState.animateScrollToItem(target)
    }

    LaunchedEffect(searchQuery) {
        if (searchActive) listState.scrollToItem(0)
    }

    fun beginDrag(idx: Int) {
        val current = drag
        if (current != null && current.held >= 0) return
        Haptics.tick(context)
        if (current != null) {
            drag = current.copy(held = idx, travel = 0f)
        } else {
            val base = liveProfiles.value
            drag = ProfileDrag(
                order = base,
                keys = ApiProfiles.orderKeys(base),
                selected = liveSelected.value,
                held = idx,
                travel = 0f
            )
        }
    }

    fun endDrag() {
        rootPointer = null
        val current = drag ?: return
        if (current.held < 0) return
        if (current.order == liveProfiles.value) {
            drag = null
        } else {
            drag = current.copy(held = -1)
            val pending = drag
            liveReorder.value(current.order, current.selected) { _ ->
                if (drag === pending) drag = null
            }
        }
    }

    fun cancelDrag() {
        rootPointer = null
        if (drag?.held?.let { it >= 0 } == true) drag = null
    }

    fun moveDrag(distance: Float) {
        val current = drag ?: return
        val stride = rowStride
        if (current.held < 0 || stride <= 0f) return
        val pageRange = profilePageRange(current.held, shownProfiles.lastIndex)
        var travelled = current.travel + distance
        var next = current
        while (travelled >= stride) {
            val stepped = next.stepped(+1, pageRange)
            if (stepped === next) {
                travelled = 0f
                break
            }
            next = stepped
            travelled -= stride
        }
        while (travelled <= -stride) {
            val stepped = next.stepped(-1, pageRange)
            if (stepped === next) {
                travelled = 0f
                break
            }
            next = stepped
            travelled += stride
        }
        val edge = when {
            next.held == pageRange.first -> travelled.coerceIn(0f, stride)
            next.held == pageRange.last -> travelled.coerceIn(-stride, 0f)
            else -> travelled.coerceIn(-stride, stride)
        }
        drag = next.copy(travel = edge)
        if (next !== current) Haptics.tick(context)
    }

    LaunchedEffect(active) {
        if (!active) cancelDrag()
    }

    LaunchedEffect(active, drag?.held?.let { it >= 0 } == true, scrollState, edgeZone, maxEdgeSpeed) {
        if (!active || drag?.held?.let { it >= 0 } != true) return@LaunchedEffect
        scrollState.scroll {
            var previousFrame = withFrameNanos { it }
            while (drag?.held?.let { it >= 0 } == true) {
                val frame = withFrameNanos { it }
                val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previousFrame = frame
                if (drag?.held?.let { it >= 0 } != true) break
                val pointer = rootPointer ?: continue
                val viewport = liveViewport.value ?: continue
                if (pointer.x < viewport.left || pointer.x > viewport.right) continue
                val velocity = edgeScrollVelocity(pointer.y, viewport.top, viewport.bottom, edgeZone, maxEdgeSpeed)
                if (velocity != 0f) {
                    val consumed = scrollBy(velocity * seconds)
                    if (consumed != 0f) moveDrag(consumed)
                }
            }
        }
    }



    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                S.apiSelectionTitle,
                color = onGradient,
                fontSize = LucentTitles.SectionSize,
                fontWeight = LucentTitles.SectionWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("${shownProfiles.size}", color = onGradientMuted, fontSize = 11.sp)
            if (pageCount > 1) {
                Spacer(modifier = Modifier.width(6.dp))
                Text("${currentPage + 1}/$pageCount", color = onGradientMuted, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    if (searchOpen) {
                        searchOpen = false
                        searchQuery = ""
                    } else {
                        searchOpen = true
                    }
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searchOpen) S.actionCancel else S.actionSearch,
                    tint = onGradientMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            headerActions()
        }
        Spacer(modifier = Modifier.height(8.dp))
        SectionRule()
        Spacer(modifier = Modifier.height(8.dp))
        if (searchOpen) {
            CompactField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = S.apiSearchProfilesPlaceholder,
                trailing = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = S.actionCancel,
                                tint = onGradientMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (profiles.isEmpty()) {
            Text(S.apiNoneTitle, color = onGradient, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(S.apiNoneBody, color = onGradientMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(3.dp))
        }
        if (displayedProfiles.isNotEmpty()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val pageWidth = maxWidth
                    val fillerHeight = if (rowStride > 0f) {
                        with(density) { rowStride.toDp() }
                    } else {
                        49.dp
                    }
                    LazyRow(
                        state = listState,
                        flingBehavior = snapBehavior,
                        userScrollEnabled = pageCount > 1 && settled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(count = pageCount) { page ->
                            Column(modifier = Modifier.width(pageWidth)) {
                                val pageStart = page * PROFILES_PER_PAGE
                                val pageEnd = minOf(pageStart + PROFILES_PER_PAGE, displayedProfiles.size)
                                for (position in pageStart until pageEnd) {
                                    val entry = displayedProfiles[position]
                                    val idx = entry.index
                                    val p = entry.value
                                    val identity = shownKeys[idx]
                                    itemKey(identity) {
                                        var restingIdx by remember { mutableIntStateOf(idx) }
                                        val slide = remember { Animatable(0f) }
                                        val liveIdx = rememberUpdatedState(idx)
                                        var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                        val held = drag?.held == idx
                                        val isActive = idx == activeProfileIdx
                                        LaunchedEffect(idx, held) {
                                            val stride = rowStride
                                            if (restingIdx != idx) {
                                                val shift = (restingIdx - idx) * stride
                                                restingIdx = idx
                                                if (held || stride <= 0f) {
                                                    slide.snapTo(0f)
                                                } else {
                                                    slide.snapTo(shift)
                                                    slide.animateTo(0f, PROFILE_SLIDE_SPRING)
                                                }
                                            } else if (!held) {
                                                val residual = drag?.travel ?: 0f
                                                if (residual != 0f && stride > 0f) {
                                                    slide.snapTo(residual)
                                                    slide.animateTo(0f, PROFILE_SLIDE_SPRING)
                                                }
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .zIndex(if (held) 1f else 0f)
                                                .fillMaxWidth()
                                                .onSizeChanged { if (it.height > 0) rowStride = it.height.toFloat() }
                                                .padding(bottom = 3.dp)
                                                .graphicsLayer {
                                                    val carried = drag?.travel ?: 0f
                                                    translationY = slide.value + (if (held) carried else 0f)
                                                    scaleX = if (held) HELD_SCALE else 1f
                                                    scaleY = if (held) HELD_SCALE else 1f
                                                    alpha = if (held) HELD_ALPHA else 1f
                                                }
                                                .frostedGlass(cornerRadius = ROW_CARD_RADIUS)
                                                .selectedCard(isActive, ROW_CARD_RADIUS, borderWidth = 1.5.dp, washStrength = 1.35f)
                                                .padding(horizontal = 10.dp, vertical = 1.dp)
                                        ) {
                                            if (canReorder) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .onGloballyPositioned { handleCoordinates = it }
                                                        .pointerInput(identity, active) {
                                                            if (!active) return@pointerInput
                                                            try {
                                                                detectDragGesturesAfterLongPress(
                                                                    onDragStart = { position ->
                                                                        val coordinates = handleCoordinates
                                                                        if (coordinates != null && coordinates.isAttached) {
                                                                            beginDrag(liveIdx.value)
                                                                            rootPointer = coordinates.localToRoot(position)
                                                                        }
                                                                    },
                                                                    onDrag = { change, _ ->
                                                                        change.consume()
                                                                        val coordinates = handleCoordinates
                                                                        if (coordinates != null && coordinates.isAttached) {
                                                                            val pointer = coordinates.localToRoot(change.position)
                                                                            val previous = rootPointer
                                                                            rootPointer = pointer
                                                                            if (previous != null) moveDrag(pointer.y - previous.y)
                                                                        } else {
                                                                            cancelDrag()
                                                                        }
                                                                    },
                                                                    onDragEnd = { endDrag() },
                                                                    onDragCancel = { cancelDrag() }
                                                                )
                                                            } finally {
                                                                if (drag?.keys?.getOrNull(drag?.held ?: -1) == identity) cancelDrag()
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.DragHandle,
                                                        contentDescription = S.a11yDragToReorder,
                                                        tint = onGradientMuted.copy(alpha = onGradientMuted.alpha * 0.7f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .selectable(
                                                        selected = isActive,
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = rememberScaleIndication(),
                                                        enabled = settled,
                                                        role = Role.RadioButton,
                                                        onClick = {
                                                            Haptics.tick(context)
                                                            onSelectProfile(idx)
                                                        }
                                                    )
                                            ) {
                                                Text(
                                                    text = p.name.ifBlank { S.apiProfileDefaultName(idx + 1) },
                                                    color = onGradient,
                                                    fontSize = LucentTitles.ItemSize,
                                                    fontWeight = if (isActive) FontWeight.Bold else LucentTitles.ItemWeight,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val modelCount = if (idx == activeProfileIdx) models.size else p.selectedModels.size
                                                Text(
                                                    text = S.modelCountLabel(modelCount),
                                                    color = onGradientMuted,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            DeleteWithConfirmButton(
                                                onDelete = { if (settled) onRequestDeleteProfile(idx) },
                                                tint = onGradientMuted,
                                                buttonSize = 30.dp,
                                                iconSize = 18.dp,
                                                confirmTextPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                confirmTextSize = 11.sp,
                                                contentDescription = S.apiDeleteA11y
                                            )
                                        }
                                    }
                                }
                                if (pageCount > 1) {
                                    repeat(PROFILES_PER_PAGE - (pageEnd - pageStart)) {
                                        Spacer(modifier = Modifier.height(fillerHeight))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (searchActive) {
            Text(
                text = S.apiSearchNoResults,
                color = onGradientMuted,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        GlassButton(
            text = S.apiAddButton,
            icon = Icons.Default.Add,
            enabled = settled,
            onClick = onAddProfile
        )
    }
}
