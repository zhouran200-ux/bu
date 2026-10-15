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
import androidx.compose.runtime.mutableIntStateOf
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
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.MarkdownDocumentParser
import com.lucent.app.data.Note
import com.lucent.app.data.NotebookPurge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SourceFocus(val noteId: Long, val sliceId: String, val requestId: Long)

@Composable
fun NotebookSourcesTab(
    notebookId: Long,
    sourceNotes: List<Note>,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    focus: SourceFocus? = null
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val accent = MaterialTheme.colorScheme.primary

    val documentMimeTypes = arrayOf(
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        AppScope.io.launch {
            val result = MarkdownDocumentParser.parseUri(context, uri)
            result.onSuccess { parsed ->
                val newId = db.noteDao().insert(
                    Note(
                        title = parsed.title,
                        body = parsed.markdown
                    )
                )
                NotebookPurge.linkNote(db, notebookId, newId)
                onReload()
                LucentToast.show(context, "已导入文档《${parsed.title}》并解析为 Markdown")
            }.onFailure { err ->
                LucentToast.show(context, err.message ?: "解析文件失败")
            }
        }
    }

    var selectedNoteId by remember(notebookId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(focus?.requestId) {
        focus?.let { selectedNoteId = it.noteId }
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
                    text = "暂无关联资料文档",
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
                    .pressableGlass(onClick = { openDocumentLauncher.launch(documentMimeTypes) }, cornerRadius = 20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "上传文档",
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
                    focus = focus,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SourceReader(
    notebookId: Long,
    note: Note,
    focus: SourceFocus?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val blocks = remember(note.id, note.body) { splitRenderBlocks(note.body) }
    val readingKeys = remember(blocks) { blocks.indices.map { "block:$it" } + "footer" }
    val listState = rememberReadingListState("source:$notebookId:${note.id}", readingKeys)
    var highlightIndex by remember(note.id) { mutableIntStateOf(-1) }
    val currentSectionTitle by remember(blocks, note.title, listState) {
        derivedStateOf {
            currentDocumentSectionTitle(blocks, listState.firstVisibleItemIndex, note.title)
        }
    }

    LaunchedEffect(focus?.requestId, note.id, blocks) {
        val target = focus ?: return@LaunchedEffect
        if (target.noteId != note.id) return@LaunchedEffect
        val index = SourceLocator.blockIndexForSlice(note.title, note.body, blocks, target.sliceId)
        if (index < 0) {
            LucentToast.show(context, "未找到该引用的原文位置")
            return@LaunchedEffect
        }
        highlightIndex = index
        listState.scrollToItem(index)
        delay(2600)
        if (highlightIndex == index) highlightIndex = -1
    }

    Box(modifier = modifier.background(lucentPanelBackground())) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 60.dp, end = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(blocks, key = { index, _ -> readingKeys[index] }) { index, block ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (index == highlightIndex) {
                                Modifier.background(accent.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                ) {
                    MarkdownText(
                        text = block.text,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item(key = "footer") { Spacer(modifier = Modifier.height(48.dp)) }
        }

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
