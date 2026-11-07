package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.i18n.S
import kotlin.math.abs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

internal val SourceReadingPositionKey = SemanticsPropertyKey<String>("SourceReadingPosition")

internal fun Modifier.sourceReadingPosition(state: LazyListState, keys: List<String>): Modifier =
    semantics { this[SourceReadingPositionKey] = state.sourcePosition(keys).encode() }

private fun LazyListState.sourcePosition(keys: List<String>): ReadingPosition =
    ReadingPosition(
        keys.getOrElse(firstVisibleItemIndex) { "" },
        firstVisibleItemIndex,
        firstVisibleItemScrollOffset
    )

@Composable
internal fun SourceReaderDocument(
    notebookId: Long,
    content: SourceReaderContent,
    focus: SourceFocus?,
    active: Boolean,
    emphasizedFocus: SourceFocus?,
    onEmphasize: (SourceFocus?) -> Unit,
    modifier: Modifier,
    onReplaceDocument: (() -> Unit)? = null,
    onRemoveDocument: (() -> Unit)? = null
) {
    val source = content.source
    val items = content.items
    val context = LocalContext.current.applicationContext
    val memoryKeys = SourceReadingMemoryKeys(notebookId, source.noteId, source.revision)
    val pageState = remember(memoryKeys) { mutableStateOf(ReadingPositionMemory.readSourcePage(context, memoryKeys)) }
    var showIndexPreview by pageState
    LaunchedEffect(memoryKeys, showIndexPreview) {
        ReadingPositionMemory.writeSourcePage(context, memoryKeys, showIndexPreview)
    }
    DisposableEffect(memoryKeys) {
        onDispose { ReadingPositionMemory.writeSourcePage(context, memoryKeys, pageState.value) }
    }
    val previewKeys = remember(content.previewRows) { content.previewRows.map { it.key } }
    val motionEnabled = LocalBackgroundEnvironment.current.motionEnabled
    val reading = LocalReadingTypography.current
    val readerKeys = remember(items) { items.map { it.key } }
    val readingKeys = remember(readerKeys) { readerKeys + "footer" }
    val chapterIndices = remember(items) { items.indices.filter { items[it].sliceId == null } }
    val savedDocument = remember(memoryKeys) { ReadingPositionMemory.readSourceDocument(context, memoryKeys) }
    val savedModel = remember(memoryKeys) { ReadingPositionMemory.read(context, memoryKeys.model) }
    var documentVisited by remember(memoryKeys) { mutableStateOf(savedDocument != null || !showIndexPreview) }
    var modelVisited by remember(memoryKeys) { mutableStateOf(savedModel != null || showIndexPreview) }
    var listState by remember(memoryKeys) {
        val position = savedDocument?.resolve(readingKeys)
            ?: savedModel?.forSourceLayout(readingKeys)
        mutableStateOf(LazyListState(position?.index ?: 0, position?.offset ?: 0))
    }
    var previewListState by remember(memoryKeys) {
        val position = savedModel?.resolve(previewKeys) ?: savedDocument?.forSourceLayout(previewKeys)
        mutableStateOf(LazyListState(position?.index ?: 0, position?.offset ?: 0))
    }
    RememberReadingPosition(memoryKeys.document) {
        if (documentVisited) listState.sourcePosition(readingKeys) else null
    }
    RememberReadingPosition(memoryKeys.model) {
        if (modelVisited) previewListState.sourcePosition(previewKeys) else null
    }
    val returnToDocument: () -> Unit = {
        if (showIndexPreview) {
            val position = previewListState.sourcePosition(previewKeys).forSourceLayout(readingKeys)
            listState = LazyListState(position.index, position.offset)
            documentVisited = true
        }
        showIndexPreview = false
    }
    val currentSectionTitle by remember(items, source.title, listState) {
        derivedStateOf { sourceSectionTitle(items, listState.firstVisibleItemIndex, source.title) }
    }
    var consumedRequest by remember(memoryKeys) { mutableStateOf<Long?>(null) }
    LaunchedEffect(active, focus, memoryKeys) {
        if (!active) return@LaunchedEffect
        val target = focus ?: return@LaunchedEffect
        if (consumedRequest == target.requestId) return@LaunchedEffect
        consumedRequest = target.requestId
        val leavingPreview = showIndexPreview
        returnToDocument()
        onEmphasize(null)
        when (val location = sourceFocusLocation(target, source.noteId, source.revision, items)) {
            is SourceFocusLocation.Unavailable -> Unit
            is SourceFocusLocation.Found -> {
                onEmphasize(target)
                if (leavingPreview) {
                    listState = LazyListState(location.index)
                    return@LaunchedEffect
                }
                withFrameNanos { }
                if (motionEnabled && listState.layoutInfo.visibleItemsInfo.isNotEmpty() && abs(listState.firstVisibleItemIndex - location.index) <= 3) {
                    listState.animateScrollToItem(location.index)
                } else {
                    listState.scrollToItem(location.index)
                }
            }
        }
    }
    BackHandler(enabled = active && showIndexPreview, onBack = returnToDocument)
    AnimatedContent(
        targetState = showIndexPreview,
        modifier = modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(tween(320)) { it * direction } togetherWith
                slideOutHorizontally(tween(320)) { -it * direction }).using(SizeTransform(clip = false))
        },
        label = "sourceReaderPage"
    ) { preview ->
        if (preview) {
            SourceIndexPreview(
                source = source,
                rows = content.previewRows,
                listState = previewListState,
                active = active && showIndexPreview
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().background(lucentPanelBackground()).testTag("source_reader_page")) {
                val titleMaxWidth = (maxWidth - 24.dp).coerceIn(0.dp, 280.dp)
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("source_reader_list")
                            .sourceReadingPosition(listState, readingKeys),
                        contentPadding = PaddingValues(start = 18.dp, top = 60.dp, end = 18.dp, bottom = 88.dp + bottomInset),
                        verticalArrangement = Arrangement.spacedBy(4.dp * reading.paragraphSpacing)
                    ) {
                        itemsIndexed(items, key = { _, item -> item.key }) { _, item ->
                            ScopedReadingTypography {
                                MarkdownText(
                                    text = item.text,
                                    initialSnapshot = content.markdown.getValue(item.key),
                                    emphasized = item.sliceId != null && emphasizedFocus?.noteId == source.noteId &&
                                        emphasizedFocus.revision == source.revision && emphasizedFocus.sliceId == item.sliceId,
                                    citationsEnabled = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        item(key = "footer") { Spacer(modifier = Modifier.height(48.dp)) }
                    }
                }
                DocumentScrollCapsule(
                    listState = listState,
                    itemKeys = readingKeys,
                    chapterIndices = chapterIndices,
                    enabled = active && !showIndexPreview,
                    label = S.sourcesFastScroll,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )

                SourceChapterBadge(
                    title = currentSectionTitle,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                        .testTag("source_reader_chapter").widthIn(max = titleMaxWidth)
                )

                // Keep both controls above system navigation; list padding protects the final paragraph.
                SourceReaderActions(
                    enabled = active && !showIndexPreview,
                    previewEnabled = source.tagged.text.isNotBlank(),
                    onReplace = onReplaceDocument,
                    onRemove = onRemoveDocument,
                    onOpenPreview = {
                        val position = listState.sourcePosition(readingKeys).forSourceLayout(previewKeys)
                        previewListState = LazyListState(position.index, position.offset)
                        modelVisited = true
                        showIndexPreview = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

            }
        }
    }
}
