package com.lucent.app.ui

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    fun readSourceDocument(context: Context, keys: SourceReadingMemoryKeys): ReadingPosition? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = ReadingPosition.decode(prefs.getString(keys.document, null))
        val legacy = ReadingPosition.decode(prefs.getString(keys.legacy, null))
        if (prefs.contains(keys.legacy)) {
            val editor = prefs.edit().remove(keys.legacy)
            if (saved == null && legacy != null) editor.putString(keys.document, legacy.encode())
            editor.apply()
        }
        return saved ?: legacy
    }

    fun readSourcePage(context: Context, keys: SourceReadingMemoryKeys): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(keys.page, false)

    fun writeSourcePage(context: Context, keys: SourceReadingMemoryKeys, model: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(keys.page, false) != model) prefs.edit().putBoolean(keys.page, model).apply()
    }

    fun clearNotebook(context: Context, notebookId: Long) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it == "chat:$notebookId" || it.startsWith("chat:$notebookId:") || it.startsWith("source:$notebookId:") }
            .forEach { editor.remove(it) }
        editor.apply()
        val chatPrefs = context.applicationContext.getSharedPreferences("lucent_notebook_chat_prefs", Context.MODE_PRIVATE)
        val chatEditor = chatPrefs.edit()
        val suffix = "_$notebookId"
        chatPrefs.all.keys.filter { it.endsWith(suffix) }.forEach { chatEditor.remove(it) }
        chatEditor.apply()
    }
}

internal fun LazyListState.readingPosition(): ReadingPosition? {
    val layout = layoutInfo
    if (layout.viewportSize.height <= 0) return null
    val index = firstVisibleItemIndex
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
    val itemKey = when (val rawKey = item.key) {
        is String -> rawKey
        is Long -> "notebook:$rawKey"
        else -> return null
    }
    return ReadingPosition(itemKey, index, firstVisibleItemScrollOffset)
}

@Composable
internal fun rememberReadingListState(
    key: String,
    itemKeys: List<String>,
    persist: Boolean = true
): LazyListState {
    val context = LocalContext.current.applicationContext
    val savedPosition = remember(key) { ReadingPositionMemory.read(context, key) }
    var restored by remember(key) { mutableStateOf(itemKeys.isNotEmpty() || savedPosition == null) }
    val state = remember(key) {
        val position = savedPosition?.resolve(itemKeys)
        LazyListState(position?.index ?: 0, position?.offset ?: 0)
    }
    LaunchedEffect(key, itemKeys.isNotEmpty()) {
        if (!restored && itemKeys.isNotEmpty() && savedPosition != null) {
            val resolved = savedPosition.resolve(itemKeys)
            restored = true
            state.scrollToItem(resolved.index, resolved.offset)
        }
    }
    if (persist) RememberReadingPosition(key) { if (restored) state.readingPosition() else null }
    return state
}

@Composable
internal fun RememberReadingPosition(key: String, position: () -> ReadingPosition?) {
    val context = LocalContext.current.applicationContext
    val currentPosition = remember(key) { mutableStateOf(position) }
    currentPosition.value = position
    LaunchedEffect(key) {
        snapshotFlow { currentPosition.value() }.conflate().collect {
            delay(250)
            currentPosition.value()?.let { ReadingPositionMemory.write(context, key, it) }
        }
    }
    DisposableEffect(key) {
        onDispose { currentPosition.value()?.let { ReadingPositionMemory.write(context, key, it) } }
    }
}

internal data class SourceReadingMemoryKeys(val notebookId: Long, val sourceId: Long, val revision: String) {
    val legacy = "source:$notebookId:$sourceId:slices:$revision"
    val document = "$legacy:document"
    val model = "$legacy:model"
    val page = "$legacy:page"
}

internal fun ReadingPosition.forSourceLayout(itemKeys: List<String>): ReadingPosition =
    resolve(itemKeys).copy(offset = 0)
