package com.lucent.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.MarkdownDocumentParser
import com.lucent.app.data.Note
import com.lucent.app.data.NotebookPurge
import kotlinx.coroutines.launch

@Composable
fun NotebookSourcesTab(
    notebookId: Long,
    sourceNotes: List<Note>,
    onOpenReader: (Note) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    var showAddDialog by remember { mutableStateOf(false) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var noteTitleInput by remember { mutableStateOf("") }
    var noteBodyInput by remember { mutableStateOf("") }

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

    Box(modifier = modifier.fillMaxSize()) {
        if (sourceNotes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "暂无关联资料文档",
                    fontSize = LucentTitles.SectionSize,
                    fontWeight = LucentTitles.SectionWeight,
                    color = onGradient
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "点击右下角 + 按钮上传本地 TXT/Word 文档或新建笔记，构建你的笔记本知识库。",
                    fontSize = 14.sp,
                    color = onGradientMuted,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(sourceNotes, key = { it.id }) { note ->
                    SourceNoteCard(
                        note = note,
                        onOpen = { onOpenReader(note) },
                        onDelete = {
                            AppScope.io.launch {
                                NotebookPurge.releaseNote(context, db, notebookId, note.id)
                                onReload()
                            }
                            LucentToast.show(context, "已删除该文档")
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0284C7),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .navigationBarsPadding()
                .size(58.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { showAddDialog = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "添加资料",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("添加来源资料", fontSize = LucentTitles.SectionSize, fontWeight = LucentTitles.SectionWeight) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.20f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                                .clickable {
                                    showAddDialog = false
                                    openDocumentLauncher.launch(
                                        arrayOf(
                                            "text/plain",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        )
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "上传本地文档 (TXT / Word)",
                                        fontSize = LucentTitles.ItemSize,
                                        fontWeight = LucentTitles.ItemWeight,
                                        color = onGradient
                                    )
                                    Text(
                                        "仅支持 .txt 与 .docx，自动解析为 Markdown",
                                        fontSize = 12.sp,
                                        color = onGradientMuted
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = onGradient.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAddDialog = false
                                    showCreateNoteDialog = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NoteAdd,
                                    contentDescription = null,
                                    tint = onGradient,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("新建笔记本文档", fontSize = LucentTitles.ItemSize, fontWeight = LucentTitles.ItemWeight, color = onGradient)
                                    Text("手动输入标题与正文", fontSize = 12.sp, color = onGradientMuted)
                                }
                            }
                        }

                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        if (showCreateNoteDialog) {
            AlertDialog(
                onDismissRequest = { showCreateNoteDialog = false },
                title = { Text("新建笔记本文档", fontSize = LucentTitles.SectionSize, fontWeight = LucentTitles.SectionWeight) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = noteTitleInput,
                            onValueChange = { noteTitleInput = it },
                            label = { Text("文档标题") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = noteBodyInput,
                            onValueChange = { noteBodyInput = it },
                            label = { Text("文档内容") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val title = noteTitleInput.ifBlank { "未命名文档" }
                            val body = noteBodyInput
                            showCreateNoteDialog = false
                            noteTitleInput = ""
                            noteBodyInput = ""
                            AppScope.io.launch {
                                val newId = db.noteDao().insert(Note(title = title, body = body))
                                NotebookPurge.linkNote(db, notebookId, newId)
                                onReload()
                            }
                            LucentToast.show(context, "关联文档已添加")
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateNoteDialog = false }) {
                        Text("取消")
                    }
                }
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

@Composable
private fun SourceNoteCard(
    note: Note,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(cornerRadius = 18.dp)
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0284C7).copy(alpha = 0.25f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifBlank { com.lucent.app.i18n.S.untitled },
                    fontSize = LucentTitles.SectionSize,
                    fontWeight = LucentTitles.SectionWeight,
                    color = onGradient
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = previewExcerpt(note.body).ifBlank { "暂无可预览内容" },
                    fontSize = 12.sp,
                    color = onGradientMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${note.body.length} 字符",
                    fontSize = 12.sp,
                    color = onGradientMuted.copy(alpha = onGradientMuted.alpha * 0.7f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "预览",
                        tint = onGradientMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = onGradientMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SourceReaderOverlay(
    note: Note,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current

    Surface(
        color = (if (isDarkGlass()) LucentGlass.HazeContainerDark else LucentGlass.HazeContainerLight)
            .copy(alpha = 0.86f),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = onGradient
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.30f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                ) {
                    Text(
                        text = note.title.ifBlank { com.lucent.app.i18n.S.untitled },
                        color = Color(0xFF38BDF8),
                        fontSize = LucentTitles.SectionSize,
                        fontWeight = LucentTitles.SectionWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(14.dp)
                    .frostedGlass(cornerRadius = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownText(
                        text = note.body,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
