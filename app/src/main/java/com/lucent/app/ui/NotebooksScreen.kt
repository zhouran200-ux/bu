package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.AppScope
import com.lucent.app.BackClaim
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Notebook
import com.lucent.app.data.NotebookPurge
import com.lucent.app.data.SettingsRepository
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun NotebooksScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    active: Boolean = true
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val notebooks by db.notebookDao().getAll().collectAsState(initial = emptyList())
    val counts by db.notebookDao().itemCounts().collectAsState(initial = emptyList())
    val countById = remember(counts) { counts.associate { it.notebookId to it.count } }
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    fun recordNotebookOpen(id: Long) {
        AppScope.io.launch {
            runCatching {
                val data = org.json.JSONObject(settingsRepo.notebookOpensOnce())
                val previous = data.optJSONObject(id.toString()) ?: org.json.JSONObject()
                data.put(id.toString(), org.json.JSONObject()
                    .put("last", System.currentTimeMillis())
                    .put("count", previous.optInt("count", 0) + 1))
                settingsRepo.setNotebookOpens(data.toString())
            }
        }
    }

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
                            color = NotebookColor.DEFAULT.key,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    var creating by remember { mutableStateOf(false) }
    var coverForNew by remember { mutableStateOf(NotebookColor.DEFAULT) }
    var nameForNew by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }

    LaunchedEffect(AppNavigation.createNotebookRequested, active) {
        if (active && AppNavigation.consumeCreateNotebook()) {
            creating = true
        }
    }

    var draggingNotebookId by remember { mutableStateOf<Long?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var batchDeleting by remember { mutableStateOf(false) }

    val reorderState = rememberReorderDragState()
    val listState = rememberRestoredListState("NotebooksScreen#list")
    val reorderSlots = rememberListSlots(listState)
    ReorderSettleEffect(reorderState, reorderSlots)
    val placementSpec = rememberReorderPlacementSpec()
    val reorderEnabled = true

    BackClaim(active && (selectionMode || (!showBack && openNotebookId != null)))
    BackHandler(enabled = active && (selectionMode || (!showBack && openNotebookId != null))) {
        when {
            selectionMode -> {
                selectionMode = false
                selectedIds = emptySet()
            }
            else -> openNotebookId = null
        }
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

    if (batchDeleting) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { batchDeleting = false },
            title = { Text(com.lucent.app.i18n.S.notebookDeleteTitle, fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight) },
            text = { Text(com.lucent.app.i18n.S.notebookBatchDeleteBody(count)) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds
                    batchDeleting = false
                    selectionMode = false
                    selectedIds = emptySet()
                    AppScope.io.launch {
                        ids.forEach { id -> NotebookPurge.purge(db, id) }
                    }
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = {
                TextButton(onClick = { batchDeleting = false }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        )
    }

    if (creating) {
        NotebookEditorDialog(
            title = com.lucent.app.i18n.S.notebookNew,
            confirmLabel = com.lucent.app.i18n.S.notebookCreate,
            initialName = nameForNew,
            initialColor = coverForNew,
            showColorPicker = false,
            allowBlankName = true,
            placeholder = nextDefaultNotebookName(notebooks),
            onConfirm = { name, color ->
                nameForNew = ""
                coverForNew = NotebookColor.DEFAULT
                val finalName = if (name.isBlank()) nextDefaultNotebookName(notebooks) else name.trim()
                creating = false
                scope.launch {
                    val newId = db.notebookDao().insert(Notebook(title = finalName, color = color.key))
                    if (newId > 0) {
                        openNotebookId = newId
                        recordNotebookOpen(newId)
                    }
                }
            },
            onDismiss = { creating = false }
        )
    }

    renaming?.let { notebook ->
        NotebookEditorDialog(
            title = com.lucent.app.i18n.S.notebookRename,
            confirmLabel = com.lucent.app.i18n.S.notebookRenameAction,
            initialName = notebook.title,
            initialColor = NotebookColor.fromKey(notebook.color),
            showColorPicker = false,
            onConfirm = { name, _ ->
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

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun drop(beforeId: Long?, afterId: Long?) {
        val movingId = draggingNotebookId ?: reorderState.draggingId
        draggingNotebookId = null
        val picked: List<Notebook> = if (selectionMode && selectedIds.isNotEmpty()) {
            visible.filter { it.id in selectedIds }
        } else {
            visible.filter { it.id == movingId }
        }
        if (picked.isEmpty()) { reorderState.cancel(); return }
        val reordered = reorderedAround(visible, picked, beforeId, afterId) { it.id }
        if (reordered === visible) { reorderState.cancel(); return }
        AppScope.io.launch {
            reordered.forEachIndexed { index, notebook ->
                if (notebook.manualOrder != index * 1000) {
                    db.notebookDao().update(notebook.copy(manualOrder = index * 1000))
                }
            }
        }
        exitSelection()
    }

    fun setPinned(targets: List<Notebook>, pinned: Boolean) {
        AppScope.io.launch {
            targets.forEach { notebook ->
                db.notebookDao().update(notebook.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (showBack) {
            BackHeader(onBack = onBack)
        }

        if (selectionMode) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                IconButton(onClick = { exitSelection() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = com.lucent.app.i18n.S.a11yCancelSelection,
                        tint = onGradient
                    )
                }
                Text(
                    com.lucent.app.i18n.S.nSelected(selectedIds.size),
                    color = onGradient,
                    fontSize = LucentTitles.ScreenSize,
                    fontWeight = LucentTitles.ScreenWeight,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val targets = visible.filter { it.id in selectedIds }
                    val anyUnpinned = targets.any { !it.pinned }
                    setPinned(targets, anyUnpinned)
                    exitSelection()
                }) {
                    Icon(
                        if (visible.filter { it.id in selectedIds }.all { it.pinned }) Icons.Default.PushPin
                        else Icons.Default.PushPin,
                        contentDescription = com.lucent.app.i18n.S.notebookPinA11y,
                        tint = onGradient
                    )
                }
                TextButton(onClick = {
                    val allIds = visible.map { it.id }.toSet()
                    selectedIds = if (selectedIds.containsAll(allIds)) emptySet() else allIds
                }) {
                    Text(
                        if (selectedIds.containsAll(visible.map { it.id }.toSet()) && visible.isNotEmpty())
                            com.lucent.app.i18n.S.clearAllSelection
                        else com.lucent.app.i18n.S.selectAll
                    )
                }
                IconButton(
                    onClick = { if (selectedIds.isNotEmpty()) batchDeleting = true },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = com.lucent.app.i18n.S.a11yDeleteSelected,
                        tint = onGradient
                    )
                }
            }
        }

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
                    selectionMode = selectionMode,
                    selected = notebook.id in selectedIds,
                    itemModifier = Modifier
                        .animateItem(placementSpec = placementSpec)
                        .reorderVisuals(notebook.id, reorderState, reorderSlots, shadow = false),
                    reorderModifier = Modifier.reorderableItem(
                        id = notebook.id,
                        enabled = reorderEnabled,
                        listState = listState,
                        state = reorderState,
                        onLongPress = {
                            draggingNotebookId = notebook.id
                            selectionMode = true
                            if (notebook.id !in selectedIds) selectedIds = selectedIds + notebook.id
                        },
                        onDrop = { beforeId, afterId -> drop(beforeId, afterId) }
                    ).onSecondaryClick {
                        draggingNotebookId = notebook.id
                        selectionMode = true
                        if (notebook.id !in selectedIds) selectedIds = selectedIds + notebook.id
                    },
                    onOpen = {
                        if (selectionMode) {
                            selectedIds = if (notebook.id in selectedIds) selectedIds - notebook.id else selectedIds + notebook.id
                        } else {
                            openNotebookId = notebook.id
                            recordNotebookOpen(notebook.id)
                        }
                    },
                    onToggleSelect = {
                        selectedIds = if (notebook.id in selectedIds) selectedIds - notebook.id else selectedIds + notebook.id
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
    selectionMode: Boolean,
    selected: Boolean,
    itemModifier: Modifier,
    reorderModifier: Modifier,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var menuOpen by remember { mutableStateOf(false) }
    val title = notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle }
    val dark = isDarkGlass()
    val checkTint = if (dark) Color(0xFF14131C) else Color.White
    val formattedDate = remember(notebook.updatedAt) { formatMonthDateTime(notebook.updatedAt) }

    Row(
        modifier = itemModifier
            .fillMaxWidth()
            .frostedGlass(cornerRadius = 22.dp)
            .clickable {
                Haptics.tick(context)
                if (selectionMode) onToggleSelect() else onOpen()
            }
            .then(reorderModifier)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) onGradient else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (selected) onGradient else onGradientMuted.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = com.lucent.app.i18n.S.a11ySelected,
                        tint = checkTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
        }

        NotebookCover(
            colorKey = notebook.color,
            label = title,
            modifier = Modifier.width(26.dp).height(34.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = onGradient,
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

        if (!selectionMode) {
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
}

fun nextDefaultNotebookName(existingNotebooks: List<Notebook>): String {
    val base = "新建笔记本"
    val existingTitles = existingNotebooks.filter { it.trashedAt == null }.map { it.title.trim() }.toSet()
    if (base !in existingTitles) return base
    var index = 2
    while ("$base ($index)" in existingTitles) {
        index++
    }
    return "$base ($index)"
}

@Composable
internal fun NotebookEditorDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialColor: NotebookColor,
    showNameField: Boolean = true,
    showColorPicker: Boolean = false,
    allowBlankName: Boolean = false,
    placeholder: String = "",
    onConfirm: (String, NotebookColor) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showNameField) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(com.lucent.app.i18n.S.notebookName) },
                        placeholder = if (placeholder.isNotBlank()) {
                            { Text(placeholder, color = LocalOnGradientMuted.current) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showColorPicker) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        com.lucent.app.i18n.S.notebookCoverTitle,
                        color = LocalOnGradient.current,
                        fontSize = LucentTitles.SectionSize,
                        fontWeight = LucentTitles.SectionWeight
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NotebookCoverPicker(selected = color, onSelect = { color = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (showNameField && name.isBlank() && !allowBlankName) {
                    LucentToast.show(context, com.lucent.app.i18n.S.notebookNameRequired)
                } else {
                    onConfirm(name, color)
                    onDismiss()
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) }
        }
    )
}

