package com.lucent.app.ui

import com.lucent.app.data.TypographyScopes
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTypographyTest {

    @Test
    fun defaultReadingTypographyKeepsTheExistingLineHeight() {
        assertEquals(1f, ReadingTypography().lineFactor, 0.0001f)
    }

    @Test
    fun lineFactorScalesFromTheDefaultLineSpacing() {
        assertEquals(1.25f, ReadingTypography(lineSpacing = 1.5f).lineFactor, 0.0001f)
        assertEquals(0.8333f, ReadingTypography(lineSpacing = 1f).lineFactor, 0.0001f)
    }

    @Test
    fun scopeNormalizationOnlyAcceptsText() {
        assertEquals(TypographyScopes.TEXT, TypographyScopes.normalize("text"))
        assertEquals(TypographyScopes.GLOBAL, TypographyScopes.normalize("global"))
        assertEquals(TypographyScopes.GLOBAL, TypographyScopes.normalize(null))
        assertEquals(TypographyScopes.GLOBAL, TypographyScopes.normalize("unexpected"))
    }
}
