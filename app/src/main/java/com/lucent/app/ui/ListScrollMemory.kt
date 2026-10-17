package com.lucent.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

private object ListScrollMemory {
    private val entries = HashMap<String, IntArray>()

    fun read(key: String): IntArray? = entries[key]

    fun write(key: String, index: Int, offset: Int) {
        val slot = entries[key]
        if (slot == null) {
            entries[key] = intArrayOf(index, offset)
        } else {
            slot[0] = index
            slot[1] = offset
        }
    }
}

@Composable
fun rememberRestoredListState(key: String): LazyListState {
    val restored = remember(key) { ListScrollMemory.read(key) }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = restored?.get(0) ?: 0,
        initialFirstVisibleItemScrollOffset = restored?.get(1) ?: 0
    )
    LaunchedEffect(state, key) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (state.layoutInfo.totalItemsCount > 1) ListScrollMemory.write(key, index, offset)
            }
    }
    return state
}
