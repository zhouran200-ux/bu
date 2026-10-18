package com.lucent.app.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.collapseExcessBlankLines
import com.lucent.app.i18n.S
import com.lucent.app.ui.ApiModelPickerDialog
import com.lucent.app.ui.LucentIcons
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.selectedCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ApiModelSelectionSection(
    models: List<String>,
    selectedModel: String,
    customModelInput: String,
    onModelsChange: (List<String>) -> Unit,
    onSelectedModelChange: (String) -> Unit,
    onCustomModelInputChange: (String) -> Unit,
    onCopyModel: (String) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = S.apiChatModel,
            color = onGradient,
            fontSize = LucentTitles.SectionSize,
            fontWeight = LucentTitles.SectionWeight
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${models.size}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 11.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    if (models.isNotEmpty()) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                models.forEach { m ->
                    val isSelected = m == selectedModel
                    val cardShape = RoundedCornerShape(10.dp)
                    val cardBg = onGradient.copy(alpha = 0.04f)
                    val cardBorder = onGradientMuted.copy(alpha = 0.2f)
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else onGradient

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(cardBg)
                            .border(1.dp, cardBorder, cardShape)
                            .selectedCard(isSelected, 10.dp)
                            .combinedClickable(
                                onClick = { onSelectedModelChange(if (isSelected) "" else m) },
                                onLongClick = {
                                    onCopyModel(m)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    else onGradient.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = LucentIcons.Model,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else onGradientMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = m,
                            color = contentColor,
                            fontSize = LucentTitles.ItemSize,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        DeleteWithConfirmButton(
                            onDelete = {
                                val newModels = models - m
                                onModelsChange(newModels)
                                if (selectedModel == m) {
                                    onSelectedModelChange(newModels.firstOrNull().orEmpty())
                                }
                            },
                            tint = onGradientMuted.copy(alpha = 0.7f),
                            buttonSize = 26.dp,
                            iconSize = 16.dp,
                            confirmTextPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
                            confirmTextSize = 11.sp,
                            contentDescription = S.apiRemoveModel
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }

    fun addCustomModel() {
        val trimmed = customModelInput.trim()
        if (trimmed.isNotBlank()) {
            if (trimmed !in models) {
                onModelsChange(models + trimmed)
            }
            onSelectedModelChange(trimmed)
            onCustomModelInputChange("")
        }
    }

    CompactField(
        value = customModelInput,
        onValueChange = { onCustomModelInputChange(collapseExcessBlankLines(it)) },
        placeholder = S.apiModelNamePlaceholder,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { addCustomModel() }),
        trailing = {
            if (customModelInput.isNotBlank()) {
                IconButton(onClick = { addCustomModel() }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = S.apiAddModel,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    )
}

@Composable
internal fun ApiModelSelectionDialog(
    pickerOpen: Boolean,
    choices: List<String>,
    models: List<String>,
    selectedModel: String,
    onModelsChange: (List<String>) -> Unit,
    onSelectedModelChange: (String) -> Unit,
    onApplied: () -> Unit,
    onDismiss: () -> Unit
) {
    if (pickerOpen) {
        ApiModelPickerDialog(
            names = choices,
            selected = models.toSet(),
            onDone = { picked ->
                val list = choices.filter { it in picked }
                onModelsChange(list)
                if (selectedModel !in list) {
                    onSelectedModelChange(list.firstOrNull().orEmpty())
                }
                onApplied()
            },
            onDismiss = onDismiss
        )
    }
}
