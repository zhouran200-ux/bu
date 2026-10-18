package com.lucent.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN attachments TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN attachments TEXT NOT NULL DEFAULT '[]'")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN dueAt INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER")
        db.execSQL("UPDATE tasks SET completedAt = createdAt WHERE isDone = 1 AND completedAt IS NULL")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS chat_conversations (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN conversationId INTEGER NOT NULL DEFAULT 1")
        val now = System.currentTimeMillis()
        val cursor = db.query("SELECT COUNT(*) FROM chat_messages")
        val count = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        cursor.close()
        if (count > 0) {
            db.execSQL(
                "INSERT INTO chat_conversations (id, title, createdAt, updatedAt) " +
                    "VALUES (1, 'Conversation', $now, $now)"
            )
        }
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN archivedAt INTEGER")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN isChecklist INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN checklist TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE notes ADD COLUMN trashedAt INTEGER")

        db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN subtasks TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN repeatRule TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN trashedAt INTEGER")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_versions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`noteId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`tags` TEXT NOT NULL, " +
                "`isChecklist` INTEGER NOT NULL, " +
                "`checklist` TEXT NOT NULL, " +
                "`savedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_versions_noteId` ON `note_versions` (`noteId`)")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN tokens INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_archived` ON `notes` (`archived`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_trashedAt` ON `notes` (`trashedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_createdAt` ON `tasks` (`createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_isDone` ON `tasks` (`isDone`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_trashedAt` ON `tasks` (`trashedAt`)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN manualOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN isDraft INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN draftSavedAt INTEGER")
        db.execSQL("ALTER TABLE notes ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE tasks ADD COLUMN manualOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN isDraft INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN draftSavedAt INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_versions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, " +
                "`subtasks` TEXT NOT NULL, " +
                "`priority` INTEGER NOT NULL, " +
                "`dueAt` INTEGER, " +
                "`savedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_versions_taskId` ON `task_versions` (`taskId`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_isDraft` ON `notes` (`isDraft`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_hidden` ON `notes` (`hidden`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_isDraft` ON `tasks` (`isDraft`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_hidden` ON `tasks` (`hidden`)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN isDoodle INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN doodle TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN replyToId INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN bodySpans TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE tasks ADD COLUMN notesSpans TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentList TEXT")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notebooks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notebooks_updatedAt` ON `notebooks` (`updatedAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notebook_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`notebookId` INTEGER NOT NULL, " +
                "`itemKind` TEXT NOT NULL, " +
                "`itemId` INTEGER NOT NULL, " +
                "`addedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notebook_items_notebookId` ON `notebook_items` (`notebookId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notebook_items_itemKind_itemId` ON `notebook_items` (`itemKind`, `itemId`)")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING fts5(" +
                    "title, content, content='notes', content_rowid='id')"
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `tasks_fts` USING fts5(" +
                    "title, content, content='tasks', content_rowid='id')"
            )

            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `notes_fts_ai` AFTER INSERT ON `notes` BEGIN " +
                    "INSERT INTO `notes_fts`(rowid, title, content) VALUES (new.id, new.title, new.body); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `notes_fts_ad` AFTER DELETE ON `notes` BEGIN " +
                    "INSERT INTO `notes_fts`(`notes_fts`, rowid, title, content) VALUES ('delete', old.id, old.title, old.body); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `notes_fts_au` AFTER UPDATE ON `notes` BEGIN " +
                    "INSERT INTO `notes_fts`(`notes_fts`, rowid, title, content) VALUES ('delete', old.id, old.title, old.body); " +
                    "INSERT INTO `notes_fts`(rowid, title, content) VALUES (new.id, new.title, new.body); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `tasks_fts_ai` AFTER INSERT ON `tasks` BEGIN " +
                    "INSERT INTO `tasks_fts`(rowid, title, content) VALUES (new.id, new.title, new.notes); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `tasks_fts_ad` AFTER DELETE ON `tasks` BEGIN " +
                    "INSERT INTO `tasks_fts`(`tasks_fts`, rowid, title, content) VALUES ('delete', old.id, old.title, old.notes); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `tasks_fts_au` AFTER UPDATE ON `tasks` BEGIN " +
                    "INSERT INTO `tasks_fts`(`tasks_fts`, rowid, title, content) VALUES ('delete', old.id, old.title, old.notes); " +
                    "INSERT INTO `tasks_fts`(rowid, title, content) VALUES (new.id, new.title, new.notes); END"
            )
            db.execSQL("INSERT INTO `notes_fts`(`notes_fts`) VALUES('rebuild')")
            db.execSQL("INSERT INTO `tasks_fts`(`tasks_fts`) VALUES('rebuild')")
        } catch (t: Throwable) {
            android.util.Log.w("LucentDb", "FTS5 unavailable during 17->18 migration; continuing without FTS", t)
        }
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_embeddings` (" +
                "`noteId` INTEGER NOT NULL, `model` TEXT NOT NULL, `dim` INTEGER NOT NULL, " +
                "`vec` BLOB NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`noteId`, `model`))"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `note_embeddings_cleanup` AFTER DELETE ON `notes` BEGIN " +
                "DELETE FROM `note_embeddings` WHERE `noteId` = old.id; END"
        )
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN agentTrace TEXT")
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN formatOverride TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN formatOverride TEXT")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notebooks ADD COLUMN color TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notebooks ADD COLUMN manualOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notebooks ADD COLUMN trashedAt INTEGER")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notebooks ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN reasoningBlocks TEXT")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN reasoningText TEXT")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN quotedRole TEXT")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN quotedText TEXT")
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes_26` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`tags` TEXT NOT NULL, `attachments` TEXT NOT NULL, `archived` INTEGER NOT NULL, " +
                "`archivedAt` INTEGER, `pinned` INTEGER NOT NULL, `color` TEXT NOT NULL, " +
                "`trashedAt` INTEGER, `manualOrder` INTEGER NOT NULL, `isDraft` INTEGER NOT NULL, " +
                "`draftSavedAt` INTEGER, `hidden` INTEGER NOT NULL, `bodySpans` TEXT NOT NULL, " +
                "`formatOverride` TEXT)"
        )
        db.execSQL(
            "INSERT INTO `notes_26` (`id`, `title`, `body`, `updatedAt`, `tags`, `attachments`, `archived`, `archivedAt`, `pinned`, `color`, `trashedAt`, `manualOrder`, `isDraft`, `draftSavedAt`, `hidden`, `bodySpans`, `formatOverride`) " +
                "SELECT `id`, `title`, `body`, `updatedAt`, `tags`, `attachments`, `archived`, `archivedAt`, `pinned`, `color`, `trashedAt`, `manualOrder`, `isDraft`, `draftSavedAt`, `hidden`, `bodySpans`, `formatOverride` FROM `notes`"
        )
        db.execSQL("DROP TABLE `notes`")
        db.execSQL("ALTER TABLE `notes_26` RENAME TO `notes`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_archived` ON `notes` (`archived`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_trashedAt` ON `notes` (`trashedAt`)")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_messages_27` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `attachmentMime` TEXT, " +
                "`attachmentData` TEXT, `attachmentName` TEXT, `attachmentList` TEXT, " +
                "`conversationId` INTEGER NOT NULL, `tokens` INTEGER NOT NULL, `replyToId` INTEGER NOT NULL, " +
                "`reasoningBlocks` TEXT, `reasoningText` TEXT, `quotedRole` TEXT, `quotedText` TEXT)"
        )
        db.execSQL(
            "INSERT INTO `chat_messages_27` (`id`, `role`, `content`, `timestamp`, `attachmentMime`, `attachmentData`, `attachmentName`, `attachmentList`, `conversationId`, `tokens`, `replyToId`, `reasoningBlocks`, `reasoningText`, `quotedRole`, `quotedText`) " +
                "SELECT `id`, `role`, `content`, `timestamp`, `attachmentMime`, `attachmentData`, `attachmentName`, `attachmentList`, `conversationId`, `tokens`, `replyToId`, `reasoningBlocks`, `reasoningText`, `quotedRole`, `quotedText` FROM `chat_messages`"
        )
        db.execSQL("DROP TABLE `chat_messages`")
        db.execSQL("ALTER TABLE `chat_messages_27` RENAME TO `chat_messages`")
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS `note_embeddings_cleanup`")
        db.execSQL("DROP TABLE IF EXISTS `note_embeddings`")
        db.execSQL("DROP TABLE IF EXISTS `note_versions`")
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN cachedTokens INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN answerSeconds INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `chat_conversations`")
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN firstTokenMillis INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN generationMillis INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN cachedTokensKnown INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN sourceNoteId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN sourceRevision TEXT DEFAULT NULL")
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_timestamp ON chat_messages(conversationId, timestamp)")
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS `notes_fts_ai`")
        db.execSQL("DROP TRIGGER IF EXISTS `notes_fts_ad`")
        db.execSQL("DROP TRIGGER IF EXISTS `notes_fts_au`")
        db.execSQL("DROP TRIGGER IF EXISTS `tasks_fts_ai`")
        db.execSQL("DROP TRIGGER IF EXISTS `tasks_fts_ad`")
        db.execSQL("DROP TRIGGER IF EXISTS `tasks_fts_au`")
        db.execSQL("DROP TRIGGER IF EXISTS `note_embeddings_cleanup`")
        db.execSQL("DROP TABLE IF EXISTS `notes_fts`")
        db.execSQL("DROP TABLE IF EXISTS `tasks_fts`")
        db.execSQL("DROP TABLE IF EXISTS `tasks`")
        db.execSQL("DROP TABLE IF EXISTS `task_versions`")
        db.execSQL("DROP INDEX IF EXISTS `index_notes_isDraft`")
        db.execSQL("DROP INDEX IF EXISTS `index_notes_hidden`")
        db.execSQL("DROP INDEX IF EXISTS `index_tasks_isDraft`")
        db.execSQL("DROP INDEX IF EXISTS `index_tasks_hidden`")
    }
}
