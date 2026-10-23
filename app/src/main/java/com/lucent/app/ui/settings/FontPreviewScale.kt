package com.lucent.app.ui.settings

internal fun previewFontScale(inheritedScale: Float, appliedScale: Float, draftScale: Float): Float =
    inheritedScale / appliedScale * draftScale
