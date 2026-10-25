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
    @Test fun textScopePreviewKeepsTheSystemScaleAndAppliesTheDraftOnce() {
        assertEquals(1.2f * 1.3f, previewScopedScale(1.2f, 1.3f, 1.3f, textOnly = true), 0.0001f)
        assertEquals(1.56f, previewScopedScale(1.56f, 1.3f, 1.3f, textOnly = false), 0.0001f)
    }
}
