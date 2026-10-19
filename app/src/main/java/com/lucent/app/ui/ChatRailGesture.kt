package com.lucent.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope

internal suspend fun PointerInputScope.detectChatRailDrag(
    onPress: (Offset) -> Boolean,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (!onPress(down.position)) return@awaitEachGesture
        try {
            down.consume()
            val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (start != null) {
                onDragStart(start.position)
                verticalDrag(start.id) { change ->
                    onDrag(change.position)
                    change.consume()
                }
            }
        } finally {
            onRelease()
        }
    }
}
