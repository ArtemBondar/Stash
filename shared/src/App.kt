package org.example.stash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val WideInboxWidth = 720.dp
private val ContentPadding = 16.dp
private val ElementSpacing = 8.dp
private val MinimumTouchTarget = 48.dp
private val RowShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
@Composable
fun App(store: CaptureStore) {
    var text by remember { mutableStateOf("") }
    val draft = remember { CaptureDraft() }
    var query by remember { mutableStateOf("") }
    var refreshVersion by remember { mutableStateOf(0) }
    val generation = remember { InboxReadGeneration() }
    var entries by remember { mutableStateOf<List<Capture>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
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
            generation.applyIfCurrent(request) {
                entries = result
                if (selectedId != null && result.none { it.id == selectedId }) selectedId = null
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (generation.isCurrent(request)) loadFailed = true
        } finally {
            if (generation.isCurrent(request)) loading = false
        }
    }
    val save: () -> Unit = save@{
        if (saving) return@save
        val (capture, savedRevision) = try { draft.begin { input, kind ->
            Capture(Uuid.random().toString(), input, kind, Clock.System.now().toEpochMilliseconds())
        } } catch (e: IllegalArgumentException) {
            error = e.message
            return@save
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
    }
    val edit: (String) -> Unit = { draft.edit(it); text = draft.text; saved = false }
    val search: (String) -> Unit = {
        if (it != query) {
            query = it
            generation.invalidate()
            loading = true
            loadFailed = false
        }
    }
    val retry: () -> Unit = {
        generation.invalidate()
        loading = true
        refreshVersion++
    }
    StashTheme {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                if (maxWidth >= WideInboxWidth) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(ContentPadding), verticalArrangement = Arrangement.spacedBy(ElementSpacing)) {
                            CaptureEditor(text, edit, saving, saved, error, save)
                        }
                        LazyColumn(Modifier.weight(1.15f).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(ContentPadding), verticalArrangement = Arrangement.spacedBy(ElementSpacing)) {
                            inboxContent(query, search, entries, loading, loadFailed, retry, selectedId) { selectedId = it }
                        }
                        Column(Modifier.weight(1.15f).fillMaxSize().padding(ContentPadding), verticalArrangement = Arrangement.spacedBy(ElementSpacing)) {
                            Text("Item details", style = MaterialTheme.typography.titleLarge)
                            val selected = if (!loading && !loadFailed) entries.find { it.id == selectedId } else null
                            if (selected != null) {
                                key(selected.id) {
                                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                        SelectionContainer {
                                            Text(selected.text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            } else if (!loading && !loadFailed) {
                                Text("Select an item to view its details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(ContentPadding), verticalArrangement = Arrangement.spacedBy(ElementSpacing)) {
                        item { Column(verticalArrangement = Arrangement.spacedBy(ElementSpacing)) { CaptureEditor(text, edit, saving, saved, error, save) } }
                        inboxContent(query, search, entries, loading, loadFailed, retry)
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureEditor(
    text: String, onTextChange: (String) -> Unit, saving: Boolean, saved: Boolean,
    error: String?, onSave: () -> Unit,
) {
    Text("Quick capture", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text("Link or text") },
        maxLines = 5,
        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
            if (it.key == Key.Enter && (it.isCtrlPressed || it.isMetaPressed)) {
                if (it.type == KeyEventType.KeyUp) onSave()
                true
            } else false
        },
    )
    Text("Use Ctrl+Enter or Command+Enter to save", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(enabled = !saving, onClick = onSave, modifier = Modifier.heightIn(min = MinimumTouchTarget)) { Text(if (saving) "Saving…" else "Save to Inbox") }
    if (saved) Text("Saved locally")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

private fun LazyListScope.inboxContent(
    query: String, onQueryChange: (String) -> Unit, entries: List<Capture>,
    loading: Boolean, loadFailed: Boolean, onRetry: () -> Unit,
    selectedId: String? = null, onSelect: ((String) -> Unit)? = null,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(ElementSpacing)) {
            Text("Inbox", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = query, onValueChange = onQueryChange, label = { Text("Search Inbox") }, modifier = Modifier.fillMaxWidth())
        }
    }
    when {
        loading -> item { Text("Loading Inbox…") }
        loadFailed -> item {
            Column(verticalArrangement = Arrangement.spacedBy(ElementSpacing)) {
                Text("Could not load Inbox.")
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = MinimumTouchTarget)) { Text("Retry") }
            }
        }
        entries.isEmpty() -> item { Text(if (query.isEmpty()) "Your Inbox is empty." else "No matching items.") }
        else -> items(entries, key = { it.id }) { item ->
            if (onSelect == null) {
                Text(item.text, Modifier.fillMaxWidth().padding(vertical = ElementSpacing), style = MaterialTheme.typography.bodyLarge)
            } else {
                val selected = item.id == selectedId
                val colors = MaterialTheme.colorScheme
                Text(
                    text = item.text,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinimumTouchTarget)
                        .clip(RowShape)
                        .background(if (selected) colors.secondaryContainer else Color.Transparent)
                        .selectable(selected = selected, role = Role.Button, onClick = { onSelect(item.id) })
                        .padding(horizontal = ElementSpacing, vertical = ElementSpacing),
                    color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
