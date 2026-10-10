package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Note
import com.lucent.app.data.Notebook

enum class NotebookDetailTab {
    CHAT,
    SOURCES
}

@Composable
fun NotebookDetailScreen(
    notebookId: Long,
    onBack: () -> Unit,
    onOpenNote: (Note) -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    var selectedTab by remember { mutableStateOf(NotebookDetailTab.CHAT) }
    var notebook by remember { mutableStateOf<Notebook?>(null) }
    var sourceNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var readerNote by remember { mutableStateOf<Note?>(null) }
    var reloadCount by remember { mutableIntStateOf(0) }

    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    BackHandler {
        if (readerNote != null) {
            readerNote = null
        } else {
            onBack()
        }
    }

    LaunchedEffect(notebookId, reloadCount) {
        notebook = db.notebookDao().getByIdOnce(notebookId)
        val items = db.notebookDao().getItemsOnce(notebookId)
        val itemIds = items.map { it.itemId }
        sourceNotes = if (itemIds.isNotEmpty()) {
            db.noteDao().getByIds(itemIds)
        } else {
            emptyList()
        }
    }

    val title = notebook?.title?.ifBlank { "未命名笔记本" } ?: "笔记本"

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            NotebookDetailHeader(
                selectedTab = selectedTab,
                sourceCount = sourceNotes.size,
                onSelectTab = { selectedTab = it },
                onBack = onBack
            )

            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 200),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { tab ->
                when (tab) {
                    NotebookDetailTab.CHAT -> {
                        NotebookChatTab(
                            notebookId = notebookId,
                            notebookTitle = title,
                            sourceNotes = sourceNotes,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    NotebookDetailTab.SOURCES -> {
                        NotebookSourcesTab(
                            notebookId = notebookId,
                            sourceNotes = sourceNotes,
                            onOpenNote = onOpenNote,
                            onOpenReader = { readerNote = it },
                            onReload = { reloadCount++ },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        readerNote?.let { note ->
            SourceReaderOverlay(
                note = note,
                onDismiss = { readerNote = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun NotebookDetailHeader(
    selectedTab: NotebookDetailTab,
    sourceCount: Int,
    onSelectTab: (NotebookDetailTab) -> Unit,
    onBack: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val dark = isDarkGlass()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = onGradient
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 40.dp)
                .frostedGlass(cornerRadius = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isChat = selectedTab == NotebookDetailTab.CHAT
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isChat) (if (dark) Color(0xFF0284C7).copy(alpha = 0.35f) else Color(0xFF0284C7).copy(alpha = 0.20f)) else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelectTab(NotebookDetailTab.CHAT) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "对话",
                            color = if (isChat) (if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7)) else onGradientMuted,
                            fontSize = LucentTitles.SectionSize,
                            fontWeight = if (isChat) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.padding(horizontal = 2.dp))

                val isSources = selectedTab == NotebookDetailTab.SOURCES
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSources) (if (dark) Color(0xFF0284C7).copy(alpha = 0.35f) else Color(0xFF0284C7).copy(alpha = 0.20f)) else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelectTab(NotebookDetailTab.SOURCES) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "来源 ($sourceCount)",
                            color = if (isSources) (if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7)) else onGradientMuted,
                            fontSize = LucentTitles.SectionSize,
                            fontWeight = if (isSources) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
