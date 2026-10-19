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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class MarkdownParseSnapshot(
    val blocks: List<MdBlock>,
    val refs: List<CitationRef>,
    val annotated: List<AnnotatedString> = emptyList()
)

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    onCitationClick: ((CitationRef) -> Unit)? = null,
    sourceNoteId: Long? = null,
    sourceRevision: String? = null,
    emphasized: Boolean = false,
    citationsEnabled: Boolean = true,
    streaming: Boolean = false
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val incrementalParser = remember { IncrementalMarkdownParser() }
    val parserMutex = remember { Mutex() }
    var parsed by remember { mutableStateOf(MarkdownParseSnapshot(emptyList(), emptyList())) }
    val currentClick = rememberUpdatedState(onCitationClick)
    val click: (CitationRef) -> Unit = remember { { ref -> currentClick.value?.invoke(ref) } }
    LaunchedEffect(text, streaming, citationsEnabled, emphasized, onGradient, onGradientMuted, accent, sourceNoteId, sourceRevision, onCitationClick != null) {
        parsed = withContext(Dispatchers.Default) {
            parserMutex.withLock {
                val displayText = if (streaming && citationsEnabled) streamingMarkdownTail(text) else text
                val blocks = incrementalParser.parse(displayText)
                val cursor = CitationCursor()
                val decor = CitationDecor(if (onCitationClick != null) click else null, sourceNoteId, sourceRevision, citationsEnabled, emphasized)
                val annotated = blocks.map { block ->
                    val raw = when (block) {
                        is MdBlock.Heading -> block.text
                        is MdBlock.Paragraph -> block.text
                        is MdBlock.Bullet -> block.text
                        is MdBlock.Numbered -> block.text
                        is MdBlock.Quote -> block.text
                        is MdBlock.Code -> return@map AnnotatedString(block.lines.joinToString(10.toChar().toString()))
                        else -> return@map AnnotatedString("")
                    }
                    inlineAnnotated(raw, if (block is MdBlock.Quote) onGradientMuted else onGradient, accent, decor, cursor)
                }
                MarkdownParseSnapshot(blocks, cursor.refs.toList(), annotated)
            }
        }
    }
    val blocks = parsed.blocks
    val fontWeight = if (emphasized) FontWeight.Bold else null
    val badgeContents = remember(parsed.refs, accent, onCitationClick != null) {
        citationBadgeContents(
            refs = parsed.refs,
            accent = accent,
            onClick = if (onCitationClick != null) click else null
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> {
                    if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        parsed.annotated[index],
                        color = onGradient,
                        inlineContent = badgeContents,
                        fontSize = when (block.level) {
                            1 -> 22.sp
                            2 -> 19.sp
                            3 -> 17.sp
                            else -> 15.sp
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                is MdBlock.Paragraph -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        parsed.annotated[index],
                        color = onGradient,
                        fontWeight = fontWeight,
                        inlineContent = badgeContents
                    )
                }

                is MdBlock.Bullet -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("•", color = onGradientMuted, fontWeight = fontWeight, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            parsed.annotated[index],
                            color = onGradient,
                            fontWeight = fontWeight,
                            inlineContent = badgeContents,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Numbered -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("${block.marker}.", color = onGradientMuted, fontWeight = fontWeight, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            parsed.annotated[index],
                            color = onGradient,
                            fontWeight = fontWeight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Quote -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(onGradientMuted)
                        )
                        Text(
                            parsed.annotated[index],
                            color = onGradientMuted,
                            fontWeight = fontWeight,
                            fontStyle = FontStyle.Italic,
                            inlineContent = badgeContents,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                is MdBlock.Code -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(onGradient.copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        Text(
                            parsed.annotated[index],
                            color = onGradient,
                            fontWeight = fontWeight,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }

                MdBlock.Blank -> Spacer(modifier = Modifier.height(12.dp))

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
