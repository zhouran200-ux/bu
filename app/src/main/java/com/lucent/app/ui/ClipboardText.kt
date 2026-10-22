package com.lucent.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.lucent.app.i18n.S

private const val CLIP_LABEL = "Lucent"

internal fun copyPlainText(context: Context, text: String): Boolean {
    if (text.isBlank()) return false
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    return runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text)) }.isSuccess
}

internal fun copyPlainTextWithToast(context: Context, text: String): Boolean {
    val copied = copyPlainText(context, text)
    LucentToast.show(context, if (copied) S.apiCopied else S.copyFailed)
    return copied
}
