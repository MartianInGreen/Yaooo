package app.yaooo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yaooo.data.LibraryWork
import app.yaooo.data.ReadProgress
import app.yaooo.data.Repository
import app.yaooo.data.WorkInfo
import app.yaooo.util.Ao3Url
import kotlinx.coroutines.launch

private enum class LibraryFilter(val label: String) { ALL("All"), UPDATES("New chapters"), DOWNLOADED("Downloaded"), WIP("In progress"), FINISHED("Finished") }

@Composable
fun LibraryScreen(
    works: List<LibraryWork>,
    progress: Map<Long, ReadProgress>,
    repo: Repository,
    onOpen: (WorkInfo) -> Unit,
) {
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var showAdd by remember { mutableStateOf(false) }
    val shown = when (filter) {
        LibraryFilter.ALL -> works
        LibraryFilter.UPDATES -> works.filter { it.unreadNew > 0 }
        LibraryFilter.DOWNLOADED -> works.filter { it.downloaded }
        LibraryFilter.WIP -> works.filter { !it.info.complete && !it.finished }
        LibraryFilter.FINISHED -> works.filter { it.finished }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp, 12.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("  Add from URL") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp, 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFilter.entries.forEach { f ->
                val count = if (f == LibraryFilter.UPDATES) works.count { it.unreadNew > 0 } else 0
                FilterChip(filter == f, { filter = f }, label = { Text(f.label + if (count > 0) " ($count)" else "", maxLines = 1, softWrap = false) })
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (works.isEmpty()) "Your library is empty.\nBookmark a fic, or add one from its URL." else "Nothing here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else LazyColumn {
            items(shown, key = { it.info.id }) { w ->
                WorkCard(w.info, w, onClick = { onOpen(w.info) }, progress = progress[w.info.id])
            }
        }
    }
    if (showAdd) AddFromUrlDialog(repo, onDismiss = { showAdd = false })
}

@Composable
private fun AddFromUrlDialog(repo: Repository, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val id = Ao3Url.workId(text)

    fun add(download: Boolean) {
        val workId = id ?: return
        busy = true; error = null
        scope.launch {
            try {
                val detail = repo.loadWork(workId)
                repo.setBookmarked(detail.info, true)
                if (download) repo.download(workId, detail.info)
                onDismiss()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.message ?: "Could not add this work"
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add from URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { text = it; error = null }, singleLine = true, enabled = !busy,
                    label = { Text("archiveofourown.org/works/…") }, modifier = Modifier.fillMaxWidth(),
                    isError = text.isNotBlank() && id == null,
                    supportingText = { if (text.isNotBlank() && id == null) Text("Not an AO3 work link") },
                )
                if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Fetching from AO3…", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { add(true) }, enabled = id != null && !busy) { Text("Add & download") }
                Button(onClick = { add(false) }, enabled = id != null && !busy) { Text("Add") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
