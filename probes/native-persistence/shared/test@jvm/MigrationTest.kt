package org.example.stashprobe

import androidx.room3.Room
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class MigrationTest {
    @Test
    fun preservesV1Data() = runBlocking {
        val dir = Files.createTempDirectory("stash-migrate")
        val path = dir.resolve("probe.db").toString()
        val conn = BundledSQLiteDriver().open(path)
        conn.execSQL("CREATE TABLE IF NOT EXISTS `ProbeItem` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`id`))")
        conn.execSQL("CREATE TABLE IF NOT EXISTS `ProbeOutbox` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, PRIMARY KEY(`id`))")
        conn.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        conn.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '29d12fc0d0a74d6350a964020896072a')")
        conn.execSQL("INSERT INTO ProbeItem VALUES ('old', 'original')")
        conn.execSQL("INSERT INTO ProbeOutbox VALUES ('old', 'old')")
        conn.execSQL("PRAGMA user_version = 1")
        conn.close()
        val migration = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE ProbeItem ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }
        fun open() = Room.databaseBuilder<ProbeDb>(path).setDriver(BundledSQLiteDriver()).addMigrations(migration).build()
        val db = open()
        try {
            assertEquals(ProbeItem("old", "original"), db.dao().find("old"))
            assertEquals(ProbeOutbox("old", "old"), db.dao().findOutbox("old"))
        } finally {
            db.close()
        }
        val reopened = open()
        try {
            assertEquals(ProbeItem("old", "original"), reopened.dao().find("old"))
            assertEquals(ProbeOutbox("old", "old"), reopened.dao().findOutbox("old"))
        } finally {
            reopened.close()
            dir.toFile().deleteRecursively()
        }
    }
}
