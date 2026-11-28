package org.example.stash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal class QuickCaptureSession(
    private val store: CaptureStore,
    private val newCapture: (String, CaptureKind) -> Capture,
) {
    private val draft = CaptureDraft()
    var text by mutableStateOf("")
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val canClose get() = !saving

    fun edit(value: String) {
        if (saving) return
        draft.edit(value)
        text = value
        error = null
    }

    suspend fun save(): Boolean {
        if (saving) return false
        val (capture, revision) = try {
            draft.begin(newCapture)
        } catch (e: IllegalArgumentException) {
            error = e.message
            return false
        }
        saving = true
        error = null
        return try {
            store.capture(capture)
            draft.committed(capture, revision)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Could not save: ${e.message}"
            false
        } finally {
            saving = false
        }
    }
}

@Composable
internal fun QuickCapture(session: QuickCaptureSession, close: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(session.text)) }
    val focus = remember { FocusRequester() }
    var enterPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val save = { scope.launch(start = CoroutineStart.UNDISPATCHED) { if (session.save()) close() }; Unit }
    LaunchedEffect(session.saving) { if (!session.saving) focus.requestFocus() }
    StashTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp).onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Escape && value.composition == null -> {
                        if (event.type == KeyEventType.KeyDown && session.canClose) close()
                        true
                    }
                    event.key == Key.Enter && event.type == KeyEventType.KeyUp && enterPressed -> {
                        enterPressed = false
                        true
                    }
                    event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed && value.composition == null -> {
                        if (!enterPressed) { enterPressed = true; save() }
                        true
                    }
                    else -> false
                }
            }) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; session.edit(it.text) },
                    label = { Text("Link or text") },
                    readOnly = session.saving,
                    modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focus),
                )
                session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = save, enabled = !session.saving) { Text(if (session.saving) "Saving…" else "Save") }
            }
        }
    }
}
