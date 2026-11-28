package org.example.stash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
@Composable
fun App(store: CaptureStore) {
    var text by remember { mutableStateOf("") }
    val draft = remember { CaptureDraft() }
    var query by remember { mutableStateOf("") }
    var refreshVersion by remember { mutableStateOf(0) }
    val generation = remember { InboxReadGeneration() }
    var entries by remember { mutableStateOf<List<Capture>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store, query, refreshVersion) {
        val request = generation.snapshot()
        loading = true
        loadFailed = false
        try {
            val result = store.items(query)
            generation.applyIfCurrent(request) { entries = result }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (generation.isCurrent(request)) loadFailed = true
        } finally {
            if (generation.isCurrent(request)) loading = false
        }
    }
    MaterialTheme {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
            Text("Inbox", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(value = text, onValueChange = { draft.edit(it); text = draft.text; saved = false }, label = { Text("Link or text") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = !saving, onClick = {
                val (capture, savedRevision) = try { draft.begin { input, kind ->
                    Capture(Uuid.random().toString(), input, kind, Clock.System.now().toEpochMilliseconds())
                } } catch (e: IllegalArgumentException) {
                    error = e.message
                    return@Button
                }
                saving = true
                saved = false
                error = null
                scope.launch {
                    try {
                        store.capture(capture)
                        saved = true
                        draft.committed(capture, savedRevision)
                        text = draft.text
                        generation.invalidate()
                        loading = true
                        refreshVersion++
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { error = "Could not save: ${e.message}" }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Saving…" else "Save to Inbox") }
            if (saved) Text("Saved locally")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(value = query, onValueChange = {
                if (it != query) {
                    query = it
                    generation.invalidate()
                    loading = true
                    loadFailed = false
                }
            }, label = { Text("Search Inbox") }, modifier = Modifier.fillMaxWidth())
            if (loading) Text("Loading Inbox…")
            else if (loadFailed) {
                Text("Could not load Inbox.")
                Button(onClick = { generation.invalidate(); loading = true; refreshVersion++ }) { Text("Retry") }
            }
            else if (entries.isEmpty()) Text(if (query.isEmpty()) "Your Inbox is empty." else "No matching items.")
            else if (entries.isNotEmpty()) LazyColumn { items(entries, key = { it.id }) { item -> Text(item.text, Modifier.fillMaxWidth().padding(vertical = 8.dp)) } }
        }
    }
}
