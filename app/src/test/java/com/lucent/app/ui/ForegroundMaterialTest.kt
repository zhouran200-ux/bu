package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class ForegroundMaterialTest {
    @Test fun readingLayerIsDenserThanOrdinaryCardButNotOpaque() {
        for (dark in listOf(false, true)) {
            val card = foregroundMaterialSpec(dark, false)
            val reading = foregroundMaterialSpec(dark, true)
            assertTrue(reading.tintAlpha > card.tintAlpha)
            assertTrue(reading.tintAlpha < 1f)
            assertTrue(card.fallbackAlpha >= card.tintAlpha)
            assertTrue(reading.fallbackAlpha >= reading.tintAlpha)
            assertTrue(reading.blur >= card.blur)
        }
    }
}
