package com.lucent.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfileImportMode
import com.lucent.app.data.ApiProfileImportPlan
import com.lucent.app.data.ApiProfileTxt
import com.lucent.app.data.ApiProfileTxtParse
import com.lucent.app.data.DocumentInputLimits
import com.lucent.app.data.planApiProfileImport
import com.lucent.app.i18n.S
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

private const val PROFILE_FILE_MIME = "text/plain"
private val PROFILE_FILE_TYPES = arrayOf("text/plain", "text/*", "application/octet-stream")

@Composable
internal fun ApiProfileTransferSection(
    profiles: List<ApiProfile>,
    selectedProfileId: String?,
    active: Boolean,
    onApply: (ApiProfileImportPlan) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var pending by remember { mutableStateOf<ApiProfileTxtParse?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun fail(message: String) {
        pending = null
        LucentToast.show(context, message)
    }

    fun apply(mode: ApiProfileImportMode) {
        val parse = pending ?: return
        val plan = planApiProfileImport(profiles, selectedProfileId, parse.profiles, mode)
        if (mode == ApiProfileImportMode.Merge && plan.added == 0 && plan.updated == 0 && plan.dropped == 0) {
            fail(S.apiTransferNothing)
            return
        }
        pending = null
        onApply(plan)
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PROFILE_FILE_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val payload = ApiProfileTxt.encode(profiles, System.currentTimeMillis(), TimeZone.getDefault())
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
                    onSuccess = { S.apiTransferExported(profiles.size) },
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
                .onSuccess { pending = it }
                .onFailure { LucentToast.show(context, S.apiTransferImportFailed(it.message ?: S.noDetails)) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(12.dp)) {
        Text(
            text = S.apiTransferTitle,
            color = onGradient,
            fontSize = LucentTitles.SectionSize,
            fontWeight = LucentTitles.SectionWeight
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = S.apiTransferHint, color = onGradientMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton(
                text = S.apiTransferExport,
                enabled = active && !busy && profiles.isNotEmpty(),
                onClick = {
                    exporter.launch(ApiProfileTxt.fileName(System.currentTimeMillis(), TimeZone.getDefault()))
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassButton(
                text = S.apiTransferImport,
                enabled = active && !busy,
                onClick = { importer.launch(PROFILE_FILE_TYPES) }
            )
        }

        pending?.let { parse ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = S.apiTransferRead(parse.profiles.size, parse.invalidLines),
                color = onGradient,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = S.apiTransferMergeHint, color = onGradientMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Text(text = S.apiTransferReplaceHint, color = onGradientMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(
                    text = S.apiTransferMerge,
                    enabled = active && !busy,
                    onClick = { apply(ApiProfileImportMode.Merge) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                GlassButton(
                    text = S.apiTransferReplace,
                    enabled = active && !busy,
                    onClick = { apply(ApiProfileImportMode.Replace) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                GlassButton(
                    text = S.actionCancel,
                    enabled = !busy,
                    onClick = { pending = null }
                )
            }
        }
    }
}
