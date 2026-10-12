package com.lucent.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.MarkdownDocumentParser
import com.lucent.app.data.Note
import com.lucent.app.data.NotebookPurge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NotebookSourcesTab(
    notebookId: Long,
    sourceNotes: List<Note>,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
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

    val current = sourceNotes.firstOrNull()
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
            SourceReader(note = current, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SourceReader(note: Note, modifier: Modifier = Modifier) {
    val scroll = rememberSaveable(note.id, saver = ScrollState.Saver) { ScrollState(0) }
    val accent = MaterialTheme.colorScheme.primary
    val sections = remember(note.id, note.body) { splitDocumentSections(note.body) }
    val tops = remember(note.id) { mutableMapOf<Int, Int>() }

    val currentSection by remember {
        derivedStateOf {
            activeSectionNumber(sections.indices.map { tops[it] }, scroll.value)
        }
    }

    var chipVisible by remember { mutableStateOf(true) }
    LaunchedEffect(scroll.isScrollInProgress) {
        if (scroll.isScrollInProgress) {
            chipVisible = false
        } else {
            delay(500)
            chipVisible = true
        }
    }

    Box(modifier = modifier.background(lucentPanelBackground())) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            sections.forEachIndexed { index, section ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (tops[index] == null) {
                                tops[index] = coords.boundsInParent().top.toInt()
                            }
                        }
                ) {
                    MarkdownText(
                        text = section,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }

        AnimatedVisibility(
            visible = sections.size > 1 && chipVisible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$currentSection / ${sections.size}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = LucentTitles.SectionWeight
                    )
                }
            }
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
