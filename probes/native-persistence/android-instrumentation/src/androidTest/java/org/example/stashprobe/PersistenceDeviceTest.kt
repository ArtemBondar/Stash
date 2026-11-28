package org.example.stashprobe

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room3.Room
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class PersistenceDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun withDatabase(test: suspend (String) -> Unit) = runBlocking {
        val directory = context.cacheDir.resolve("persistence-test").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            test(directory.resolve("probe.db").absolutePath)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun open(path: String, migration: Migration? = null): ProbeDb {
        val builder = Room.databaseBuilder<ProbeDb>(context, path).setDriver(BundledSQLiteDriver())
        if (migration != null) builder.addMigrations(migration)
        return builder.build()
    }

    @Test
    fun testItemReopen() = withDatabase { path ->
        open(path).also {
            it.dao().insert(ProbeItem("1", "saved offline"))
            it.close()
        }
        open(path).also {
            try {
                assertEquals(ProbeItem("1", "saved offline"), it.dao().find("1"))
            } finally {
                it.close()
            }
        }
    }

    @Test
    fun testAtomicReopen() = withDatabase { path ->
        val db = open(path)
        db.dao().capture("ok", "mixed https://example.org")
        try {
            db.dao().capture("fail", "retry", true)
            fail("expected injected failure")
        } catch (e: IllegalStateException) {
            assertEquals("injected", e.message)
        }
        assertNull(db.dao().find("fail"))
        assertNull(db.dao().findOutbox("fail"))
        db.close()
        open(path).also {
            try {
                assertEquals(ProbeItem("ok", "mixed https://example.org"), it.dao().find("ok"))
                assertEquals(ProbeOutbox("ok", "ok"), it.dao().findOutbox("ok"))
                assertNull(it.dao().find("fail"))
                assertNull(it.dao().findOutbox("fail"))
            } finally {
                it.close()
            }
        }
    }

    @Test
    fun testMigrationReopen() = withDatabase { path ->
        BundledSQLiteDriver().open(path).also {
            it.execSQL("CREATE TABLE IF NOT EXISTS `ProbeItem` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`id`))")
            it.execSQL("CREATE TABLE IF NOT EXISTS `ProbeOutbox` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, PRIMARY KEY(`id`))")
            it.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            it.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '29d12fc0d0a74d6350a964020896072a')")
            it.execSQL("INSERT INTO ProbeItem VALUES ('old', 'original')")
            it.execSQL("INSERT INTO ProbeOutbox VALUES ('old', 'old')")
            it.execSQL("PRAGMA user_version = 1")
            it.close()
        }
        val migration = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE ProbeItem ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }
        repeat(2) {
            open(path, migration).also {
                try {
                    assertEquals(ProbeItem("old", "original"), it.dao().find("old"))
                    assertEquals(ProbeOutbox("old", "old"), it.dao().findOutbox("old"))
                } finally {
                    it.close()
                }
            }
        }
    }

    @Test
    fun testLiteralSearchReopen() = withDatabase { path ->
        open(path).also {
            try {
                insertSearchFixtures(it.dao())
                assertLiteralSearch(it.dao())
            } finally {
                it.close()
            }
        }
        open(path).also {
            try {
                assertLiteralSearch(it.dao())
            } finally {
                it.close()
            }
        }
    }
}
