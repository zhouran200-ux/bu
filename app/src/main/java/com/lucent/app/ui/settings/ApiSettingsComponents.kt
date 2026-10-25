package com.lucent.app.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S
import com.lucent.app.ui.Haptics
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.frostedGlass

private val CompactFieldHeight = 42.dp
private val CompactFieldLabelWidth = 56.dp

@Composable
internal fun DeleteWithConfirmButton(
    onDelete: () -> Unit,
    tint: Color,
    buttonSize: Dp = 26.dp,
    iconSize: Dp = 16.dp,
    confirmTextPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    confirmTextSize: TextUnit = 11.sp,
    modifier: Modifier = Modifier,
    contentDescription: String = S.actionDelete
) {
    val context = LocalContext.current
    var confirmOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (confirmOpen) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .clickable {
                        confirmOpen = false
                        Haptics.tick(context)
                        onDelete()
                    }
                    .padding(confirmTextPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = S.apiConfirmDelete,
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = confirmTextSize,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    confirmOpen = false
                    Haptics.tick(context)
                },
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = S.actionCancel,
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        } else {
            IconButton(
                onClick = {
                    confirmOpen = true
                    Haptics.tick(context)
                },
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
internal fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    placeholder: String = "",
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CompactFieldHeight)
            .clip(shape)
            .background(onGradient.copy(alpha = if (focused) 0.07f else 0.04f))
            .border(1.dp, if (focused) accent else onGradientMuted.copy(alpha = 0.3f), shape)
            .padding(horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    color = onGradientMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.width(CompactFieldLabelWidth)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                readOnly = readOnly,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = if (readOnly) onGradientMuted else onGradient
                ),
                cursorBrush = SolidColor(accent),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interaction,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (label.isNotBlank()) 6.dp else 0.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotBlank()) {
                            Text(
                                text = placeholder,
                                color = onGradientMuted.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        inner()
                    }
                }
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(2.dp))
                trailing()
            }
        }
    }
}

@Composable
internal fun SectionRule() {
    val line = LocalOnGradient.current.copy(alpha = 0.18f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, line, line, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(LocalOnGradient.current.copy(alpha = 0.055f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
internal fun ChipRow(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (key, label) ->
            val selected = key == selectedKey
            val shape = RoundedCornerShape(percent = 50)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) accent else onGradientMuted.copy(alpha = 0.3f),
                        shape
                    )
                    .clickable {
                        Haptics.tick(context)
                        onSelect(key)
                    }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) accent else onGradientMuted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun FloatingCopyButton(
    copied: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = if (copied) 0.22f else 0.12f))
            .border(1.dp, accent.copy(alpha = if (copied) 0.7f else 0.42f), shape)
            .clickable {
                Haptics.tick(context)
                onClick()
            }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = S.apiCopyFullText,
            tint = accent,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (copied) S.apiCopied else S.apiCopyFullText,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResponseCard(
    body: String,
    charCount: Int,
    copied: Boolean,
    onCopy: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(cornerRadius = 12.dp)
            .combinedClickable(onClick = { }, onLongClick = onCopy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(onGradient.copy(alpha = 0.05f))
                    .padding(8.dp)
            ) {
                val scrollState = rememberScrollState()
                Text(
                    text = body.ifBlank { S.apiEmptyBody },
                    color = onGradient,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(scrollState)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = S.apiCopyHint,
                    color = onGradientMuted.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                Text(
                    text = S.apiCharCount(charCount),
                    color = onGradientMuted.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            FloatingCopyButton(copied = copied, onClick = onCopy)
        }
    }
}
