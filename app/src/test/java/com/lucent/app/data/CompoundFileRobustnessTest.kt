package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompoundFileRobustnessTest {

    private fun compoundHeader(size: Int = 1024): ByteArray {
        val bytes = ByteArray(size)
        byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
        ).copyInto(bytes)
        return bytes
    }

    private fun controlledFailure(bytes: ByteArray): Throwable? =
        runCatching { DocTextExtractor.extract(bytes) }.exceptionOrNull()

    @Test
    fun littleEndianReadersMatchTheSpec() {
        val data = byteArrayOf(0x34, 0x12, 0x78, 0x56, 0x01, 0x00)
        assertEquals(0x1234, readLe16(data, 0))
        assertEquals(0x5678, readLe16(data, 2))
        assertEquals(0x56781234, readLe32(data, 0))
        assertEquals(0x00015678, readLe32(data, 2))
    }

    private fun putLe16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putLe32(bytes: ByteArray, at: Int, value: Int) {
        for (index in 0..3) bytes[at + index] = ((value ushr (8 * index)) and 0xFF).toByte()
    }

    private fun minimalCompoundFile(): ByteArray {
        val bytes = ByteArray(512 * 3)
        byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
        ).copyInto(bytes)
        putLe16(bytes, 0x1E, 9)
        putLe16(bytes, 0x20, 6)
        putLe32(bytes, 0x2C, 1)
        putLe32(bytes, 0x30, 1)
        putLe32(bytes, 0x38, 4096)
        putLe32(bytes, 0x3C, -2)
        putLe32(bytes, 0x40, 0)
        putLe32(bytes, 0x44, -2)
        putLe32(bytes, 0x48, 0)
        putLe32(bytes, 0x4C, 0)
        for (index in 1 until 109) putLe32(bytes, 0x4C + index * 4, -1)
        val fat = 512
        putLe32(bytes, fat, -3)
        putLe32(bytes, fat + 4, -2)
        for (index in 2 until 128) putLe32(bytes, fat + index * 4, -1)
        val directory = 1024
        "Root Entry".toByteArray(Charsets.UTF_16LE).copyInto(bytes, directory)
        putLe16(bytes, directory + 0x40, 22)
        bytes[directory + 0x42] = 5
        putLe32(bytes, directory + 0x74, -2)
        putLe32(bytes, directory + 0x78, 0)
        return bytes
    }

    @Test
    fun parseableCompoundFileWithoutTheStreamReportsNoStream() {
        val store = CompoundFile(minimalCompoundFile())
        assertNull(store.stream("WordDocument"))
        assertNull(store.stream("1Table"))
    }

    @Test
    fun truncatedFileUnderOneSectorIsRejected() {
        val failure = runCatching { CompoundFile(compoundHeader(size = 511)) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun zeroedHeaderWithSignatureFailsAsAControlledException() {
        val failure = controlledFailure(compoundHeader())
        assertNotNull("签名正确但内容为空必须报错", failure)
        assertTrue("必须是可捕获的异常而不是 Error", failure is Exception)
    }

    @Test
    fun absurdSectorShiftDoesNotProduceAnUncatchableError() {
        val bytes = compoundHeader()
        bytes[0x1E] = 0x1F
        bytes[0x1F] = 0x00
        val failure = controlledFailure(bytes)
        assertNotNull(failure)
        assertTrue(failure is Exception)
    }

    @Test
    fun hugeSectorSizeAndSectorCountStayBounded() {
        val bytes = compoundHeader()
        bytes[0x1E] = 0x10
        bytes[0x1F] = 0x00
        bytes[0x2C] = 0xFF.toByte()
        bytes[0x2D] = 0xFF.toByte()
        bytes[0x2E] = 0xFF.toByte()
        bytes[0x2F] = 0x7F
        val failure = controlledFailure(bytes)
        assertNotNull(failure)
        assertTrue(failure is Exception)
    }

    @Test
    fun docxArchiveIsNotTreatedAsOleCompoundStorage() {
        val zip = ByteArray(2048)
        zip[0] = 'P'.code.toByte()
        zip[1] = 'K'.code.toByte()
        val failure = controlledFailure(zip)
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun longSectorChainTerminatesInsteadOfLoopingForever() {
        val bytes = compoundHeader(size = 8192)
        bytes[0x1E] = 0x09
        bytes[0x1F] = 0x00
        bytes[0x2C] = 0x10
        bytes[0x30] = 0x01
        val failure = controlledFailure(bytes)
        assertNotNull(failure)
        assertTrue(failure is Exception)
    }
}