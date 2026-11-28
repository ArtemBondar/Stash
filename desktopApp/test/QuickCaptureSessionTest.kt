package org.example.stash

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickCaptureSessionTest {
    @Test fun writeFailureWithoutMessageOffersRetryAndRetainsDraft() = runBlocking {
        val store = object : CaptureStore {
            override suspend fun items(query: String) = emptyList<Capture>()
            override suspend fun capture(item: Capture) { throw Exception() }
        }
        val session = QuickCaptureSession(store) { text, kind -> Capture("id", text, kind, 42) }
        session.edit("hello")

        assertFalse(session.save())
        assertEquals("hello", session.text)
        assertEquals("Could not save. Please try again.", session.error)
    }

    @Test fun failedWriteRetainsInputAndRetryUsesSameCapture() = runBlocking {
        val writes = mutableListOf<Capture>()
        val release = CompletableDeferred<Unit>()
        val store = object : CaptureStore {
            override suspend fun items(query: String) = emptyList<Capture>()
            override suspend fun capture(item: Capture) {
                writes += item
                if (writes.size == 1) error("write failed")
                release.await()
            }
        }
        var issuedIds = 0
        val session = QuickCaptureSession(store) { text, kind -> Capture("id-${++issuedIds}", text, kind, 42) }
        assertFalse(session.save())
        assertTrue(writes.isEmpty())
        session.edit("hello")
        assertFalse(session.save())
        assertEquals("hello", session.text)
        assertTrue(session.error != null)
        val retry = launch(start = CoroutineStart.UNDISPATCHED) { assertTrue(session.save()) }
        assertTrue(session.saving)
        assertFalse(session.canClose)
        assertFalse(session.save())
        assertEquals(2, writes.size)
        release.complete(Unit)
        retry.join()
        assertEquals(writes[0], writes[1])
        assertTrue(session.canClose)
    }
}
