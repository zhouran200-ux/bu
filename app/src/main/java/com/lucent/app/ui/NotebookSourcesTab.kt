package com.lucent.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lucent.app.AppScope
import com.lucent.app.data.NotebookRepository
import com.lucent.app.data.MarkdownDocumentParser
import com.lucent.app.data.Note
import com.lucent.app.data.IndexedSource
import com.lucent.app.data.NovelSourceIndex
import com.lucent.app.i18n.S
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

data class SourceFocus(
    val noteId: Long,
    val sliceId: String,
    val requestId: Long,
    val revision: String? = null
)

@Composable
fun NotebookSourcesTab(
    notebookId: Long,
    sourceNotes: List<Note>,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    focus: SourceFocus? = null,
    active: Boolean = true
) {
    val context = LocalContext.current
    val repository = remember { NotebookRepository.from(context) }
    val accent = MaterialTheme.colorScheme.primary

    val documentMimeTypes = arrayOf(
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )

    var importing by remember(notebookId) { mutableStateOf(false) }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null || importing) return@rememberLauncherForActivityResult
        importing = true
        AppScope.io.launch {
            try {
                val parsed = MarkdownDocumentParser.parseUri(context, uri).getOrThrow()
                val document = Note(title = parsed.title, body = parsed.markdown)
                val noteId = repository.replaceDocument(notebookId, document)
                withContext(Dispatchers.Default) { NovelSourceIndex.get(document.copy(id = noteId)) }
                withContext(Dispatchers.Main) {
                    onReload()
                    LucentToast.show(context, S.sourcesImported(parsed.title))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                withContext(Dispatchers.Main) {
                    LucentToast.show(context, failure.message ?: S.sourcesImportFailed)
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { importing = false }
            }
        }
    }

    var selectedNoteId by remember(notebookId) { mutableStateOf<Long?>(null) }
    var emphasizedFocus by remember(notebookId) { mutableStateOf<SourceFocus?>(null) }
    var confirmingRemove by remember(notebookId) { mutableStateOf(false) }
    LaunchedEffect(notebookId, active, focus, sourceNotes) {
        if (!active) return@LaunchedEffect
        focus?.let {
            if (emphasizedFocus?.requestId != it.requestId) emphasizedFocus = null
            if (sourceNotes.any { note -> note.id == it.noteId }) selectedNoteId = it.noteId
        }
    }
    LaunchedEffect(sourceNotes) {
        if (sourceNotes.none { it.id == selectedNoteId }) selectedNoteId = sourceNotes.firstOrNull()?.id
    }
    val current = sourceNotes.firstOrNull { it.id == selectedNoteId } ?: sourceNotes.firstOrNull()
    if (confirmingRemove && current != null) {
        AlertDialog(
            modifier = Modifier.border(0.75.dp, foregroundRim(), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            containerColor = glassPanelColor(),
            tonalElevation = 0.dp,
            onDismissRequest = { confirmingRemove = false },
            title = {
                KeepKeyboardForNonEditingDialog()
                Text(S.sourcesRemove, fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight)
            },
            text = { Text(S.sourcesRemoveConfirmBody(current.title.ifBlank { S.sourcesRemove })) },
            confirmButton = {
                TextButton(onClick = {
                    Haptics.tick(context)
                    confirmingRemove = false
                    if (!importing) {
                        importing = true
                        AppScope.io.launch {
                            try {
                                repository.removeDocument(notebookId)
                                withContext(Dispatchers.Main) {
                                    onReload()
                                    LucentToast.show(context, S.sourcesRemoved)
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                withContext(Dispatchers.Main) {
                                    LucentToast.show(context, failure.message ?: S.chatDeleteFailed)
                                }
                            } finally {
                                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { importing = false }
                            }
                        }
                    }
                }) {
                    Text(S.sourcesRemove, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    Haptics.tick(context)
                    confirmingRemove = false
                }) {
                    Text(S.actionCancel)
                }
            }
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (current == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = accent.copy(alpha = 0.25f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (importing) S.sourcesImporting else S.sourcesEmpty,
                    fontSize = LucentTitles.SectionSize,
                    fontWeight = LucentTitles.SectionWeight,
                    color = LocalOnGradient.current
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accent,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .navigationBarsPadding()
                    .size(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .pressableGlass(
                        onClick = {
                            if (!importing) {
                                Haptics.tick(context)
                                openDocumentLauncher.launch(documentMimeTypes)
                            }
                        },
                        cornerRadius = 20.dp,
                        enabled = !importing
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = S.sourcesUpload,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        } else {
            key(current.id) {
                CompositionLocalProvider(LocalLiveGlassEnabled provides false) {
                    SourceReader(
                        notebookId = notebookId,
                        note = current,
                        focus = focus?.takeIf { it.noteId == current.id },
                        active = active,
                        emphasizedFocus = emphasizedFocus,
                        onEmphasize = { emphasizedFocus = it },
                        onReplaceDocument = { if (!importing) openDocumentLauncher.launch(documentMimeTypes) },
                        onRemoveDocument = { if (!importing) confirmingRemove = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

internal data class SourceReaderContent(
    val source: IndexedSource,
    val items: List<SourceReadingItem>,
    val previewRows: List<SourcePreviewRow>,
    val markdown: Map<String, MarkdownParseSnapshot>
)

@Composable
internal fun SourceReader(
    notebookId: Long,
    note: Note,
    focus: SourceFocus?,
    active: Boolean,
    emphasizedFocus: SourceFocus?,
    onEmphasize: (SourceFocus?) -> Unit,
    modifier: Modifier = Modifier,
    onReplaceDocument: (() -> Unit)? = null,
    onRemoveDocument: (() -> Unit)? = null
) {
    val sourceStyle = MarkdownStyle(
        LocalOnGradient.current, LocalOnGradientMuted.current, MaterialTheme.colorScheme.primary,
        citationsEnabled = false, emphasized = false, sourceNoteId = null, sourceRevision = null, clickable = false
    )
    var content by remember(notebookId, note.id, note.body, note.title) { mutableStateOf<SourceReaderContent?>(null) }
    var error by remember(notebookId, note.id, note.body, note.title) { mutableStateOf<String?>(null) }
    LaunchedEffect(notebookId, note.id, note.body, note.title, sourceStyle) {
        try {
            content = withContext(Dispatchers.Default) {
                val source = NovelSourceIndex.get(note)
                val items = sourceReadingItems(source.body, source.tagged)
                SourceReaderContent(
                    source,
                    items,
                    sourcePreviewRows(source.body, source.tagged),
                    items.associate { it.key to MarkdownRenderer().render(it.text, sourceStyle, null) }
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: S.sourcesLoadFailed
        }
    }
    val loaded = content
    if (loaded == null) {
        Box(modifier = modifier.background(lucentPanelBackground()), contentAlignment = Alignment.Center) {
            Text(text = error?.let { S.sourcesLoadFailedDetail(it) } ?: S.sourcesLoading, color = LocalOnGradient.current)
        }
        return
    }
    key(notebookId, loaded.source.noteId, loaded.source.revision) {
        SourceReaderDocument(
            notebookId = notebookId,
            content = loaded,
            focus = focus,
            active = active,
            emphasizedFocus = emphasizedFocus,
            onEmphasize = onEmphasize,
            modifier = modifier,
            onReplaceDocument = onReplaceDocument,
            onRemoveDocument = onRemoveDocument
        )
    }
}

internal fun previewExcerpt(body: String, limit: Int = 96): String {
    val flat = body.lineSequence()
        .map { line -> line.trim().trimStart('#', '*', '>', '-').trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (flat.length <= limit) flat else flat.take(limit).trimEnd() + "…"
}
