package com.lucent.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.ui.settings.ApiModelsSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiModelsSectionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val clipboard: ClipboardManager
        get() = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @After fun clearClipboard() {
        rule.runOnUiThread { clipboard.clearPrimaryClip() }
    }

    @Test fun clickSelectsModelAndSecondClickClearsTestModel() {
        val selected = mutableStateOf("")
        rule.setContent {
            MaterialTheme {
                Column {
                    ApiModelsSection(
                        models = listOf("模型甲", "模型乙"),
                        testModel = selected.value,
                        onTestModelChange = { selected.value = it },
                        customModelInput = "",
                        onModelsChange = {},
                        onCustomModelInputChange = {}
                    )
                }
            }
        }
        rule.onNodeWithText("模型甲").performClick()
        rule.runOnIdle { assertEquals("模型甲", selected.value) }
        rule.onNodeWithText("模型甲").assertIsSelected().performClick()
        rule.runOnIdle { assertEquals("", selected.value) }
    }

    @Test fun longPressCopiesModelNameWithoutSelectingIt() {
        val selected = mutableStateOf("")
        rule.setContent {
            MaterialTheme {
                Column {
                    ApiModelsSection(
                        models = listOf("provider/model-with-long-name"),
                        testModel = selected.value,
                        onTestModelChange = { selected.value = it },
                        customModelInput = "",
                        onModelsChange = {},
                        onCustomModelInputChange = {}
                    )
                }
            }
        }
        rule.onNodeWithText("provider/model-with-long-name").performTouchInput { longClick() }
        rule.waitUntil(5_000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "provider/model-with-long-name"
        }
        rule.runOnIdle {
            assertEquals("provider/model-with-long-name", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            assertEquals("", selected.value)
        }
    }
}
