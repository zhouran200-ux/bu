package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Note
import kotlinx.coroutines.launch

enum class NotebookDetailTab {
    CHAT,
    SOURCES
}

internal fun notebookBackTarget(
    currentTab: NotebookDetailTab,
    settledTab: NotebookDetailTab = currentTab,
    targetTab: NotebookDetailTab = currentTab
): NotebookDetailTab? = if (
    currentTab != NotebookDetailTab.CHAT ||
    settledTab != NotebookDetailTab.CHAT ||
    targetTab != NotebookDetailTab.CHAT
) NotebookDetailTab.CHAT else null

@Composable
fun NotebookDetailScreen(
    notebookId: Long,
    onBack: () -> Unit,
    active: Boolean = true
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    val tabs = NotebookDetailTab.entries
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val selectedTab = tabs[pagerState.currentPage]
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var sourceNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var reloadCount by remember { mutableIntStateOf(0) }
    var sourcesLoaded by remember(notebookId) { mutableStateOf(false) }
    var sourceError by remember(notebookId) { mutableStateOf<String?>(null) }
    var sourceFocus by remember { mutableStateOf<SourceFocus?>(null) }
    var focusRequest by remember { mutableStateOf(0L) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != NotebookDetailTab.CHAT) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val navigateBack: () -> Unit = {
        val target = notebookBackTarget(
            tabs[pagerState.currentPage],
            tabs[pagerState.settledPage],
            tabs[pagerState.targetPage]
        )
        if (target == null) {
            onBack()
        } else {
            scope.launch { pagerState.scrollToPage(tabs.indexOf(target)) }
        }
    }
    BackHandler(enabled = active, onBack = navigateBack)

    LaunchedEffect(notebookId, reloadCount) {
        try {
            val items = db.notebookDao().getItemsOnce(notebookId)
            val itemIds = items.filter { it.itemKind == com.lucent.app.data.NotebookItem.KIND_NOTE }.map { it.itemId }
            sourceNotes = if (itemIds.isNotEmpty()) db.noteDao().getByIds(itemIds) else emptyList()
            sourcesLoaded = true
            sourceError = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            sourceError = "读取文档失败：${e.message}"
        }
    }

    if (!sourcesLoaded || sourceError != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.TextButton(onClick = { reloadCount++ }) {
                Text(sourceError?.plus("，点击重试") ?: "正在读取文档…")
            }
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            NotebookDetailHeader(
                selectedTab = selectedTab,
                sourceCount = sourceNotes.size,
                onSelectTab = { tab ->
                    scope.launch { pagerState.animateScrollToPage(tabs.indexOf(tab)) }
                }
            )

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = tabs.lastIndex,
                key = { tabs[it] },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (tabs[page]) {
                    NotebookDetailTab.CHAT -> {
                        NotebookChatTab(
                            notebookId = notebookId,
                            sourceNotes = sourceNotes,
                            modifier = Modifier.fillMaxSize(),
                            active = active && selectedTab == NotebookDetailTab.CHAT,
                            onOpenSource = { noteId, sliceId, revision ->
                                focusRequest += 1
                                sourceFocus = SourceFocus(noteId, sliceId, focusRequest, revision)
                                scope.launch {
                                    pagerState.animateScrollToPage(tabs.indexOf(NotebookDetailTab.SOURCES))
                                }
                            }
                        )
                    }
                    NotebookDetailTab.SOURCES -> {
                        NotebookSourcesTab(
                            notebookId = notebookId,
                            sourceNotes = sourceNotes,
                            onReload = { reloadCount++ },
                            modifier = Modifier.fillMaxSize(),
                            focus = sourceFocus,
                            active = active && selectedTab == NotebookDetailTab.SOURCES
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookDetailHeader(
    selectedTab: NotebookDetailTab,
    sourceCount: Int,
    onSelectTab: (NotebookDetailTab) -> Unit
) {
    val onGradientMuted = LocalOnGradientMuted.current
    val dark = isDarkGlass()
    val accent = MaterialTheme.colorScheme.primary
    val tabPillSelected = accent.copy(alpha = if (dark) 0.35f else 0.20f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .frostedGlass(cornerRadius = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabPill(
                    label = "对话",
                    selected = selectedTab == NotebookDetailTab.CHAT,
                    accent = accent,
                    selectedBg = tabPillSelected,
                    mutedColor = onGradientMuted,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTab(NotebookDetailTab.CHAT) }
                )

                Spacer(modifier = Modifier.padding(horizontal = 2.dp))

                TabPill(
                    label = "来源 ($sourceCount)",
                    selected = selectedTab == NotebookDetailTab.SOURCES,
                    accent = accent,
                    selectedBg = tabPillSelected,
                    mutedColor = onGradientMuted,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTab(NotebookDetailTab.SOURCES) }
                )
            }
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    accent: Color,
    selectedBg: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) selectedBg else Color.Transparent,
        animationSpec = LucentMotion.toColor(LucentMotion.Settle),
        label = "tabPillBg"
    )
    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) accent else mutedColor,
        animationSpec = LucentMotion.toColor(LucentMotion.Settle),
        label = "tabPillText"
    )
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = LucentTitles.SectionSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
