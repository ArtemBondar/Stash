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
        val item = Capture("id", "Café 100%_ https://example.org/Path", CaptureKind.NOTE, 1)
        val first = open()
        RoomCaptureStore(first).capture(item)
        RoomCaptureStore(first).capture(item.copy(id = "hidden", profile = "other"))
        assertEquals(listOf(item), RoomCaptureStore(first).items("CAFÉ"))
        first.close()
        val reopened = open()
        try {
            RoomCaptureStore(reopened).capture(item)
            assertFailsWith<CaptureConflict> { RoomCaptureStore(reopened).capture(item.copy(text = "different")) }
            assertEquals(listOf(item), RoomCaptureStore(reopened).items())
            for (query in listOf("CAFÉ", "%_", "example.org/path", "")) {
                assertEquals(listOf(item), RoomCaptureStore(reopened).items(query))
            }
            assertEquals(emptyList(), RoomCaptureStore(reopened).items("%missing_"))
            assertEquals(2, reopened.dao().outboxCount())
        } finally {
            reopened.close()
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }
}
