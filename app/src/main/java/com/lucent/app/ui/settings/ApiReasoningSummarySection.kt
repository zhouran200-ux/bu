package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ReasoningPlanBuilder
import com.lucent.app.i18n.S

@Composable
internal fun ApiReasoningSummarySection(provider: String, model: String, baseUrl: String) {
    if (model.isBlank()) return
    val plan = remember(provider, model, baseUrl) { ReasoningPlanBuilder.build(provider, model, baseUrl) }

    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = S.apiReasoningTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (plan.supported) {
            val levels = if (plan.levels.isEmpty()) "-" else plan.levels.joinToString(" / ")
            Text(
                text = S.apiReasoningControl(plan.controlLabel, levels),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = S.apiReasoningPlan(plan.entries.joinToString(" · ") { "${it.first} ${it.second}" }),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = listOf(S.apiReasoningUnsupported, plan.note).filter { it.isNotBlank() }.joinToString("\n"),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
