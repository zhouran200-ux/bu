package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S
import kotlin.math.abs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag

@Composable
internal fun SourceReaderDocument(
    notebookId: Long,
    content: SourceReaderContent,
    focus: SourceFocus?,
    active: Boolean,
    emphasizedFocus: SourceFocus?,
    onEmphasize: (SourceFocus?) -> Unit,
    modifier: Modifier
) {
    val source = content.source
    val items = content.items
    var showIndexPreview by remember(source.noteId, source.revision) { mutableStateOf(false) }
    val previewKeys = remember(content.previewRows) { content.previewRows.map { it.key } }
    val previewListState = rememberLazyListState()
    val motionEnabled = LocalBackgroundEnvironment.current.motionEnabled
    val readerKeys = remember(items) { items.map { it.key } }
    val readingKeys = remember(readerKeys) { readerKeys + "footer" }
    val chapterIndices = remember(items) { items.indices.filter { items[it].sliceId == null } }
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
        val leavingPreview = showIndexPreview
        showIndexPreview = false
        onEmphasize(null)
        when (val location = sourceFocusLocation(target, source.noteId, source.revision, items)) {
            is SourceFocusLocation.Unavailable -> Unit
            is SourceFocusLocation.Found -> {
                onEmphasize(target)
                if (leavingPreview) {
                    listState.requestScrollToItem(location.index)
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
    val returnToDocument: () -> Unit = {
        previewListState.readingPosition()?.resolve(readerKeys)?.let { position ->
            listState.requestScrollToItem(position.index, position.offset)
        }
        showIndexPreview = false
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
                active = active && showIndexPreview,
                onBack = returnToDocument
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().background(lucentPanelBackground()).testTag("source_reader_page")) {
                val titleMaxWidth = (maxWidth - 76.dp).coerceIn(120.dp, 280.dp)
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("source_reader_list"),
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
                }
                DocumentScrollCapsule(
                    listState = listState,
                    itemKeys = readingKeys,
                    chapterIndices = chapterIndices,
                    enabled = active && !showIndexPreview,
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
                        .widthIn(max = titleMaxWidth)
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

                SourceIndexPreviewEntry(
                    enabled = active && !showIndexPreview && source.tagged.text.isNotBlank(),
                    onClick = {
                        listState.readingPosition()?.resolve(previewKeys)?.let { position ->
                            previewListState.requestScrollToItem(position.index, position.offset)
                        }
                        showIndexPreview = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )

            }
        }
    }
}
