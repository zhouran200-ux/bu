package com.lucent.app.ui

import android.view.ViewParent
import android.view.WindowManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties

internal val NonEditingPopupProperties = PopupProperties(focusable = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberImeVisibleOnOpen(): Boolean {
    val visible = WindowInsets.isImeVisible
    return remember { visible }
}

@Composable
internal fun KeepKeyboardForNonEditingDialog() {
    val view = LocalView.current
    DisposableEffect(view) {
        var parent: ViewParent? = view.parent
        var provider: DialogWindowProvider? = view as? DialogWindowProvider
        while (provider == null && parent != null) {
            provider = parent as? DialogWindowProvider
            parent = parent.parent
        }
        val window = provider?.window
        val flag = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        val hadFlag = ((window?.attributes?.flags ?: 0) and flag) != 0
        window?.addFlags(flag)
        onDispose {
            if (!hadFlag) window?.clearFlags(flag)
        }
    }
}

@Composable
internal fun PreserveEditingKeyboardOnOpen(wasVisible: Boolean, requester: FocusRequester) {
    LaunchedEffect(requester) {
        if (wasVisible) requester.requestFocus()
    }
}
