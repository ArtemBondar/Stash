package org.example.stash

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptureTest {
    @Test fun preservesInputAndClassifiesConservatively() {
        assertEquals(CaptureKind.LINK, classifyCapture("https://example.com/a"))
        assertEquals(CaptureKind.LINK, classifyCapture("https://stash.example/path"))
        assertEquals(CaptureKind.NOTE, classifyCapture("https://example.com/a b"))
        assertEquals(CaptureKind.NOTE, classifyCapture("look https://example.com"))
        val markdown = "# Heading\nA [link](https://example.com)\n"
        assertEquals(CaptureKind.NOTE, classifyCapture(markdown))
        val preserved = CaptureDraft().apply { edit(markdown) }.begin { text, kind -> Capture("id", text, kind, 1) }.first
        assertEquals(markdown, preserved.text)
        assertEquals(CaptureKind.NOTE, classifyCapture(" https://example.com "))
        assertFailsWith<IllegalArgumentException> { classifyCapture(" \n\t") }
    }
    @Test fun failedSaveRetriesSameIdAndEditsSurviveCommit() {
        val draft = CaptureDraft()
        var nextId = 0
        fun make(text: String, kind: CaptureKind) = Capture((++nextId).toString(), text, kind, 42)
        draft.edit("first")
        val (first, revision) = draft.begin(::make)
        assertEquals(first, draft.begin(::make).first)
        draft.edit("edited")
        draft.edit("first")
        draft.committed(first, revision)
        assertEquals("first", draft.text)
        val (second, secondRevision) = draft.begin(::make)
        assertEquals("2", second.id)
        draft.committed(second, secondRevision)
        assertEquals("", draft.text)
    }

    @Test fun newerInboxReadWinsWhenOlderReadCompletesLast() = runBlocking {
        val generation = InboxReadGeneration()
        val oldResult = CompletableDeferred<String>()
        val newResult = CompletableDeferred<String>()
        var displayed = ""
        val oldRequest = generation.snapshot()
        val oldRead = launch {
            val result = oldResult.await()
            generation.applyIfCurrent(oldRequest) { displayed = result }
        }
        generation.invalidate()
        val newRequest = generation.snapshot()
        val newRead = launch {
            val result = newResult.await()
            generation.applyIfCurrent(newRequest) { displayed = result }
        }
        newResult.complete("new")
        newRead.join()
        oldResult.complete("old")
        oldRead.join()
        assertEquals("new", displayed)
    }
}
