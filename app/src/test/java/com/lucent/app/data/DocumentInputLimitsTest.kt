package com.lucent.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class DocumentInputLimitsTest {
    @Test fun acceptsExactLimitAndRejectsOneByteMore() {
        assertEquals(8, DocumentInputLimits.read(ByteArrayInputStream(ByteArray(8)), 8).size)
        try { DocumentInputLimits.read(ByteArrayInputStream(ByteArray(9)), 8); fail("Expected limit failure") }
        catch (_: IOException) { }
    }

    @Test fun discardedEntriesAreAlsoBounded() {
        assertEquals(8L, DocumentInputLimits.discard(ByteArrayInputStream(ByteArray(8)), 8))
        try { DocumentInputLimits.discard(ByteArrayInputStream(ByteArray(9)), 8); fail("Expected discard limit failure") }
        catch (_: IOException) { }
    }

    @Test fun noProgressReadCannotSpinForever() {
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), DocumentInputLimits.read(input, 3))
    }
}
