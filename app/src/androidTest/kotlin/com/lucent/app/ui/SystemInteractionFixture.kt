package com.lucent.app.ui

import android.app.Activity
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse

internal fun ComposeTestRule.prepareSystemInteraction(activity: Activity) {
    val keyguard = requireNotNull(activity.getSystemService(KeyguardManager::class.java))
    runOnUiThread {
        activity.setTurnScreenOn(true)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_WAKEUP)
    runOnUiThread {
        if (keyguard.isKeyguardLocked) {
            assertFalse("System interaction fixture requires an unsecured test device", keyguard.isDeviceSecure)
            activity.setShowWhenLocked(true)
            keyguard.requestDismissKeyguard(activity, null)
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }
    awaitSystemWindowFocus(activity.window.decorView)
    runOnUiThread { activity.setShowWhenLocked(false) }
}

internal fun ComposeTestRule.awaitSystemWindowFocus(node: SemanticsNodeInteraction) {
    awaitSystemWindowFocus(node.requireAndroidView())
}

internal fun ComposeTestRule.awaitSystemWindowFocus(view: View) {
    try {
        waitUntil(5_000) {
            runOnUiThread {
                val keyguard = requireNotNull(view.context.getSystemService(KeyguardManager::class.java))
                val power = requireNotNull(view.context.getSystemService(PowerManager::class.java))
                view.isAttachedToWindow && view.hasWindowFocus() && power.isInteractive &&
                    !keyguard.isKeyguardLocked && !keyguard.isDeviceLocked && !view.isImeVisible()
            }
        }
    } catch (failure: ComposeTimeoutException) {
        val state = runOnUiThread {
            val keyguard = requireNotNull(view.context.getSystemService(KeyguardManager::class.java))
            val power = requireNotNull(view.context.getSystemService(PowerManager::class.java))
            "attached=${view.isAttachedToWindow}, focused=${view.hasWindowFocus()}, " +
                "interactive=${power.isInteractive}, keyguardLocked=${keyguard.isKeyguardLocked}, " +
                "deviceLocked=${keyguard.isDeviceLocked}, imeVisible=${view.isImeVisible()}"
        }
        throw AssertionError("System interaction requires an unlocked, focused window without IME: $state", failure)
    }
}

internal fun ComposeTestRule.pressSystemBack(node: SemanticsNodeInteraction) {
    awaitSystemWindowFocus(node)
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
}

private fun SemanticsNodeInteraction.requireAndroidView(): View =
    (fetchSemanticsNode().root as? ViewRootForTest)?.view
        ?: error("System interaction fixture requires an Android Compose root")

private fun View.isImeVisible(): Boolean =
    ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true
