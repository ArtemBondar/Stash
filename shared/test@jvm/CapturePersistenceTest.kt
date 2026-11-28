package org.example.stash

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CapturePersistenceTest {
    @Test fun transactionSurvivesReopenAndRetriesAreIdempotent() = runBlocking {
        val directory = Files.createTempDirectory("stash-capture")
        val path = directory.resolve("stash.db").toString()
        fun open() = Room.databaseBuilder<StashDatabase>(path).setDriver(BundledSQLiteDriver()).build()
        val input = Capture("id", "original https://example.com", CaptureKind.NOTE, 1)
        val first = open()
        RoomCaptureStore(first).capture(input)
        RoomCaptureStore(first).capture(input)
        assertFailsWith<CaptureConflict> { RoomCaptureStore(first).capture(input.copy(text = "different")) }
        RoomCaptureStore(first).capture(input.copy(id = "second"))
        first.close()
        val reopened = open()
        try {
            assertEquals(setOf(input, input.copy(id = "second")), RoomCaptureStore(reopened).items().toSet())
            assertEquals(2, reopened.dao().outboxCount())
        } finally {
            reopened.close()
            directory.toFile().deleteRecursively()
        }
    }
    @Test fun searchUsesLiteralUnicodeSubstringAndLocalPartitionAfterReopen() = runBlocking {
        val directory = Files.createTempDirectory("stash-search")
        val path = directory.resolve("stash.db").toString()
        fun open() = Room.databaseBuilder<StashDatabase>(path).setDriver(BundledSQLiteDriver()).build()
        val items = listOf(
            Capture("1", "Café 100%_done https://example.org/Path", CaptureKind.NOTE, 1),
            Capture("2", "other", CaptureKind.NOTE, 2),
            Capture("3", "Café hidden", CaptureKind.NOTE, 3, "another")
        )
        fun check(store: CaptureStore) = runBlocking {
            assertEquals(listOf(items[1], items[0]), store.items(""))
            for (query in listOf("CAFÉ", "%_", "example.org/path")) assertEquals(listOf(items[0]), store.items(query))
            assertEquals(emptyList(), store.items("%missing_"))
        }
        val first = open()
        items.forEach { RoomCaptureStore(first).capture(it) }
        check(RoomCaptureStore(first))
        first.close()
        val reopened = open()
        try { check(RoomCaptureStore(reopened)) } finally { reopened.close(); directory.toFile().deleteRecursively() }
    }
    @Test fun failedOutboxInsertRollsBackItem() = runBlocking {
        val directory = Files.createTempDirectory("stash-rollback")
        val path = directory.resolve("stash.db").toString()
        fun open() = Room.databaseBuilder<StashDatabase>(path).setDriver(BundledSQLiteDriver()).build()
        val initial = open()
        initial.dao().rows()
        initial.close()
        val connection = BundledSQLiteDriver().open(path)
        connection.execSQL("CREATE TRIGGER reject_outbox BEFORE INSERT ON OutboxEntry BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        connection.close()
        val database = open()
        try {
            assertFailsWith<Exception> { RoomCaptureStore(database).capture(Capture("failed", "text", CaptureKind.NOTE, 1)) }
            assertNull(database.dao().find("failed"))
            assertEquals(0, database.dao().outboxCount())
        } finally {
            database.close()
            directory.toFile().deleteRecursively()
        }
    }

}
