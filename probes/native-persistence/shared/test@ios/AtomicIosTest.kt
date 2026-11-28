package org.example.stashprobe

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class AtomicIosTest {
    @Test
    fun commitRollbackReopen() = runBlocking {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".db"
        fun open() = Room.databaseBuilder<ProbeDb>(path).setDriver(BundledSQLiteDriver()).build()
        val db = open()
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
        val reopened = open()
        try {
            assertEquals(ProbeItem("ok", "mixed https://example.org"), reopened.dao().find("ok"))
            assertEquals(ProbeOutbox("ok", "ok"), reopened.dao().findOutbox("ok"))
            assertNull(reopened.dao().find("fail"))
            assertNull(reopened.dao().findOutbox("fail"))
        } finally {
            reopened.close()
        }
    }
}
