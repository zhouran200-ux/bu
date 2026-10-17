package com.lucent.app.data

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FontStoreTransactionTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val isolated = File(app.cacheDir, "font-test-" + java.util.UUID.randomUUID()).apply { mkdirs() }
    private val context = object : ContextWrapper(app) { override fun getFilesDir(): File = isolated }
    private val source = File(isolated, "source.ttf").apply { writeBytes(byteArrayOf(0, 1, 0, 0) + ByteArray(4096)) }

    @After fun cleanup() { isolated.deleteRecursively() }

    @Test fun concurrentImportsKeepEveryCommittedSlotAndRespectCapacity() {
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val futures = (1..20).map { executor.submit(Callable {
                start.await()
                try { FontStore.import(context, Uri.fromFile(source), "font").id }
                catch (_: FontStore.TooManyFontsException) { null }
            }) }
            start.countDown()
            val ids = futures.mapNotNull { it.get(20, TimeUnit.SECONDS) }
            val slots = FontStore.index(context).slots
            assertEquals(FontStore.MAX_FONTS, ids.size)
            assertEquals(ids.toSet(), slots.map { it.id }.toSet())
            assertEquals(ids.size, slots.size)
            assertFalse(File(isolated, "fonts").listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally { executor.shutdownNow() }
    }

    @Test fun deletionAndImportNeverReintroduceDeletedSlot() {
        val deleted = FontStore.import(context, Uri.fromFile(source), "old")
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val importing = executor.submit(Callable { start.await(); FontStore.import(context, Uri.fromFile(source), "new") })
            val deleting = executor.submit(Callable { start.await(); FontStore.delete(context, deleted.id) })
            start.countDown()
            val added = importing.get(20, TimeUnit.SECONDS)
            deleting.get(20, TimeUnit.SECONDS)
            assertEquals(listOf(added.id), FontStore.index(context).slots.map { it.id })
            assertNull(FontStore.fontFile(context, deleted.id))
        } finally { executor.shutdownNow() }
    }

    @Test fun missingIndexRecoversLastAtomicBackup() {
        val slot = FontStore.import(context, Uri.fromFile(source), "kept")
        val index = File(isolated, "fonts/fonts.json")
        assertTrue(index.renameTo(File(isolated, "fonts/fonts.json.bak")))
        assertEquals(listOf(slot.id), FontStore.index(context).slots.map { it.id })
    }
}
