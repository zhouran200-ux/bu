package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.AppScope
import com.lucent.app.BackClaim
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Notebook
import com.lucent.app.data.NotebookPurge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun NotebooksScreen(
    active: Boolean = true
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val notebooks by db.notebookDao().getAll().collectAsState(initial = emptyList())
    val counts by db.notebookDao().itemCounts().collectAsState(initial = emptyList())
    val countById = remember(counts) { counts.associate { it.notebookId to it.count } }

    var openNotebookId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(openNotebookId) {
        AppNavigation.activeNotebookId = openNotebookId
    }

    var creating by remember { mutableStateOf(false) }
    var nameForNew by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }

    LaunchedEffect(AppNavigation.createNotebookRequested, active) {
        if (active && AppNavigation.consumeCreateNotebook()) {
            creating = true
        }
    }

    val reorderState = rememberReorderDragState()
    val listState = rememberRestoredListState("NotebooksScreen#list")
    val reorderSlots = rememberListSlots(listState)
    ReorderSettleEffect(reorderState, reorderSlots)
    if (active && openNotebookId == null) ReorderAutoScrollEffect(listState, reorderState)
    LaunchedEffect(active, openNotebookId) {
        if (!active || openNotebookId != null) reorderState.cancel()
    }
    val placementSpec = rememberReorderPlacementSpec()

    BackClaim(active && openNotebookId != null)
    BackHandler(enabled = active && openNotebookId != null) {
        openNotebookId = null
    }

    val open = openNotebookId
    if (open != null) {
        NotebookDetailScreen(
            notebookId = open,
            onBack = { openNotebookId = null },
            active = active,
        )
        return
    }

    deleting?.let { notebook ->
        AlertDialog(
            modifier = Modifier.glassPanel(24.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            onDismissRequest = { deleting = null },
            title = { KeepKeyboardForNonEditingDialog(); Text(com.lucent.app.i18n.S.notebookDeleteTitle, fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight) },
            text = { Text(com.lucent.app.i18n.S.notebookDeleteBody(notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle })) },
            confirmButton = {
                TextButton(onClick = {
                    val target = notebook
                    deleting = null
                    AppScope.io.launch {
                        try {
                            NotebookPurge.purge(db, target.id)
                            ReadingPositionMemory.clearNotebook(context, target.id)
                            LucentToast.show(context, com.lucent.app.i18n.S.notebookDeletedToast)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            LucentToast.show(context, e.message ?: "删除失败，资料库事务已回滚")
                        }
                    }
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        )
    }

    if (creating) {
        NotebookNameDialog(
            title = com.lucent.app.i18n.S.notebookNew,
            confirmLabel = com.lucent.app.i18n.S.notebookCreate,
            initialName = nameForNew,
            allowBlankName = true,
            placeholder = nextDefaultNotebookName(notebooks),
            onConfirm = { name ->
                nameForNew = ""
                val finalName = if (name.isBlank()) nextDefaultNotebookName(notebooks) else name.trim()
                creating = false
                scope.launch {
                    val newId = db.notebookDao().insert(Notebook(title = finalName))
                    if (newId > 0) {
                        openNotebookId = newId
                    }
                }
            },
            onDismiss = { creating = false }
        )
    }

    renaming?.let { notebook ->
        NotebookNameDialog(
            title = com.lucent.app.i18n.S.notebookRename,
            confirmLabel = com.lucent.app.i18n.S.notebookRenameAction,
            initialName = notebook.title,
            onConfirm = { name ->
                scope.launch {
                    db.notebookDao().update(
                        notebook.copy(title = name.trim(), updatedAt = System.currentTimeMillis())
                    )
                    LucentToast.show(context, com.lucent.app.i18n.S.notebookRenamedToast)
                }
            },
            onDismiss = { renaming = null }
        )
    }

    val visible = notebooks

    fun drop(beforeId: Long?, afterId: Long?) {
        val movingId = reorderState.draggingId
        val picked: List<Notebook> = visible.filter { it.id == movingId }
        if (picked.isEmpty()) { reorderState.cancel(); return }
        val reordered = reorderedAround(visible, picked, beforeId, afterId) { it.id }
        if (reordered === visible) { reorderState.cancel(); return }
        val changes = reordered.mapIndexedNotNull { index, notebook ->
            val order = index * 1000
            if (notebook.manualOrder == order) null else notebook.copy(manualOrder = order)
        }
        if (changes.isEmpty()) { reorderState.cancel(); return }
        AppScope.io.launch { db.notebookDao().updateAll(changes) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = com.lucent.app.i18n.S.notebooksEmptyHint,
                    color = LocalOnGradientMuted.current,
                    fontSize = LucentTitles.SectionSize,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return@Column
        }

        Box(Modifier.fillMaxSize().clipToBounds()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().reorderableList(
                    listState = listState,
                    state = reorderState,
                    enabled = active && openNotebookId == null,
                    onLongPress = { Haptics.tick(context) },
                    onDrop = { beforeId, afterId -> drop(beforeId, afterId) }
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomBarInset.current + 12.dp)
            ) {
                items(visible, key = { it.id }) { notebook ->
                    NotebookShelfItem(
                        notebook = notebook,
                        sourceCount = notebookSourceCount(countById[notebook.id]),
                        itemModifier = Modifier
                            .animateItem(placementSpec = placementSpec)
                            .reorderVisuals(notebook.id, reorderState, reorderSlots)
                            .graphicsLayer {
                                if (reorderState.dragging && reorderState.draggingId == notebook.id) alpha = 0f
                            },
                        interactive = !reorderState.dragging,
                        onOpen = { openNotebookId = notebook.id },
                        onRename = { renaming = notebook },
                        onDelete = { deleting = notebook }
                    )
                }
            }
            val grabbed = visible.firstOrNull { it.id == reorderState.draggingId }
            if (reorderState.dragging && grabbed != null) {
                val density = LocalDensity.current
                NotebookShelfItem(
                    notebook = grabbed,
                    sourceCount = notebookSourceCount(countById[grabbed.id]),
                    itemModifier = Modifier
                        .offset {
                            IntOffset(0, (reorderState.overlayTop - listState.layoutInfo.viewportStartOffset).roundToInt())
                        }
                        .requiredSize(
                            with(density) { reorderState.draggedSize.width.toDp() },
                            with(density) { reorderState.draggedSize.height.toDp() }
                        ),
                    interactive = false,
                    onOpen = {}, onRename = {}, onDelete = {}
                )
            }
        }
    }
}

private val monthDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 · HH:mm", Locale.getDefault())

private fun formatMonthDateTime(millis: Long): String {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return zoned.format(monthDateTimeFormatter)
}

@Composable
private fun NotebookShelfItem(
    notebook: Notebook,
    sourceCount: Int,
    itemModifier: Modifier,
    interactive: Boolean = true,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = interactive && menuOpen) { menuOpen = false }
    LaunchedEffect(interactive) { if (!interactive) menuOpen = false }
    val title = notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle }
    val formattedDate = remember(notebook.updatedAt) { formatMonthDateTime(notebook.updatedAt) }

    Row(
        modifier = itemModifier
            .fillMaxWidth()
            .frostedGlass(cornerRadius = 22.dp)
            .clickable(enabled = interactive) {
                Haptics.tick(context)
                onOpen()
            }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = LucentTitles.titleInk(onGradient),
                fontSize = LucentTitles.EntrySize,
                fontWeight = LucentTitles.EntryWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$sourceCount 个来源 · $formattedDate",
                color = onGradientMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(
                onClick = { menuOpen = true },
                enabled = interactive,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = com.lucent.app.i18n.S.a11yMoreOptions,
                    tint = onGradientMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            androidx.compose.material3.DropdownMenu(
                modifier = Modifier.glassPanel(20.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                expanded = interactive && menuOpen,
                onDismissRequest = { menuOpen = false },
                properties = NonEditingPopupProperties
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(com.lucent.app.i18n.S.notebookRename) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onRename() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(com.lucent.app.i18n.S.actionDelete) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

fun nextDefaultNotebookName(existingNotebooks: List<Notebook>): String {
    val base = "新建笔记本"
    val existingTitles = existingNotebooks.map { it.title.trim() }.toSet()
    if (base !in existingTitles) return base
    var index = 2
    while ("$base ($index)" in existingTitles) {
        index++
    }
    return "$base ($index)"
}

@Composable
internal fun NotebookNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    allowBlankName: Boolean = false,
    placeholder: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    val keepEditing = rememberImeVisibleOnOpen()
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    fun submit() {
        if (name.isBlank() && !allowBlankName) {
            LucentToast.show(context, com.lucent.app.i18n.S.notebookNameRequired)
        } else {
            onConfirm(name)
            onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        PreserveEditingKeyboardOnOpen(keepEditing, inputFocus)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .glassPanel(24.dp)
                .padding(20.dp)
        ) {
            Text(title, fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight, color = LocalOnGradient.current)
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(com.lucent.app.i18n.S.notebookName) },
                placeholder = if (placeholder.isNotBlank()) {
                    { Text(placeholder, color = LocalOnGradientMuted.current) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(inputFocus)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { submit() }) { Text(confirmLabel) }
            }
        }
    }
}

