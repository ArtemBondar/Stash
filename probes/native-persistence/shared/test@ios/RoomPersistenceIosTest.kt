package org.example.stashprobe

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSFileManager
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomPersistenceIosTest {
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    @Test
    fun literalSearchSurvivesReopen() = runBlocking {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".db"
        fun open() = Room.databaseBuilder<ProbeDb>(path).setDriver(BundledSQLiteDriver()).build()
        val first = open()
        try {
            insertSearchFixtures(first.dao())
            assertLiteralSearch(first.dao())
        } finally {
            first.close()
        }
        val second = open()
        try {
            assertLiteralSearch(second.dao())
        } finally {
            second.close()
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }
    @Test
    fun persistsAcrossReopen() = runBlocking {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".db"
        fun open() = Room.databaseBuilder<ProbeDb>(path).setDriver(BundledSQLiteDriver()).build()
        val first = open()
        first.dao().insert(ProbeItem("1", "saved offline"))
        first.close()
        val second = open()
        try {
            assertEquals(ProbeItem("1", "saved offline"), second.dao().find("1"))
        } finally {
            second.close()
        }
    }
}
