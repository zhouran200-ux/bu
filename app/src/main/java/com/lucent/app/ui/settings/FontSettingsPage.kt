package com.lucent.app.ui.settings

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import com.lucent.app.data.FontLibrary
import com.lucent.app.data.FontStore
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentFontResolver
import com.lucent.app.ui.rememberLucentFont
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.SYSTEM_FONT_KEY
import com.lucent.app.ui.StepperSlider
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.glassPanelColor
import com.lucent.app.ui.glassPopupPanel
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontSettingsPage(
    repo: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val fontKey by repo.font.collectAsState(initial = SettingsCache.font)
    val savedFontScale by repo.fontScale.collectAsState(initial = SettingsCache.fontScale)
    val savedLineSpacing by repo.lineSpacing.collectAsState(initial = SettingsCache.lineSpacing)
    val savedLetterSpacing by repo.letterSpacing.collectAsState(initial = SettingsCache.letterSpacing)

    var activeFontScale by remember(savedFontScale) { mutableFloatStateOf(savedFontScale) }
    var activeLineSpacing by remember(savedLineSpacing) { mutableFloatStateOf(savedLineSpacing) }
    var activeLetterSpacing by remember(savedLetterSpacing) { mutableFloatStateOf(savedLetterSpacing) }

    var fontRefreshIndex by remember { mutableStateOf(0) }
    var importedFonts by remember { mutableStateOf<List<FontStore.FontSlot>>(emptyList()) }
    LaunchedEffect(fontRefreshIndex) {
        importedFonts = FontLibrary.slots(context)
    }
    val canImportMore = importedFonts.size < FontStore.MAX_FONTS

    var fontImporting by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var importFontNameDraft by remember { mutableStateOf("") }
    var fontPendingDelete by remember { mutableStateOf<FontStore.FontSlot?>(null) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && !fontImporting) {
            fontError = ""
            fontImporting = true
            scope.launch {
                try {
                    val displayName = withContext(Dispatchers.IO) {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }
                    importFontNameDraft = displayName.orEmpty().substringBeforeLast('.').take(60)
                    pendingImportUri = uri
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    importFontNameDraft = ""
                    pendingImportUri = uri
                } finally {
                    fontImporting = false
                }
            }
        }
    }

    fun startImportFont(uri: Uri, name: String) {
        if (fontImporting) return
        fontImporting = true
        fontError = ""
        scope.launch {
            try {
                val slot = FontLibrary.import(context, uri, name)
                fontRefreshIndex++
                repo.setFont(slot.id)
                Toast.makeText(context, S.fontImportedToast, Toast.LENGTH_SHORT).show()
            } catch (e: FontStore.NotFontException) {
                fontError = S.fontImportFailedNotFont
            } catch (e: FontStore.TooManyFontsException) {
                fontError = S.fontImportFailedTooMany(FontStore.MAX_FONTS)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                fontError = S.fontImportFailedGeneric(t.localizedMessage ?: "Error")
            } finally {
                fontImporting = false
                pendingImportUri = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = S.typographyPreviewTitle,
                    color = onGradientMuted,
                    fontSize = LucentTitles.SectionSize,
                    fontWeight = LucentTitles.SectionWeight
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = onGradient.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, onGradient.copy(alpha = 0.16f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        scope.launch {
                            repo.resetTypographyDefaults()
                            activeFontScale = 1.0f
                            activeLineSpacing = 1.2f
                            activeLetterSpacing = 0.0f
                            Toast.makeText(context, S.resetTypographyDefaults, Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = onGradient,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = S.resetTypographyDefaults,
                        color = onGradient,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val previewFamily = rememberLucentFont(fontKey)
        val previewFontSize = (14.sp.value * activeFontScale).sp
        val previewLineHeight = (previewFontSize.value * 1.35f * activeLineSpacing).sp
        val previewLetterSpacing = activeLetterSpacing.sp

        Text(
            text = S.typographyPreviewSample,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = previewFamily,
                fontSize = previewFontSize,
                lineHeight = previewLineHeight,
                letterSpacing = previewLetterSpacing
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = onGradient
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        StepperSlider(
            compact = true,
            title = S.fontSizeScaleTitle,
            valueLabel = if (kotlin.math.abs(activeFontScale - 1f) < 0.005f) "默认" else "${(activeFontScale * 100).roundToInt()}%",
            value = activeFontScale,
            valueRange = 0.85f..1.30f,
            step = 0.05f,
            onValueChange = { activeFontScale = it },
            onValueChangeFinished = { scope.launch { repo.setFontScale(activeFontScale) } }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = onGradient.copy(alpha = 0.08f)
        )

        StepperSlider(
            compact = true,
            title = S.lineSpacingTitle,
            valueLabel = if (kotlin.math.abs(activeLineSpacing - 1.2f) < 0.005f) "默认" else String.format("%.2fx", activeLineSpacing),
            value = activeLineSpacing,
            valueRange = 1.0f..1.8f,
            step = 0.05f,
            onValueChange = { activeLineSpacing = it },
            onValueChangeFinished = { scope.launch { repo.setLineSpacing(activeLineSpacing) } }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = onGradient.copy(alpha = 0.08f)
        )

        StepperSlider(
            compact = true,
            title = S.letterSpacingTitle,
            valueLabel = if (kotlin.math.abs(activeLetterSpacing) < 0.005f) "默认" else String.format("%+.1fsp", activeLetterSpacing),
            value = activeLetterSpacing,
            valueRange = -0.5f..2.0f,
            step = 0.1f,
            onValueChange = { activeLetterSpacing = it },
            onValueChangeFinished = { scope.launch { repo.setLetterSpacing(activeLetterSpacing) } }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = S.settingsFontTitle,
                color = onGradient,
                fontSize = LucentTitles.SectionSize,
                fontWeight = LucentTitles.SectionWeight
            )
            Text(
                text = S.settingsFontSub,
                color = onGradientMuted,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (fontError.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = fontError,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        val isSystemSelected = fontKey.isBlank() || fontKey == SYSTEM_FONT_KEY
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSystemSelected) onGradient.copy(alpha = 0.08f) else Color.Transparent
                )
                .border(
                    1.dp,
                    if (isSystemSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else onGradient.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp)
                )
                .clickable { scope.launch { repo.setFont(SYSTEM_FONT_KEY) } }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSystemSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else onGradient.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aa",
                    color = if (isSystemSelected) MaterialTheme.colorScheme.primary else onGradient,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = S.fontSystemLabel,
                color = onGradient,
                fontSize = LucentTitles.ItemSize,
                fontWeight = if (isSystemSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            if (isSystemSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        importedFonts.forEach { slot ->
            val isSelected = fontKey == slot.id
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) onGradient.copy(alpha = 0.08f) else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else onGradient.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { scope.launch { repo.setFont(slot.id) } }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fontSlotFamily = rememberLucentFont(slot.id)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else onGradient.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aa",
                        color = if (isSelected) MaterialTheme.colorScheme.primary else onGradient,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontSlotFamily,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = slot.name,
                    color = onGradient,
                    fontSize = LucentTitles.ItemSize,
                    fontFamily = fontSlotFamily,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                IconButton(
                    onClick = { fontPendingDelete = slot },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = S.fontDeleteA11y,
                        tint = onGradientMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = onGradient.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, onGradient.copy(alpha = 0.16f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = canImportMore && !fontImporting) {
                    documentPickerLauncher.launch(
                        arrayOf(
                            "font/*",
                            "application/x-font-ttf",
                            "application/x-font-opentype",
                            "application/vnd.ms-opentype",
                            "application/octet-stream"
                        )
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (fontImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = onGradient,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S.fontImporting, color = onGradient, fontSize = 13.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = onGradient,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (canImportMore) S.fontImportButton else S.fontSlotsFullHint(FontStore.MAX_FONTS),
                        color = onGradient,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    pendingImportUri?.let { uri ->
        val keepEditing = com.lucent.app.ui.rememberImeVisibleOnOpen()
        val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        Dialog(onDismissRequest = { pendingImportUri = null }) {
            com.lucent.app.ui.PreserveEditingKeyboardOnOpen(keepEditing, inputFocus)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .glassPopupPanel(20.dp)
                    .padding(20.dp)
            ) {
                Text(S.fontNameTitle, fontSize = LucentTitles.ItemSize, fontWeight = LucentTitles.ItemWeight, color = onGradient)
                Spacer(modifier = Modifier.height(10.dp))
                Text(S.fontNameBody, style = MaterialTheme.typography.bodySmall, color = onGradientMuted)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = importFontNameDraft,
                    onValueChange = { importFontNameDraft = it },
                    label = { Text(S.fontNameField) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        startImportFont(uri, importFontNameDraft.ifBlank { "Custom Font" })
                    }),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(inputFocus)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { pendingImportUri = null }) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        startImportFont(uri, importFontNameDraft.ifBlank { "Custom Font" })
                    }) { Text("导入") }
                }
            }
        }
    }

    fontPendingDelete?.let { slot ->
        AlertDialog(
            containerColor = glassPanelColor(),
            tonalElevation = 0.dp,
            onDismissRequest = { fontPendingDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = { com.lucent.app.ui.KeepKeyboardForNonEditingDialog(); Text(S.fontDeleteTitle, fontSize = LucentTitles.ItemSize, fontWeight = LucentTitles.ItemWeight) },
            text = { Text(S.fontDeleteBody(slot.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idToDelete = slot.id
                        fontPendingDelete = null
                        scope.launch {
                            try {
                                FontLibrary.delete(context, idToDelete)
                                LucentFontResolver.evict(idToDelete)
                                fontRefreshIndex++
                                if (fontKey == idToDelete) repo.setFont(SYSTEM_FONT_KEY)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                fontError = S.fontImportFailedGeneric(failure.localizedMessage ?: "Error")
                            }
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fontPendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}
