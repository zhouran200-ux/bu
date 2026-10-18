package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lucent.app.collapseExcessBlankLines
import com.lucent.app.i18n.S
import com.lucent.app.ui.LocalOnGradientMuted

@Composable
internal fun ApiProfileFieldsSection(
    editingProfileName: String,
    onEditingProfileNameChange: (String) -> Unit,
    key: String,
    onKeyChange: (String) -> Unit,
    keyVisible: Boolean,
    onRevealKey: () -> Unit,
    spec: String,
    onSpecChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit
) {
    val onGradientMuted = LocalOnGradientMuted.current
    val focusManager = LocalFocusManager.current
    CompactField(
        value = editingProfileName,
        onValueChange = { onEditingProfileNameChange(collapseExcessBlankLines(it)) },
        label = S.fieldName,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
    )

    Spacer(modifier = Modifier.height(8.dp))
    CompactField(
        value = key,
        onValueChange = { onKeyChange(collapseExcessBlankLines(it)) },
        label = S.fieldApiKey,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            IconButton(onClick = onRevealKey, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = S.a11yToggleKeyVisibility,
                    tint = onGradientMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    )

    Spacer(modifier = Modifier.height(8.dp))
    ChipRow(
        options = listOf(
            "openai" to "OpenAI",
            "anthropic" to "Anthropic",
            "google" to "Google",
            "custom" to S.apiSpecCustom
        ),
        selectedKey = spec,
        onSelect = { onSpecChange(it) }
    )

    Spacer(modifier = Modifier.height(8.dp))
    CompactField(
        value = url,
        onValueChange = { onUrlChange(collapseExcessBlankLines(it)) },
        label = S.fieldBaseUrl,
        placeholder = if (spec == "custom") S.apiCustomPlaceholder else "",
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
    )
}
