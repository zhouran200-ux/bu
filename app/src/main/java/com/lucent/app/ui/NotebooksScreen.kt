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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.AppScope
import com.lucent.app.BackClaim
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Notebook
import com.lucent.app.data.NotebookPurge
import dev.chrisbanes.haze.hazeSource
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

    LaunchedEffect(notebooks) {
        if (notebooks.isEmpty()) {
            AppScope.io.launch {
                val existing = db.notebookDao().getAllOnce()
                if (existing.isEmpty()) {
                    db.notebookDao().insert(
                        Notebook(
                            title = "空白笔记本",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
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
        )
        return
    }

    deleting?.let { notebook ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(com.lucent.app.i18n.S.notebookDeleteTitle, fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight) },
            text = { Text(com.lucent.app.i18n.S.notebookDeleteBody(notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle })) },
            confirmButton = {
                TextButton(onClick = {
                    val target = notebook
                    deleting = null
                    AppScope.io.launch {
                        NotebookPurge.purge(db, target.id)
                        LucentToast.show(context, com.lucent.app.i18n.S.notebookDeletedToast)
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
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(state = LocalHazeState.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomBarInset.current + 12.dp)
        ) {
            items(visible, key = { it.id }) { notebook ->
                NotebookShelfItem(
                    notebook = notebook,
                    sourceCount = (countById[notebook.id] ?: 0).coerceAtLeast(1),
                    itemModifier = Modifier
                        .animateItem(placementSpec = placementSpec)
                        .reorderVisuals(notebook.id, reorderState, reorderSlots),
                    reorderModifier = Modifier.reorderableItem(
                        id = notebook.id,
                        listState = listState,
                        state = reorderState,
                        onLongPress = { Haptics.tick(context) },
                        onDrop = { beforeId, afterId -> drop(beforeId, afterId) }
                    ),
                    onOpen = {
                        openNotebookId = notebook.id
                    },
                    onRename = { renaming = notebook },
                    onDelete = { deleting = notebook }
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
    reorderModifier: Modifier,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var menuOpen by remember { mutableStateOf(false) }
    val title = notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle }
    val formattedDate = remember(notebook.updatedAt) { formatMonthDateTime(notebook.updatedAt) }

    Row(
        modifier = itemModifier
            .fillMaxWidth()
            .frostedGlass(cornerRadius = 22.dp)
            .clickable {
                Haptics.tick(context)
                onOpen()
            }
            .then(reorderModifier)
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
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
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
    fun submit() {
        if (name.isBlank() && !allowBlankName) {
            LucentToast.show(context, com.lucent.app.i18n.S.notebookNameRequired)
        } else {
            onConfirm(name)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight) },
        text = {
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
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { submit() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) }
        }
    )
}

