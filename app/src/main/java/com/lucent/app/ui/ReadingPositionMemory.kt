package com.lucent.app.ui

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

internal data class ReadingPosition(val itemKey: String, val index: Int, val offset: Int) {
    fun encode(): String = "$index|$offset|$itemKey"

    fun resolve(itemKeys: List<String>): ReadingPosition {
        if (itemKeys.isEmpty()) return ReadingPosition("", 0, 0)
        val anchoredIndex = itemKeys.indexOf(itemKey)
        val resolvedIndex = if (anchoredIndex >= 0) anchoredIndex else index.coerceIn(itemKeys.indices)
        return ReadingPosition(itemKeys[resolvedIndex], resolvedIndex, if (anchoredIndex >= 0) offset.coerceAtLeast(0) else 0)
    }

    companion object {
        fun decode(value: String?): ReadingPosition? {
            val parts = value?.split('|', limit = 3) ?: return null
            if (parts.size != 3 || parts[2].isEmpty()) return null
            val index = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val offset = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return ReadingPosition(parts[2], index, offset)
        }
    }
}

internal object ReadingPositionMemory {
    private const val PREFS = "lucent_reading_positions"

    fun read(context: Context, key: String): ReadingPosition? =
        ReadingPosition.decode(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null))

    fun write(context: Context, key: String, position: ReadingPosition) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encoded = position.encode()
        if (prefs.getString(key, null) != encoded) prefs.edit().putString(key, encoded).apply()
    }

    fun clearNotebook(context: Context, notebookId: Long) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it == "chat:$notebookId" || it.startsWith("chat:$notebookId:") || it.startsWith("source:$notebookId:") }
            .forEach { editor.remove(it) }
        editor.apply()
    }
}

internal fun LazyListState.readingPosition(): ReadingPosition? {
    val layout = layoutInfo
    if (layout.viewportSize.height <= 0) return null
    val index = firstVisibleItemIndex
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
    val itemKey = item.key as? String ?: return null
    return ReadingPosition(itemKey, index, firstVisibleItemScrollOffset)
}

@Composable
internal fun rememberReadingListState(key: String, itemKeys: List<String>): LazyListState {
    val context = LocalContext.current.applicationContext
    val state = remember(key) {
        val position = ReadingPositionMemory.read(context, key)?.resolve(itemKeys)
        LazyListState(position?.index ?: 0, position?.offset ?: 0)
    }
    LaunchedEffect(state, key) {
        snapshotFlow { state.readingPosition() }.conflate().collect {
            delay(250)
            state.readingPosition()?.let { position -> ReadingPositionMemory.write(context, key, position) }
        }
    }
    DisposableEffect(state, key) {
        onDispose { state.readingPosition()?.let { ReadingPositionMemory.write(context, key, it) } }
    }
    return state
}
