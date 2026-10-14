package com.lucent.app

import kotlin.test.Test
import kotlin.test.assertEquals

class TextSanitizationTest {

    @Test
    fun pastedBlankLineRunsCollapseToASingleBlankLine() {
        assertEquals("a\n\nb", collapseExcessBlankLines("a\n\n\nb"))
        assertEquals("a\n\nb", collapseExcessBlankLines("a\n\n\n\n\n\nb"))
        assertEquals("a\n\nb", collapseExcessBlankLines("a\n\nb"))
    }

    @Test
    fun otherWhitespaceAndContentAreLeftUntouched() {
        assertEquals("a\nb", collapseExcessBlankLines("a\nb"))
        assertEquals("", collapseExcessBlankLines(""))
        assertEquals("单行", collapseExcessBlankLines("单行"))
        assertEquals("a\n \nb", collapseExcessBlankLines("a\n \nb"))
        assertEquals("  a  ", collapseExcessBlankLines("  a  "))
    }
}