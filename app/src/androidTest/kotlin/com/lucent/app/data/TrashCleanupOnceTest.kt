package com.lucent.app.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrashCleanupOnceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("trash_cleanup_test_" + UUID.randomUUID(), Context.MODE_PRIVATE)
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val sampleBody = "\u3014CH:1\u3015 \u7b2c\u4e00\u767e\u96f6\u4e09\u7ae0 \u62e5\u62b1\n\n\u3014S:1-1\u3015\n\u5979\u4e0d\u559c\u6b22\u6709\u4eba\u62cd\u5979\u5f97\u5c81\u80a1\u3002"

    @After fun tearDown() {
        db.close()
        prefs.edit().clear().commit()
    }

    @Test fun cleansExactAndWhitespaceSamplesButPreservesEditedAndRenamedDocuments() = runBlocking {
        assertTrue(LegacyPurge.isSampleDocument(LegacyPurge.SAMPLE_TITLE, sampleBody))
        prefs.edit().putBoolean("legacy_attachment_purge_done", true).commit()
        val notebookId = db.notebookDao().insert(Notebook(title = "retained"))
        val sampleIds = listOf(
            LegacyPurge.SAMPLE_TITLE,
            " " + LegacyPurge.SAMPLE_TITLE + " ",
            "\t\n" + LegacyPurge.SAMPLE_TITLE + "\r\n",
            "\u00a0\u3000" + LegacyPurge.SAMPLE_TITLE + "\u2003"
        ).map { title -> db.noteDao().insert(Note(title = title, body = sampleBody)) }
        val retainedIds = listOf(
            Note(title = LegacyPurge.SAMPLE_TITLE, body = sampleBody + "edited"),
            Note(title = " " + LegacyPurge.SAMPLE_TITLE + " ", body = "my own content"),
            Note(title = LegacyPurge.SAMPLE_TITLE + " backup", body = sampleBody),
            Note(title = "different title", body = sampleBody)
        ).map { db.noteDao().insert(it) }
        (sampleIds + retainedIds).forEach { id ->
            db.notebookDao().insertItem(NotebookItem(notebookId = notebookId, itemKind = NotebookItem.KIND_NOTE, itemId = id))
        }

        TrashCleanup.discardLegacySampleDocumentsOnce(prefs) { db }

        sampleIds.forEach { assertNull(db.noteDao().getByIdOnce(it)) }
        retainedIds.forEach { assertNotNull(db.noteDao().getByIdOnce(it)) }
        assertEquals(retainedIds.toSet(), db.notebookDao().getItemsOnce(notebookId).map { it.itemId }.toSet())
        assertTrue(prefs.getBoolean(TrashCleanup.LEGACY_SAMPLE_PURGE_DONE, false))
        val later = db.noteDao().insert(Note(title = LegacyPurge.SAMPLE_TITLE, body = sampleBody))
        TrashCleanup.discardLegacySampleDocumentsOnce(prefs) { error("must skip database access") }
        assertNotNull(db.noteDao().getByIdOnce(later))
    }

    @Test fun failedDeleteRollsBackLinksAndEarlierDeletesAndDoesNotMarkDone() = runBlocking {
        val notebookId = db.notebookDao().insert(Notebook(title = "retained"))
        val exactId = db.noteDao().insert(Note(title = LegacyPurge.SAMPLE_TITLE, body = sampleBody))
        val paddedId = db.noteDao().insert(Note(title = " " + LegacyPurge.SAMPLE_TITLE, body = sampleBody))
        listOf(exactId, paddedId).forEach { id ->
            db.notebookDao().insertItem(NotebookItem(notebookId = notebookId, itemKind = NotebookItem.KIND_NOTE, itemId = id))
        }
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER prevent_sample_delete BEFORE DELETE ON notes WHEN OLD.id = $paddedId BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        )
        try {
            TrashCleanup.discardLegacySampleDocumentsOnce(prefs) { db }
            fail("must report failed cleanup")
        } catch (_: SQLiteException) { }
        assertFalse(prefs.getBoolean(TrashCleanup.LEGACY_SAMPLE_PURGE_DONE, false))
        assertNotNull(db.noteDao().getByIdOnce(exactId))
        assertNotNull(db.noteDao().getByIdOnce(paddedId))
        assertEquals(2, db.notebookDao().getItemsOnce(notebookId).size)

        db.openHelper.writableDatabase.execSQL("DROP TRIGGER prevent_sample_delete")
        TrashCleanup.discardLegacySampleDocumentsOnce(prefs) { db }
        assertTrue(prefs.getBoolean(TrashCleanup.LEGACY_SAMPLE_PURGE_DONE, false))
        assertTrue(db.noteDao().getAllOnce().isEmpty())
        assertTrue(db.notebookDao().getItemsOnce(notebookId).isEmpty())
    }

    @Test fun cancelledAttemptDoesNotMarkDoneAndCanRetry() = runBlocking {
        try {
            TrashCleanup.discardLegacySampleDocumentsOnce(prefs) { throw CancellationException("injected cancellation") }
            fail("must propagate cancellation")
        } catch (_: CancellationException) { }
        assertFalse(prefs.getBoolean(TrashCleanup.LEGACY_SAMPLE_PURGE_DONE, false))
        TrashCleanup.discardLegacySampleDocumentsOnce(prefs) { db }
        assertTrue(prefs.getBoolean(TrashCleanup.LEGACY_SAMPLE_PURGE_DONE, false))
    }

    @Test fun concurrentCallsRunOnlyOneCleanupEvenWhenNoSamplesExist() = runBlocking {
        var databaseRequests = 0
        List(4) {
            async { TrashCleanup.discardLegacySampleDocumentsOnce(prefs) { databaseRequests++; db } }
        }.awaitAll()
        assertEquals(1, databaseRequests)
        assertTrue(prefs.getBoolean(TrashCleanup.LEGACY_SAMPLE_PURGE_DONE, false))
    }

    @Test fun exactTitleQueryUsesExistingTitleIndexAndCompatibilityOnlyProjectsTitles() = runBlocking {
        val exact = db.noteDao().insert(Note(title = LegacyPurge.SAMPLE_TITLE, body = "edited"))
        val padded = db.noteDao().insert(Note(title = " " + LegacyPurge.SAMPLE_TITLE, body = "edited"))
        assertEquals(listOf(exact), db.noteDao().getByTitleOnce(LegacyPurge.SAMPLE_TITLE).map { it.id })
        assertEquals(listOf(NoteTitleCandidate(padded, " " + LegacyPurge.SAMPLE_TITLE)),
            db.noteDao().getNonExactTitlesOnce(LegacyPurge.SAMPLE_TITLE))
        val plan = db.openHelper.readableDatabase.query(
            androidx.sqlite.db.SimpleSQLiteQuery(
                "EXPLAIN QUERY PLAN SELECT * FROM notes WHERE title = ?", arrayOf<Any>(LegacyPurge.SAMPLE_TITLE)
            )
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) }
        }
        assertTrue(plan.toString(), plan.any { it.contains("SEARCH", ignoreCase = true) && it.contains("index_notes_title") })
    }
}
