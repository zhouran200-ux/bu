package com.lucent.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals

internal class LegacyMigrationFixture(val databaseName: String = "app-database-legacy-migration-test") {
    private val legacyOpenHelpers = mutableListOf<SupportSQLiteOpenHelper>()
    private val legacyMigrations = arrayOf(
        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
        MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
        MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
        MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
        MIGRATION_24_25
    )

    fun createLegacyDatabase(version: Int): SupportSQLiteDatabase {
        require(version in 2..25)
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
        return openLegacyDatabase(object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, body TEXT NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, isDone INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE chat_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "role TEXT NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL, " +
                        "attachmentMime TEXT, attachmentData TEXT, attachmentName TEXT)"
                )
                applyLegacyMigrations(db, 2, version, legacyMigrations)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("Legacy fixture must be created from an empty database")
            }
        })
    }

    fun runLegacyMigrations(
        version: Int,
        vararg migrations: Migration
    ): SupportSQLiteDatabase = openLegacyDatabase(object : SupportSQLiteOpenHelper.Callback(version) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            error("Create and seed the legacy fixture before migrating it")
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            applyLegacyMigrations(db, oldVersion, newVersion, migrations)
        }
    })

    private fun openLegacyDatabase(callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteDatabase {
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(
                InstrumentationRegistry.getInstrumentation().targetContext
            ).name(databaseName).callback(callback).build()
        )
        legacyOpenHelpers += openHelper
        return openHelper.writableDatabase
    }

    private fun applyLegacyMigrations(
        db: SupportSQLiteDatabase,
        fromVersion: Int,
        toVersion: Int,
        migrations: Array<out Migration>
    ) {
        var version = fromVersion
        while (version < toVersion) {
            val migration = migrations.single {
                it.startVersion == version && it.endVersion == version + 1
            }
            migration.migrate(db)
            version = migration.endVersion
        }
        assertEquals("The complete legacy migration path must run", toVersion, version)
    }

    fun closeOpenHelpers() {
        legacyOpenHelpers.forEach { it.close() }
        legacyOpenHelpers.clear()
    }

    fun closeAll() {
        closeOpenHelpers()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
    }
}
