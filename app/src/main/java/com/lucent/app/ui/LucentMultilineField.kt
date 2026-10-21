package com.lucent.app.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction

@Composable
internal fun LucentMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    placeholderColor: Color,
    minLines: Int,
    maxLines: Int,
    modifier: Modifier = Modifier,
    cursorBrush: Brush = SolidColor(Color.Black),
    contentDescription: String? = null
) {
    val label = contentDescription
    val fieldModifier = if (label == null) modifier else modifier.semantics { this.contentDescription = label }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        minLines = minLines,
        maxLines = maxLines,
        modifier = fieldModifier,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(text = placeholder, style = textStyle.copy(color = placeholderColor))
            }
            innerTextField()
        }
    )
}
