package com.lucent.app.ui.settings

internal fun previewFontScale(inheritedScale: Float, appliedScale: Float, draftScale: Float): Float =
    inheritedScale / appliedScale * draftScale

internal fun previewScopedScale(
    inheritedScale: Float,
    appliedScale: Float,
    draftScale: Float,
    textOnly: Boolean
): Float = previewFontScale(inheritedScale, if (textOnly) 1f else appliedScale, draftScale)
