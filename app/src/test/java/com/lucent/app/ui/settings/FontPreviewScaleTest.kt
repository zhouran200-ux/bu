package com.lucent.app.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class FontPreviewScaleTest {
    @Test fun savedScaleIsNotAppliedTwice() {
        assertEquals(1.3f, previewFontScale(1.3f, 1.3f, 1.3f), 0.0001f)
    }
    @Test fun draftReplacesSavedScaleWhileKeepingSystemAccessibilityScale() {
        assertEquals(1.5f * 0.85f, previewFontScale(1.5f * 1.3f, 1.3f, 0.85f), 0.0001f)
    }
    @Test fun committingDraftDoesNotResizePreviewAgain() {
        assertEquals(previewFontScale(1.2f, 1f, 1.3f), previewFontScale(1.2f * 1.3f, 1.3f, 1.3f), 0.0001f)
    }
}
