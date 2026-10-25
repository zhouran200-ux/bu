package com.lucent.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S
import com.lucent.app.network.TestConnectionResult

@Composable
internal fun ApiConnectionDiagnosticsSection(
    active: Boolean,
    testingConn: Boolean,
    testStatusText: String,
    testSuccess: Boolean,
    lastTestResult: TestConnectionResult?,
    fetchModelsError: String,
    errorText: String,
    copiedRawHint: Boolean,
    testModels: List<String>,
    testModel: String,
    onTestModelChange: (String) -> Unit,
    onTest: () -> Unit,
    copyRawResponse: (String) -> Unit
) {
    val testBtnText = when {
        testingConn -> S.apiTesting
        testModel.isNotBlank() -> S.apiTestGeneration
        else -> S.apiTestConnection
    }
    val scopeHint = when {
        testModel.isNotBlank() -> S.apiProbeScopeHintModel(testModel)
        testModels.isEmpty() -> S.apiProbeScopeHintNoModels
        else -> S.apiProbeScopeHint
    }
    var modelMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) modelMenuOpen = false }
    val selectorShape = RoundedCornerShape(10.dp)

    Text(
        text = S.apiTestModel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
    Spacer(modifier = Modifier.height(4.dp))
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(selectorShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), selectorShape)
                .clickable(enabled = active) { modelMenuOpen = true }
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = testModel.ifBlank { S.apiTestModelConfigOnly },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = modelMenuOpen && active,
            onDismissRequest = { modelMenuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(S.apiTestModelConfigOnly, fontSize = 13.sp) },
                onClick = {
                    modelMenuOpen = false
                    onTestModelChange("")
                }
            )
            testModels.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        modelMenuOpen = false
                        onTestModelChange(model)
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = onTest,
        enabled = !testingConn && active,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = testBtnText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = scopeHint,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )

    if (testingConn) {
        Spacer(modifier = Modifier.height(6.dp))
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
    if (testStatusText.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = testStatusText,
            color = if (testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp
        )
    }

    lastTestResult?.let { res ->
        val prettyBody = remember(res) {
            if (res.success) res.prettyResponseBody() else buildString {
                appendLine(S.apiDetails)
                if (res.httpCode > 0) appendLine(S.apiHttpStatus(res.httpCode))
                appendLine(S.apiLatency(res.latencyMs))
                res.errorMessage?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                appendLine(S.apiRawResponse)
                append(res.prettyResponseBody().ifBlank { S.apiEmptyResponse })
            }
        }
        val textToCopy = prettyBody
        val displayBody = remember(prettyBody) {
            if (prettyBody.length <= 120_000) prettyBody else
                prettyBody.take(120_000) + "\n\n" + S.apiResponseTruncated(prettyBody.length)
        }
        Spacer(modifier = Modifier.height(8.dp))
        ResponseCard(
            body = displayBody,
            charCount = textToCopy.length,
            copied = copiedRawHint,
            onCopy = { copyRawResponse(textToCopy) }
        )
    }

    if (fetchModelsError.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        ResponseCard(
            body = fetchModelsError,
            charCount = fetchModelsError.length,
            copied = copiedRawHint,
            onCopy = { copyRawResponse(fetchModelsError) }
        )
    }

    if (errorText.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(errorText, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}
