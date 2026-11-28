package org.example.stash

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CapturePersistenceIosTest {
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    @Test fun persistsAndRetriesAfterReopen() = runBlocking {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".db"
        fun open() = Room.databaseBuilder<StashDatabase>(path).setDriver(BundledSQLiteDriver()).build()
        val item = Capture("id", "a link https://example.org", CaptureKind.NOTE, 1)
        val first = open()
        RoomCaptureStore(first).capture(item)
        first.close()
        val reopened = open()
        try {
            RoomCaptureStore(reopened).capture(item)
            assertFailsWith<CaptureConflict> { RoomCaptureStore(reopened).capture(item.copy(text = "different")) }
            assertEquals(listOf(item), RoomCaptureStore(reopened).items())
            assertEquals(1, reopened.dao().outboxCount())
        } finally {
            reopened.close()
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }
}
