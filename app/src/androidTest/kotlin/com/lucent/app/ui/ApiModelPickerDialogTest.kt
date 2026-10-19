package com.lucent.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.i18n.S
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiModelPickerDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun prepareSystemWindow() {
        rule.prepareSystemInteraction(rule.activity)
    }

    private val names = listOf("模型甲", "模型乙")

    @Test
    fun selectingAllThenConfirmingReportsEveryNameBeforeDismissal() {
        val done = AtomicReference<Set<String>?>(null)
        val events = mutableListOf<String>()
        rule.setContent {
            ApiModelPickerDialog(
                names = names,
                selected = emptySet(),
                onDone = { done.set(it); events += "done" },
                onDismiss = { events += "dismiss" }
            )
        }

        rule.onNodeWithText("模型甲").assertIsOff()
        rule.onNodeWithText(S.selectAll).performClick()
        rule.onNodeWithText("模型甲").assertIsOn()
        rule.onNodeWithText("模型乙").assertIsOn()
        rule.runOnIdle { assertNull(done.get()) }
        rule.onNodeWithText(S.settingsConfirmSelection(2)).performClick()
        rule.waitUntil(5_000) { events.size == 2 }
        rule.runOnIdle {
            assertEquals(setOf("模型甲", "模型乙"), done.get())
            assertEquals(listOf("done", "dismiss"), events)
        }
    }

    @Test
    fun individualRowsToggleDraftAndOnlyConfirmationSaves() {
        val done = AtomicReference<Set<String>?>(null)
        val dismissed = AtomicInteger(0)
        rule.setContent {
            ApiModelPickerDialog(names, setOf("模型甲"), done::set, { dismissed.incrementAndGet() })
        }

        rule.onNodeWithText("模型甲").assertIsOn().performClick()
        rule.onNodeWithText("模型甲").assertIsOff()
        rule.onNodeWithText("模型乙").assertIsOff().performClick()
        rule.onNodeWithText("模型乙").assertIsOn()
        rule.onNodeWithText("模型乙").performClick()
        rule.onNodeWithText("模型乙").assertIsOff().performClick()
        rule.onNodeWithText(S.settingsSelectedModels(1)).assertIsDisplayed()
        rule.runOnIdle {
            assertNull(done.get())
            assertEquals(0, dismissed.get())
        }
        rule.onNodeWithText(S.settingsConfirmSelection(1)).performClick()
        rule.waitUntil(5_000) { dismissed.get() == 1 }
        rule.runOnIdle { assertEquals(setOf("模型乙"), done.get()) }
    }

    @Test
    fun clearingAllSavesAnEmptySelectionOnlyAfterConfirmation() {
        val done = AtomicReference<Set<String>?>(null)
        val dismissed = AtomicInteger(0)
        rule.setContent {
            ApiModelPickerDialog(names, names.toSet(), done::set, { dismissed.incrementAndGet() })
        }

        rule.onNodeWithText(S.clearAllSelection).performClick()
        rule.onNodeWithText("模型甲").assertIsOff()
        rule.onNodeWithText("模型乙").assertIsOff()
        rule.onNodeWithText(S.settingsSelectedModels(0)).assertIsDisplayed()
        rule.runOnIdle {
            assertNull(done.get())
            assertEquals(0, dismissed.get())
        }
        rule.onNodeWithText(S.settingsConfirmSelection(0)).performClick()
        rule.waitUntil(5_000) { dismissed.get() == 1 }
        rule.runOnIdle { assertEquals(emptySet<String>(), done.get()) }
    }

    @Test
    fun cancellingEditedSelectionDiscardsDraftWhenReopened() {
        val open = mutableStateOf(true)
        val done = AtomicReference<Set<String>?>(null)
        val dismissed = AtomicInteger(0)
        rule.setContent {
            Text("picker host")
            if (open.value) {
                ApiModelPickerDialog(
                    names = names,
                    selected = setOf("模型甲"),
                    onDone = done::set,
                    onDismiss = { dismissed.incrementAndGet(); open.value = false }
                )
            }
        }

        rule.onNodeWithText(S.clearAllSelection).performClick()
        rule.onNodeWithText("模型乙").performClick().assertIsOn()
        rule.waitForIdle()
        rule.pressSystemBack(rule.onNodeWithText(S.apiModelsTitle))
        rule.waitUntil(5_000) { dismissed.get() == 1 }
        rule.onNodeWithText(S.apiModelsTitle).assertDoesNotExist()
        rule.runOnIdle {
            assertNull(done.get())
            open.value = true
        }
        rule.onNodeWithText("模型甲").assertIsOn()
        rule.onNodeWithText("模型乙").assertIsOff()
        rule.runOnIdle { assertNull(done.get()) }
    }

    @Test
    fun emptyModelListCanBeConfirmedWithoutInventingSelections() {
        val done = AtomicReference<Set<String>?>(null)
        rule.setContent {
            ApiModelPickerDialog(emptyList(), emptySet(), done::set, {})
        }

        rule.onNodeWithText(S.apiModelsEmpty).assertIsDisplayed()
        rule.onNodeWithText(S.selectAll).performClick()
        rule.onNodeWithText(S.settingsConfirmSelection(0)).performClick()
        rule.waitUntil(5_000) { done.get() != null }
        rule.runOnIdle { assertEquals(emptySet<String>(), done.get()) }
    }
}
