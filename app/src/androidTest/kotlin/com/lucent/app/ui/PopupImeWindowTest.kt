package com.lucent.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PopupImeWindowTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun helperOnlyFlagsDialogAndClearsFlagWhenRemovedWhileDialogStaysOpen() {
        verifyDialogFlagLifecycle(initiallySet = false)
    }

    @Test
    fun helperPreservesPreExistingDialogFlagOnDisposal() {
        verifyDialogFlagLifecycle(initiallySet = true)
    }

    private fun verifyDialogFlagLifecycle(initiallySet: Boolean) {
        val enabled = mutableStateOf(false)
        var activityWindow: Window? = null
        var dialogWindow: Window? = null
        rule.setContent {
            val context = LocalContext.current
            SideEffect { activityWindow = context.requireActivity().window }
            Dialog(onDismissRequest = {}) {
                val view = LocalView.current
                SideEffect { dialogWindow = view.requireDialogWindow() }
                if (enabled.value) KeepKeyboardForNonEditingDialog()
                Text("window flag probe dialog")
            }
        }
        rule.onNodeWithText("window flag probe dialog").assertIsDisplayed()
        val flag = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        var initialDialogFlags = 0
        var activityFlags = 0
        rule.runOnIdle {
            val dialog = requireNotNull(dialogWindow)
            val activity = requireNotNull(activityWindow)
            assertNotSame(activity, dialog)
            initialDialogFlags = dialog.attributes.flags
            activityFlags = activity.attributes.flags
            if (initiallySet) dialog.addFlags(flag) else dialog.clearFlags(flag)
            assertEquals(initiallySet, dialog.attributes.flags and flag != 0)
        }
        try {
            rule.runOnIdle { enabled.value = true }
            rule.runOnIdle {
                assertTrue(requireNotNull(dialogWindow).attributes.flags and flag != 0)
                assertEquals(activityFlags, requireNotNull(activityWindow).attributes.flags)
            }
            rule.runOnIdle { enabled.value = false }
            rule.onNodeWithText("window flag probe dialog").assertIsDisplayed()
            rule.runOnIdle {
                assertEquals(initiallySet, requireNotNull(dialogWindow).attributes.flags and flag != 0)
                assertEquals(activityFlags, requireNotNull(activityWindow).attributes.flags)
            }
        } finally {
            rule.runOnIdle {
                enabled.value = false
            }
            rule.runOnIdle {
                val dialog = requireNotNull(dialogWindow)
                if (initialDialogFlags and flag != 0) dialog.addFlags(flag) else dialog.clearFlags(flag)
            }
        }
    }

    private fun Context.requireActivity(): Activity {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        error("Expected Activity-backed Compose host")
    }

    private fun View.requireDialogWindow(): Window {
        if (this is DialogWindowProvider) return window
        var current: ViewParent? = parent
        while (current != null) {
            if (current is DialogWindowProvider) return current.window
            current = current.parent
        }
        error("Expected actual Compose Dialog window provider")
    }
}
