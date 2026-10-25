package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.CitationRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

internal data class MarkdownParseSnapshot(
    val blocks: List<MdBlock>,
    val refs: List<CitationRef>,
    val annotated: List<AnnotatedString> = emptyList()
)

private data class RevealedMarkdownInput(val text: String, val complete: Boolean)

@Composable
internal fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    onCitationClick: ((CitationRef) -> Unit)? = null,
    sourceNoteId: Long? = null,
    sourceRevision: String? = null,
    emphasized: Boolean = false,
    citationsEnabled: Boolean = true,
    streaming: Boolean = false,
    initialSnapshot: MarkdownParseSnapshot? = null
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val reading = LocalReadingTypography.current
    val lineFactor = reading.lineFactor
    val extraLetterSpacing = reading.letterSpacing.sp
    val paragraphGap = 6.dp * reading.paragraphSpacing
    val blankGap = 12.dp * reading.paragraphSpacing
    val clickable = onCitationClick != null
    val style = remember(
        onGradient, onGradientMuted, accent, citationsEnabled,
        emphasized, sourceNoteId, sourceRevision, clickable
    ) {
        MarkdownStyle(
            textColor = onGradient,
            mutedColor = onGradientMuted,
            accent = accent,
            citationsEnabled = citationsEnabled,
            emphasized = emphasized,
            sourceNoteId = sourceNoteId,
            sourceRevision = sourceRevision,
            clickable = clickable
        )
    }
    val renderer = remember { MarkdownRenderer() }
    var parsed by remember(initialSnapshot) { mutableStateOf(initialSnapshot ?: MarkdownParseSnapshot(emptyList(), emptyList())) }
    val currentClick = rememberUpdatedState(onCitationClick)
    val click: (CitationRef) -> Unit = remember(clickable) { { ref -> currentClick.value?.invoke(ref) } }
    val revealed = rememberRevealedText(text, streaming)
    val latestReveal = rememberUpdatedState(
        RevealedMarkdownInput(revealed, complete = revealed.length >= text.length)
    )
    LaunchedEffect(renderer, style, streaming) {
        snapshotFlow { latestReveal.value }
            .conflate()
            .collect { snapshot ->
                val display = if ((streaming || !snapshot.complete) && style.citationsEnabled) {
                    streamingMarkdownTail(snapshot.text)
                } else {
                    snapshot.text
                }
                parsed = withContext(Dispatchers.Default) { renderer.render(display, style, click) }
            }
    }
    val blocks = parsed.blocks
    val annotated = parsed.annotated
    val fontWeight = if (emphasized) FontWeight.Bold else null
    val badgeContents = remember(parsed.refs, accent, clickable) {
        citationBadgeContents(
            refs = parsed.refs,
            accent = accent,
            onClick = if (clickable) click else null
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> {
                    if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                    val headingSize = when (block.level) {
                        1 -> 22.sp
                        2 -> 19.sp
                        3 -> 17.sp
                        else -> 15.sp
                    }
                    Text(
                        annotated[index],
                        color = onGradient,
                        inlineContent = badgeContents,
                        fontSize = headingSize,
                        lineHeight = (headingSize.value * 1.2f * lineFactor).sp,
                        letterSpacing = extraLetterSpacing,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                is MdBlock.Paragraph -> {
                    if (index > 0) Spacer(modifier = Modifier.height(paragraphGap))
                    Text(
                        annotated[index],
                        color = onGradient,
                        fontWeight = fontWeight,
                        inlineContent = badgeContents,
                        fontSize = 14.sp,
                        lineHeight = (14f * 1.2f * lineFactor).sp,
                        letterSpacing = extraLetterSpacing
                    )
                }

                is MdBlock.Bullet -> {
                    if (index > 0) Spacer(modifier = Modifier.height(paragraphGap))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "•",
                            color = onGradientMuted,
                            fontWeight = fontWeight,
                            fontSize = 14.sp,
                            lineHeight = (14f * 1.2f * lineFactor).sp,
                            letterSpacing = extraLetterSpacing,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            annotated[index],
                            color = onGradient,
                            fontWeight = fontWeight,
                            inlineContent = badgeContents,
                            fontSize = 14.sp,
                            lineHeight = (14f * 1.2f * lineFactor).sp,
                            letterSpacing = extraLetterSpacing,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Numbered -> {
                    if (index > 0) Spacer(modifier = Modifier.height(paragraphGap))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${block.marker}.",
                            color = onGradientMuted,
                            fontWeight = fontWeight,
                            fontSize = 14.sp,
                            lineHeight = (14f * 1.2f * lineFactor).sp,
                            letterSpacing = extraLetterSpacing,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            annotated[index],
                            color = onGradient,
                            fontWeight = fontWeight,
                            fontSize = 14.sp,
                            lineHeight = (14f * 1.2f * lineFactor).sp,
                            letterSpacing = extraLetterSpacing,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Quote -> {
                    if (index > 0) Spacer(modifier = Modifier.height(paragraphGap))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(onGradientMuted)
                        )
                        Text(
                            annotated[index],
                            color = onGradientMuted,
                            fontWeight = fontWeight,
                            fontStyle = FontStyle.Italic,
                            inlineContent = badgeContents,
                            fontSize = 14.sp,
                            lineHeight = (14f * 1.2f * lineFactor).sp,
                            letterSpacing = extraLetterSpacing,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                is MdBlock.Code -> {
                    if (index > 0) Spacer(modifier = Modifier.height(paragraphGap))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(onGradient.copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        Text(
                            annotated[index],
                            color = onGradient,
                            fontWeight = fontWeight,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = (13f * 1.2f * lineFactor).sp,
                            letterSpacing = extraLetterSpacing
                        )
                    }
                }

                MdBlock.Blank -> Spacer(modifier = Modifier.height(blankGap))

                MdBlock.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(onGradientMuted.copy(alpha = 0.4f))
                )
            }
        }
    }
}
