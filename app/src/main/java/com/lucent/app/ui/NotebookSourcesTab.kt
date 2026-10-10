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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.MarkdownDocumentParser
import com.lucent.app.data.Note
import com.lucent.app.data.NotebookItem
import com.lucent.app.data.NotebookPurge
import kotlinx.coroutines.launch

@Composable
fun NotebookSourcesTab(
    notebookId: Long,
    sourceNotes: List<Note>,
    onOpenNote: (Note) -> Unit,
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
                db.notebookDao().insertItem(
                    NotebookItem(
                        notebookId = notebookId,
                        itemKind = NotebookItem.KIND_NOTE,
                        itemId = newId
                    )
                )
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
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
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
                        onTranslate = {
                            LucentToast.show(context, "已切换为全编翻译解析模式")
                        },
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
                title = { Text("添加来源资料", fontWeight = FontWeight.Bold) },
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
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
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
                                    Text("新建笔记本文档", fontSize = 15.sp, color = onGradient)
                                    Text("手动输入标题与正文", fontSize = 12.sp, color = onGradientMuted)
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
                                    AppScope.io.launch {
                                        val newDocId = db.noteDao().insert(
                                            Note(
                                                title = "用户预置文档",
                                                body = "〔CH:1〕 第一百零三章 拥抱\n\n〔S:1-1〕\n她不喜欢有人拍她得屁股。最少我这样做，她是反感的。屁股大，一直是我对自己身材不喜欢得地方，也不喜欢别人盯着看。\n她平时穿衣服得时候也会非常注意不把屁股显出来。跟我做爱得时候尤其不愿意让我对着后面。\n但我确实很喜欢她得屁股。每次拥抱的时候，我会故意低一点点双手从她的腋下穿过去，这样可以拍打她的屁股。\n她每次都会抬头看我，“别再拍了哦。”\n从恋爱到结婚，我常常这样做。我是故意的，用《大话西游》里说的，吐啊吐啊，就习惯了。我觉得这样拍啊拍的，她也会习惯了吧？\n她很喜欢拥抱的感觉。所以我每次都借机这样做。到后来，我拥抱着她的时候，拍她的屁股，她就会拍我的屁股。算是一种互相伤害。只是我不在意这种事，所以拍多了她还是会生气，“别拍了！”\n此时，镜头中那只小黑手再一次的伸过来了，啪！再次拍在她的屁股上。黑丝下的肉臂抖动中，像在驱使一匹骏马。"
                                            )
                                        )
                                        db.notebookDao().insertItem(
                                            NotebookItem(
                                                notebookId = notebookId,
                                                itemKind = NotebookItem.KIND_NOTE,
                                                itemId = newDocId
                                            )
                                        )
                                        onReload()
                                    }
                                    LucentToast.show(context, "已添加用户预置文档")
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = onGradient,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("导入用户预置示例文档", fontSize = 15.sp, color = onGradient)
                                    Text("加载演示用的章节资料", fontSize = 12.sp, color = onGradientMuted)
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
                title = { Text("新建笔记本文档", fontWeight = FontWeight.Bold) },
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
                                db.notebookDao().insertItem(
                                    NotebookItem(
                                        notebookId = notebookId,
                                        itemKind = NotebookItem.KIND_NOTE,
                                        itemId = newId
                                    )
                                )
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

@Composable
private fun SourceNoteCard(
    note: Note,
    onOpen: () -> Unit,
    onTranslate: () -> Unit,
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
                    text = note.title.ifBlank { "用户预置文档" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = onGradient
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${note.body.length} 字符",
                    fontSize = 13.sp,
                    color = onGradientMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTranslate, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Translate,
                        contentDescription = "翻译/解析",
                        tint = Color(0xFF38BDF8),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                        .padding(horizontal = 8.dp)
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                ) {
                    Text(
                        text = note.title.ifBlank { "〔CH:1〕 第一百零三章 拥抱" },
                        color = Color(0xFF38BDF8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(onClick = { LucentToast.show(context, "全篇解析与翻译模式已开启") }) {
                    Icon(
                        Icons.Default.Translate,
                        contentDescription = "翻译",
                        tint = Color(0xFF38BDF8)
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
