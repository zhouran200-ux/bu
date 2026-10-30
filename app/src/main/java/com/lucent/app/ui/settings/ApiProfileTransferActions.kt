package com.lucent.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfileImportMode
import com.lucent.app.data.ApiProfileImportPreparation
import com.lucent.app.data.ApiProfileTxt
import com.lucent.app.data.ApiProfileTxtParse
import com.lucent.app.data.DocumentInputLimits
import com.lucent.app.data.prepareApiProfileImport
import com.lucent.app.i18n.S
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.KeepKeyboardForNonEditingDialog
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.foregroundRim
import com.lucent.app.ui.glassPanelColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

private const val PROFILE_FILE_MIME = "text/plain"
private val PROFILE_FILE_TYPES = arrayOf("text/plain", "text/*", "application/octet-stream")

private data class PendingProfileImport(
    val parse: ApiProfileTxtParse,
    val prepared: ApiProfileImportPreparation
)

@Composable
internal fun ApiProfileTransferActions(
    profiles: List<ApiProfile>,
    active: Boolean,
    onRequestTransfer: (() -> Unit) -> Unit,
    onApply: (List<ApiProfile>, ApiProfileImportMode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestProfiles = rememberUpdatedState(profiles)
    var pending by remember { mutableStateOf<PendingProfileImport?>(null) }
    var conflictPending by remember { mutableStateOf<PendingProfileImport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var transferMenuOpen by remember { mutableStateOf(false) }

    fun apply(mode: ApiProfileImportMode) {
        val request = pending ?: return
        pending = null
        if (mode == ApiProfileImportMode.Merge && request.prepared.conflicts.isNotEmpty()) {
            conflictPending = request
            return
        }
        onApply(request.prepared.profiles, mode)
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PROFILE_FILE_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val payload = ApiProfileTxt.encode(latestProfiles.value, System.currentTimeMillis(), TimeZone.getDefault())
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error(S.noDetails)
                }
            }
            busy = false
            LucentToast.show(
                context,
                outcome.fold(
                    onSuccess = { S.apiTransferExported(latestProfiles.value.size) },
                    onFailure = { S.apiTransferExportFailed(it.message ?: S.noDetails) }
                )
            )
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                        DocumentInputLimits.read(stream, ApiProfileTxt.MAX_FILE_BYTES)
                    } ?: error(S.noDetails)
                    ApiProfileTxt.decode(String(bytes, Charsets.UTF_8)).getOrThrow()
                }
            }
            busy = false
            outcome
                .onSuccess { parse ->
                    pending = PendingProfileImport(
                        parse = parse,
                        prepared = prepareApiProfileImport(latestProfiles.value, parse.profiles)
                    )
                }
                .onFailure { LucentToast.show(context, S.apiTransferImportFailed(it.message ?: S.noDetails)) }
        }
    }

    Box {
        GlassButton(
            text = S.apiTransferMenu,
            enabled = active && !busy,
            onClick = { transferMenuOpen = true }
        )
        DropdownMenu(
            expanded = transferMenuOpen && active && !busy,
            onDismissRequest = { transferMenuOpen = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = glassPanelColor(),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            DropdownMenuItem(
                text = { Text(S.apiTransferImport) },
                onClick = {
                    transferMenuOpen = false
                    onRequestTransfer { importer.launch(PROFILE_FILE_TYPES) }
                }
            )
            DropdownMenuItem(
                text = { Text(S.apiTransferExport) },
                enabled = profiles.isNotEmpty(),
                onClick = {
                    transferMenuOpen = false
                    onRequestTransfer {
                        exporter.launch(ApiProfileTxt.fileName(System.currentTimeMillis(), TimeZone.getDefault()))
                    }
                }
            )
        }
    }

    pending?.let { request ->
        AlertDialog(
            modifier = Modifier.border(0.75.dp, foregroundRim(), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            containerColor = glassPanelColor(),
            tonalElevation = 0.dp,
            onDismissRequest = { if (!busy) pending = null },
            title = {
                KeepKeyboardForNonEditingDialog()
                Text(S.apiTransferTitle, fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight)
            },
            text = {
                Column {
                    Text(
                        text = S.apiTransferRead(request.parse.profiles.size, request.parse.invalidLines),
                        color = LocalOnGradient.current,
                        fontSize = 13.sp
                    )
                    if (request.prepared.mergedDuplicateCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = S.apiTransferMergedKeys(request.prepared.mergedDuplicateCount),
                            color = LocalOnGradientMuted.current,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = S.apiTransferMergeHint,
                        color = LocalOnGradientMuted.current,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Text(
                        text = S.apiTransferReplaceHint,
                        color = LocalOnGradientMuted.current,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { apply(ApiProfileImportMode.Merge) }, enabled = active && !busy) {
                        Text(S.apiTransferMerge)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { apply(ApiProfileImportMode.Replace) }, enabled = active && !busy) {
                        Text(S.apiTransferReplace)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }, enabled = !busy) {
                    Text(S.actionCancel)
                }
            }
        )
    }

    conflictPending?.let { request ->
        val conflicts = request.prepared.conflicts
        val remaining = request.prepared.profiles.size - conflicts.size
        AlertDialog(
            modifier = Modifier.border(0.75.dp, foregroundRim(), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            containerColor = glassPanelColor(),
            tonalElevation = 0.dp,
            onDismissRequest = { if (!busy) conflictPending = null },
            title = {
                KeepKeyboardForNonEditingDialog()
                Text(S.apiTransferConflictTitle, fontSize = LucentTitles.EntrySize, fontWeight = LucentTitles.EntryWeight)
            },
            text = {
                Column {
                    Text(
                        text = S.apiTransferConflictBody(conflicts.size, remaining),
                        color = LocalOnGradient.current,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    conflicts.take(5).forEach { conflict ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = S.apiTransferConflictItem(conflict.importedName, conflict.existingName),
                            color = LocalOnGradientMuted.current,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        conflictPending = null
                        onApply(request.prepared.profiles, ApiProfileImportMode.Merge)
                    },
                    enabled = active && !busy && remaining > 0
                ) {
                    Text(S.apiTransferImportRemaining(remaining))
                }
            },
            dismissButton = {
                TextButton(onClick = { conflictPending = null }, enabled = !busy) {
                    Text(S.actionCancel)
                }
            }
        )
    }
}
