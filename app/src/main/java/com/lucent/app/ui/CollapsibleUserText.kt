package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S

private const val UserMessageCollapsedLines = 6

private class UserMessageTextLayout {
    var result: TextLayoutResult? = null
}

@Composable
internal fun CollapsibleUserText(
    text: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textLayout = remember(text) { UserMessageTextLayout() }
    val textColor = LocalOnGradient.current
    val reading = LocalReadingTypography.current

    SubcomposeLayout(modifier = modifier) { constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val body = subcompose("text") {
            Box {
                SelectionContainer {
                    ScopedReadingTypography {
                        Text(
                            text = text,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = (22f * reading.lineFactor).sp,
                            letterSpacing = reading.letterSpacing.sp,
                            fontFamily = reading.fontFamily,
                            maxLines = if (expanded) Int.MAX_VALUE else UserMessageCollapsedLines,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { textLayout.result = it }
                        )
                    }
                }
            }
        }.single().measure(childConstraints)
        val canCollapse = textLayout.result?.let {
            it.lineCount > UserMessageCollapsedLines || it.didOverflowHeight
        } == true
        val toggle = if (canCollapse) {
            subcompose("toggle") {
                TextButton(onClick = {
                    Haptics.tick(context)
                    onExpandedChange(!expanded)
                }) {
                    Text(if (expanded) S.userMessageCollapse else S.userMessageExpand)
                }
            }.single().measure(childConstraints.offset(vertical = -body.height))
        } else {
            null
        }
        val width = constraints.constrainWidth(maxOf(body.width, toggle?.width ?: 0))
        val height = constraints.constrainHeight(body.height + (toggle?.height ?: 0))
        layout(width, height) {
            body.placeRelative(0, 0)
            toggle?.placeRelative(width - toggle.width, body.height)
        }
    }
}
