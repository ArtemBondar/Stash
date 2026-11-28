package org.example.stashprobe

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomPersistenceTest {
    @Test
    fun literalSearchSurvivesReopen() = runBlocking {
        val directory = Files.createTempDirectory("stash-search-probe")
        val path = directory.resolve("probe.db")
        fun open() = Room.databaseBuilder<ProbeDb>(path.toString()).setDriver(BundledSQLiteDriver()).build()
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
            directory.toFile().deleteRecursively()
        }
    }
    @Test
    fun persistsAcrossReopen() = runBlocking {
        val directory = Files.createTempDirectory("stash-room-probe")
        val path = directory.resolve("probe.db")
        fun open() = Room.databaseBuilder<ProbeDb>(path.toString()).setDriver(BundledSQLiteDriver()).build()
        val first = open()
        first.dao().insert(ProbeItem("1", "saved offline"))
        first.close()
        val second = open()
        try {
            assertEquals(ProbeItem("1", "saved offline"), second.dao().find("1"))
        } finally {
            second.close()
            directory.toFile().deleteRecursively()
        }
    }
}
