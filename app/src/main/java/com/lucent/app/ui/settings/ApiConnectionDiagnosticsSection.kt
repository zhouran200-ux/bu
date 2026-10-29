package com.lucent.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    hasModels: Boolean,
    testModel: String,
    reasoningProvider: String,
    reasoningBaseUrl: String,
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
        !hasModels -> S.apiProbeScopeHintNoModels
        else -> S.apiProbeScopeHint
    }

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

    ApiReasoningSummarySection(
        provider = reasoningProvider,
        model = testModel,
        baseUrl = reasoningBaseUrl
    )

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
