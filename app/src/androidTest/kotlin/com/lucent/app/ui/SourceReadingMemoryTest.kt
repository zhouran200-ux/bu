package com.lucent.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceReadingMemoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notebookId = -System.nanoTime()
    private val keys = SourceReadingMemoryKeys(notebookId, 7L, "revision-a")

    @After fun clearMemory() {
        ReadingPositionMemory.clearNotebook(context, notebookId)
        ReadingPositionMemory.clearNotebook(context, notebookId - 1)
    }

    @Test fun legacySharedKeyMigratesToDocumentWithoutInventingModelProgress() {
        val legacy = ReadingPosition("slice:S11-1", 21, 137)
        ReadingPositionMemory.write(context, keys.legacy, legacy)
        assertEquals(legacy, ReadingPositionMemory.readSourceDocument(context, keys))
        assertEquals(legacy, ReadingPositionMemory.read(context, keys.document))
        assertNull(ReadingPositionMemory.read(context, keys.legacy))
        assertNull(ReadingPositionMemory.read(context, keys.model))
        assertFalse(ReadingPositionMemory.readSourcePage(context, keys))
        assertEquals(legacy, ReadingPositionMemory.readSourceDocument(context, keys))
    }

    @Test fun migrationNeverReplacesAnIndependentDocumentPosition() {
        val document = ReadingPosition("slice:S21-1", 41, 219)
        ReadingPositionMemory.write(context, keys.legacy, ReadingPosition("slice:S11-1", 21, 137))
        ReadingPositionMemory.write(context, keys.document, document)
        assertEquals(document, ReadingPositionMemory.readSourceDocument(context, keys))
        assertNull(ReadingPositionMemory.read(context, keys.legacy))
    }

    @Test fun positionsAndPageAreIsolatedByNotebookSourceAndRevision() {
        val document = ReadingPosition("slice:S11-1", 21, 137)
        val model = ReadingPosition("slice:S21-1", 41, 219)
        ReadingPositionMemory.write(context, keys.document, document)
        ReadingPositionMemory.write(context, keys.model, model)
        ReadingPositionMemory.writeSourcePage(context, keys, true)
        val others = listOf(
            keys.copy(notebookId = notebookId - 1),
            keys.copy(sourceId = 8L),
            keys.copy(revision = "revision-b")
        )
        others.forEach { other ->
            assertNull(ReadingPositionMemory.readSourceDocument(context, other))
            assertNull(ReadingPositionMemory.read(context, other.model))
            assertFalse(ReadingPositionMemory.readSourcePage(context, other))
        }
        assertEquals(document, ReadingPositionMemory.readSourceDocument(context, keys))
        assertEquals(model, ReadingPositionMemory.read(context, keys.model))
        assertTrue(ReadingPositionMemory.readSourcePage(context, keys))
        ReadingPositionMemory.write(context, others.first().document, document)
        ReadingPositionMemory.clearNotebook(context, notebookId)
        assertNull(ReadingPositionMemory.readSourceDocument(context, keys))
        assertNull(ReadingPositionMemory.read(context, keys.model))
        assertFalse(ReadingPositionMemory.readSourcePage(context, keys))
        assertEquals(document, ReadingPositionMemory.readSourceDocument(context, others.first()))
    }
}
