package com.lucent.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.abs
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
                val noteId = repository.importDocument(notebookId, document)
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
    LaunchedEffect(notebookId, active, focus, sourceNotes) {
        if (!active) return@LaunchedEffect
        focus?.let {
            if (emphasizedFocus?.requestId != it.requestId) emphasizedFocus = null
            if (sourceNotes.any { note -> note.id == it.noteId }) selectedNoteId = it.noteId
            else LucentToast.show(context, S.sourcesCitationMissing)
        }
    }
    LaunchedEffect(sourceNotes) {
        if (sourceNotes.none { it.id == selectedNoteId }) selectedNoteId = sourceNotes.firstOrNull()?.id
    }
    val current = sourceNotes.firstOrNull { it.id == selectedNoteId } ?: sourceNotes.firstOrNull()
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
                    .pressableGlass(onClick = { if (!importing) openDocumentLauncher.launch(documentMimeTypes) }, cornerRadius = 20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = S.sourcesUpload,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        } else {
            key(current.id) {
                SourceReader(
                    notebookId = notebookId,
                    note = current,
                    focus = focus?.takeIf { it.noteId == current.id },
                    active = active,
                    emphasizedFocus = emphasizedFocus,
                    onEmphasize = { emphasizedFocus = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private data class SourceReaderContent(
    val source: IndexedSource,
    val items: List<SourceReadingItem>
)

@Composable
private fun SourceReader(
    notebookId: Long,
    note: Note,
    focus: SourceFocus?,
    active: Boolean,
    emphasizedFocus: SourceFocus?,
    onEmphasize: (SourceFocus?) -> Unit,
    modifier: Modifier = Modifier
) {
    var content by remember(note.id, note.body, note.title) { mutableStateOf<SourceReaderContent?>(null) }
    var error by remember(note.id, note.body, note.title) { mutableStateOf<String?>(null) }
    LaunchedEffect(note.id, note.body, note.title) {
        try {
            content = withContext(Dispatchers.Default) {
                val source = NovelSourceIndex.get(note)
                SourceReaderContent(source, sourceReadingItems(source.body, source.tagged))
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
    key(loaded.source.noteId, loaded.source.revision) {
        SourceReaderDocument(notebookId, loaded, focus, active, emphasizedFocus, onEmphasize, modifier)
    }
}

@Composable
private fun SourceReaderDocument(
    notebookId: Long,
    content: SourceReaderContent,
    focus: SourceFocus?,
    active: Boolean,
    emphasizedFocus: SourceFocus?,
    onEmphasize: (SourceFocus?) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val source = content.source
    val items = content.items
    val motionEnabled = LocalBackgroundEnvironment.current.motionEnabled
    val readingKeys = remember(items) { items.map { it.key } + "footer" }
    val listState = rememberReadingListState("source:$notebookId:${source.noteId}:slices:${source.revision}", readingKeys)
    val currentSectionTitle by remember(items, source.title, listState) {
        derivedStateOf { sourceSectionTitle(items, listState.firstVisibleItemIndex, source.title) }
    }
    var consumedRequest by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(active, focus, source.noteId, source.revision) {
        if (!active) return@LaunchedEffect
        val target = focus ?: return@LaunchedEffect
        if (consumedRequest == target.requestId) return@LaunchedEffect
        consumedRequest = target.requestId
        onEmphasize(null)
        when (val location = sourceFocusLocation(target, source.noteId, source.revision, items)) {
            is SourceFocusLocation.Unavailable -> LucentToast.show(context, location.message)
            is SourceFocusLocation.Found -> {
                onEmphasize(target)
                withFrameNanos { }
                if (motionEnabled && abs(listState.firstVisibleItemIndex - location.index) <= 3) {
                    listState.animateScrollToItem(location.index)
                } else {
                    listState.scrollToItem(location.index)
                }
            }
        }
    }
    Box(modifier = modifier.background(lucentPanelBackground())) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 60.dp, end = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.key }) { _, item ->
                MarkdownText(
                    text = item.text,
                    emphasized = item.sliceId != null && emphasizedFocus?.noteId == source.noteId &&
                        emphasizedFocus.revision == source.revision && emphasizedFocus.sliceId == item.sliceId,
                    citationsEnabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "footer") { Spacer(modifier = Modifier.height(48.dp)) }
        }
        FloatingScrollCapsule(
            listState = listState,
            itemKeys = readingKeys,
            enabled = active,
            label = S.sourcesFastScroll,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = LocalOnGradient.current,
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .widthIn(max = 280.dp)
                .foregroundGlass(12.dp, reading = true)
        ) {
            Text(
                text = currentSectionTitle,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp
                ),
                fontWeight = LucentTitles.SectionWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
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
