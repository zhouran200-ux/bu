package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTextDecoderTest {
    @Test fun utfBomVariantsKeepTextWithoutBom() {
        val text = "章节正文"
        assertEquals(text, DocumentTextDecoder.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, DocumentTextDecoder.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)))
        assertEquals(text, DocumentTextDecoder.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)))
    }

    @Test fun legacyChineseEncodingStillWorks() {
        val text = "用户原文"
        assertEquals(text, DocumentTextDecoder.decode(text.toByteArray(java.nio.charset.Charset.forName("GB18030"))))
    }
}
