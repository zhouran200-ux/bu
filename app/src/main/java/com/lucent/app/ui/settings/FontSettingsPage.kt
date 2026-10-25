package com.lucent.app.ui.settings
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import com.lucent.app.ui.PreserveEditingKeyboardOnOpen
import com.lucent.app.ui.glassPopupPanel
import com.lucent.app.ui.rememberImeVisibleOnOpen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lucent.app.AppScope
import com.lucent.app.data.FontLibrary
import com.lucent.app.data.FontStore
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.Haptics
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentFontResolver
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.SYSTEM_FONT_KEY
import com.lucent.app.ui.foregroundRim
import com.lucent.app.ui.glassPanelColor
import kotlinx.coroutines.Dispatchers
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
    val savedParagraphSpacing by repo.paragraphSpacing.collectAsState(initial = SettingsCache.paragraphSpacing)
    val savedTypographyScope by repo.typographyScope.collectAsState(initial = SettingsCache.typographyScope)

    var activeFontScale by remember(savedFontScale) { mutableFloatStateOf(savedFontScale) }
    var activeLineSpacing by remember(savedLineSpacing) { mutableFloatStateOf(savedLineSpacing) }
    var activeLetterSpacing by remember(savedLetterSpacing) { mutableFloatStateOf(savedLetterSpacing) }
    var activeParagraphSpacing by remember(savedParagraphSpacing) { mutableFloatStateOf(savedParagraphSpacing) }
    var activeTypographyScope by remember(savedTypographyScope) { mutableStateOf(savedTypographyScope) }

    var fontRefreshIndex by remember { mutableIntStateOf(0) }
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
                AppScope.setFont(context, repo, slot.id)
                LucentToast.show(context, S.fontImportedToast)
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


    FontTypographySection(
        fontKey = fontKey,
        fontScale = activeFontScale,
        appliedFontScale = savedFontScale,
        lineSpacing = activeLineSpacing,
        letterSpacing = activeLetterSpacing,
        paragraphSpacing = activeParagraphSpacing,
        typographyScope = activeTypographyScope,
        onFontScaleChange = { activeFontScale = it },
        onFontScaleCommit = {
            AppScope.setFontScale(context, repo, activeFontScale)
        },
        onLineSpacingChange = { activeLineSpacing = it },
        onLineSpacingCommit = {
            AppScope.setLineSpacing(context, repo, activeLineSpacing)
        },
        onLetterSpacingChange = { activeLetterSpacing = it },
        onLetterSpacingCommit = {
            AppScope.setLetterSpacing(context, repo, activeLetterSpacing)
        },
        onParagraphSpacingChange = { activeParagraphSpacing = it },
        onParagraphSpacingCommit = {
            AppScope.setParagraphSpacing(context, repo, activeParagraphSpacing)
        },
        onTypographyScopeChange = { scope ->
            activeTypographyScope = scope
            AppScope.setTypographyScope(context, repo, scope)
        },
        onResetDefaults = {
            activeFontScale = 1.0f
            activeLineSpacing = 1.2f
            activeLetterSpacing = 0.0f
            activeParagraphSpacing = 1.0f
            AppScope.resetTypographyDefaults(context, repo)
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    FontLibrarySection(
        fontKey = fontKey,
        fonts = importedFonts,
        error = fontError,
        importing = fontImporting,
        canImportMore = canImportMore,
        onSelectSystem = { AppScope.setFont(context, repo, SYSTEM_FONT_KEY) },
        onSelectFont = { AppScope.setFont(context, repo, it) },
        onRequestDelete = { fontPendingDelete = it },
        onImportClick = {
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
    )

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
                    TextButton(onClick = {
                        Haptics.tick(context)
                        pendingImportUri = null
                    }) { Text(S.actionCancel) }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        Haptics.tick(context)
                        startImportFont(uri, importFontNameDraft.ifBlank { "Custom Font" })
                    }) { Text(S.fontImport) }
                }
            }
        }
    }

    fontPendingDelete?.let { slot ->
        AlertDialog(
            modifier = Modifier.border(0.75.dp, foregroundRim(), RoundedCornerShape(24.dp)),
            containerColor = glassPanelColor(),
            tonalElevation = 0.dp,
            onDismissRequest = { fontPendingDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = { com.lucent.app.ui.KeepKeyboardForNonEditingDialog(); Text(S.fontDeleteTitle, fontSize = LucentTitles.ItemSize, fontWeight = LucentTitles.ItemWeight) },
            text = { Text(S.fontDeleteBody(slot.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        Haptics.tick(context)
                        val idToDelete = slot.id
                        fontPendingDelete = null
                        scope.launch {
                            try {
                                FontLibrary.delete(context, idToDelete)
                                LucentFontResolver.evict(idToDelete)
                                fontRefreshIndex++
                                if (fontKey == idToDelete) AppScope.setFont(context, repo, SYSTEM_FONT_KEY)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                fontError = S.fontImportFailedGeneric(failure.localizedMessage ?: "Error")
                            }
                        }
                    }
                ) {
                    Text(S.actionDelete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    Haptics.tick(context)
                    fontPendingDelete = null
                }) {
                    Text(S.actionCancel)
                }
            }
        )
    }
}