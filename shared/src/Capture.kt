package org.example.stash

const val LOCAL_PROFILE = "local"

enum class CaptureKind { LINK, NOTE }

data class Capture(val id: String, val text: String, val kind: CaptureKind, val createdAt: Long, val profile: String = LOCAL_PROFILE)

class CaptureConflict : IllegalStateException("This capture ID already belongs to different content. Copy your text and try again.")

fun classifyCapture(text: String): CaptureKind {
    require(text.isNotBlank()) { "Enter some text to save." }
    val standaloneUrl = Regex("(?i)^https?://[^\\s/]+(?:/[^\\s]*)?$")
    return if (standaloneUrl.matches(text)) CaptureKind.LINK else CaptureKind.NOTE
}

interface CaptureStore {
    suspend fun items(query: String = ""): List<Capture>
    suspend fun capture(item: Capture)
}

// Remembers an uncertain write so a retry uses the original ID and timestamp.
class CaptureDraft {
    var text: String = ""
        private set
    private var revision = 0L
    private var pending: Capture? = null

    fun edit(value: String) { text = value; revision++ }
    fun begin(newCapture: (String, CaptureKind) -> Capture): Pair<Capture, Long> {
        val kind = classifyCapture(text)
        val capture = pending?.takeIf { it.text == text } ?: newCapture(text, kind)
        pending = capture
        return capture to revision
    }
    fun committed(capture: Capture, savedRevision: Long) {
        if (pending == capture) pending = null
        if (revision == savedRevision) text = ""
    }
}

// Invalidates in-flight reads as soon as the user changes the query or a save commits.
class InboxReadGeneration {
    private var current = 0
    fun invalidate() { current++ }
    fun snapshot(): Int = current
    fun isCurrent(snapshot: Int): Boolean = snapshot == current
    fun applyIfCurrent(snapshot: Int, update: () -> Unit) { if (isCurrent(snapshot)) update() }
}
