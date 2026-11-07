package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.IndexedSource
import com.lucent.app.i18n.S

@Composable
internal fun SourceIndexPreview(
    source: IndexedSource,
    rows: List<SourcePreviewRow>,
    listState: LazyListState,
    active: Boolean
) {
    val ink = LocalOnGradient.current
    val muted = LocalOnGradientMuted.current
    val density = LocalDensity.current
    val reading = LocalReadingTypography.current
    val rowKeys = remember(rows) { rows.map { it.key } }
    val chapterIndices = remember(rows) { rows.indices.filter { rows[it].isChapter } }
    val sectionTitles = remember(rows, source.title) { sourcePreviewSectionTitles(rows, source.title) }
    val currentSectionTitle by remember(sectionTitles, listState, source.title) {
        derivedStateOf { sectionTitles.getOrElse(listState.firstVisibleItemIndex) { source.title } }
    }
    val meta = S.sourceIndexPreviewMeta(
        source.tagged.chapters.size,
        source.tagged.slices.size,
        source.tagged.text.length
    )
    val sourceLine = listOf(meta, source.title.trim()).filter { it.isNotBlank() }.joinToString(" · ")
    var dockHeight by remember(density) { mutableStateOf(56.dp) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(lucentPanelBackground()).testTag("source_model_page")
    ) {
        val titleMaxWidth = (maxWidth - 24.dp).coerceIn(0.dp, 280.dp)
        val dockWidth = (maxWidth - 24.dp).coerceIn(0.dp, 320.dp)
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(S.sourceIndexPreviewEmpty, color = muted, fontSize = LucentTitles.ItemSize)
            }
        } else {
            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("source_model_list")
                        .sourceReadingPosition(listState, rowKeys),
                    // Measure the dock so enlarged fonts cannot hide the final text behind it.
                    contentPadding = PaddingValues(start = 18.dp, top = 60.dp, end = 18.dp, bottom = dockHeight + 28.dp + bottomInset),
                    verticalArrangement = Arrangement.spacedBy(20.dp * reading.paragraphSpacing)
                ) {
                    items(rows, key = { it.key }) { row ->
                        ScopedReadingTypography {
                            Text(
                                text = row.text,
                                color = ink,
                                fontSize = 13.sp,
                                lineHeight = (20f * reading.lineFactor).sp,
                                letterSpacing = reading.letterSpacing.sp,
                                fontFamily = reading.fontFamily
                            )
                        }
                    }
                }
            }
            DocumentScrollCapsule(
                listState = listState,
                itemKeys = rowKeys,
                chapterIndices = chapterIndices,
                enabled = active,
                label = S.sourceIndexPreviewFastScroll,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        SourceChapterBadge(
            title = currentSectionTitle,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                .testTag("source_model_chapter").widthIn(max = titleMaxWidth)
        )
        SourcePreviewInfoDock(
            sourceLine = sourceLine,
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(12.dp)
                .width(dockWidth).testTag("source_model_info")
                .onSizeChanged { dockHeight = with(density) { it.height.toDp() } }
        )
    }
}
