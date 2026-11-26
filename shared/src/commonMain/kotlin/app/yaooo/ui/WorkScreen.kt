package app.yaooo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yaooo.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WorkState(val id: Long, private val seed: WorkInfo?, private val repo: Repository, private val scope: CoroutineScope) {
    var detail by mutableStateOf<WorkDetail?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var downloading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    init { load() }

    fun load() {
        loading = true; error = null
        scope.launch {
            try { detail = repo.loadWork(id, seed) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not load work" }
            finally { loading = false }
        }
    }

    fun download() {
        downloading = true
        scope.launch {
            try {
                // Downloading implies keeping it: make sure the metadata row exists.
                detail?.info?.let { repo.setBookmarked(it, repo.libraryWork(id)?.bookmarked ?: false) }
                repo.download(id, seed)
                message = "Downloaded for offline reading"
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = "Download failed: ${e.message}" }
            finally { downloading = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkScreen(
    state: WorkState,
    repo: Repository,
    seed: WorkInfo?,
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    onTag: (TagType, String) -> Unit,
) {
    val lib by repo.observeWork(state.id).collectAsState(null)
    val info = state.detail?.info ?: lib?.info ?: seed
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); state.message = null } }
    val progressMap by repo.observeProgress().collectAsState(emptyMap())
    val progress = progressMap[state.id]

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(info?.title ?: "Work", maxLines = 1) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (info != null) {
                        val bookmarked = lib?.bookmarked == true
                        IconButton({ repo.setBookmarked(info, !bookmarked) }) {
                            Icon(if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Bookmark")
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (info == null) {
            Column(Modifier.padding(pad).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.loading) CircularProgressIndicator(Modifier.padding(32.dp))
                state.error?.let { ErrorBox(it, state::load) }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(info.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("by " + info.authors.joinToString(), color = MaterialTheme.colorScheme.primary)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RatingPill(info.rating)
                    Text(statsLine(info), style = MaterialTheme.typography.labelMedium)
                }

                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRead(progress?.position ?: 1) }) {
                        Icon(Icons.Default.MenuBook, null); Text(if (progress != null) "  Continue ch. ${progress.position}" else "  Read")
                    }
                    if (lib?.downloaded == true) {
                        OutlinedButton(onClick = { repo.removeDownload(state.id) }) { Icon(Icons.Default.Delete, null); Text("  Remove download") }
                    } else {
                        OutlinedButton(onClick = state::download, enabled = !state.downloading) {
                            if (state.downloading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Download, null)
                            Text("  Download")
                        }
                    }
                }
                if (lib?.bookmarked == true && !info.complete) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (lib?.notify == true) Icons.Default.Notifications else Icons.Default.NotificationsOff, null)
                        Text("  Notify me about new chapters", Modifier.weight(1f))
                        Switch(lib?.notify == true, { repo.setNotify(state.id, it) })
                    }
                }

                if (info.summary.isNotBlank()) { SectionLabel("Summary"); Text(info.summary) }
                SectionLabel("Fandoms"); TagFlow(info.fandoms) { onTag(TagType.FANDOM, it) }
                if (info.relationships.isNotEmpty()) { SectionLabel("Relationships"); TagFlow(info.relationships) { onTag(TagType.RELATIONSHIP, it) } }
                if (info.characters.isNotEmpty()) { SectionLabel("Characters"); TagFlow(info.characters) { onTag(TagType.CHARACTER, it) } }
                if (info.freeforms.isNotEmpty()) { SectionLabel("Tags"); TagFlow(info.freeforms) { onTag(TagType.FREEFORM, it) } }
                if (info.warnings.isNotEmpty()) { SectionLabel("Warnings"); TagFlow(info.warnings) }
                SectionLabel("Chapters")
                if (state.loading && state.detail == null && lib?.downloaded != true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Fetching the work from AO3 — long fics can take a minute…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.error?.let { ErrorBox("Couldn't load chapters: $it", state::load) }
            }
            val chapters = state.detail?.chapters ?: repo.offlineChapters(state.id)
            items(chapters, key = { it.position }) { ch ->
                val isNew = lib != null && ch.position > (lib?.knownChapters ?: 0) - (lib?.unreadNew ?: 0) && (lib?.unreadNew ?: 0) > 0
                ListItem(
                    headlineContent = { Text(ch.title.ifBlank { "Chapter ${ch.position}" }) },
                    supportingContent = if (ch.date.isNotBlank()) ({ Text(ch.date) }) else null,
                    trailingContent = if (isNew) ({ Badge { Text("new") } }) else null,
                    modifier = Modifier.clickable { onRead(ch.position) },
                )
            }
        }
    }
}
