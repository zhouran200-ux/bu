package com.lucent.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.i18n.S
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiModelPickerDialogTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun selectingAllThenConfirmingReportsEveryName() {
        val done = AtomicReference<Set<String>?>(null)

        rule.setContent {
            ApiModelPickerDialog(
                names = listOf("模型甲", "模型乙"),
                selected = emptySet(),
                onDone = { done.set(it) },
                onDismiss = {}
            )
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(S.selectAll).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(S.selectAll).performClick()
        rule.onNodeWithText(S.settingsConfirmSelection(2)).performClick()

        rule.waitUntil(timeoutMillis = 5_000) { done.get() != null }
        assertEquals(setOf("模型甲", "模型乙"), done.get())
    }
}
