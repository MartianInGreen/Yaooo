package app.yaooo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yaooo.PlatformBackHandler
import app.yaooo.ReaderPrefs
import app.yaooo.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class ReaderState(
    val workId: Long,
    startPosition: Int,
    private val repo: Repository,
    private val scope: CoroutineScope,
) {
    var chapters by mutableStateOf<List<ChapterRef>>(emptyList())
    var position by mutableStateOf(startPosition)
    var blocks by mutableStateOf<List<Block>>(emptyList())
    var title by mutableStateOf("")
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var restoreIndex by mutableStateOf(0)
    var generation by mutableStateOf(0) // bumps whenever a new chapter body is ready

    init {
        repo.markRead(workId)
        scope.launch {
            try {
                chapters = repo.loadWork(workId).chapters
                if (chapters.isEmpty()) error("This work has no readable chapters.")
                open(position, restoreSaved = true)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Throwable) { error = e.message ?: "Could not load"; loading = false }
        }
    }

    fun open(pos: Int, restoreSaved: Boolean = false) {
        val ref = chapters.firstOrNull { it.position == pos } ?: chapters.firstOrNull() ?: return
        loading = true; error = null
        scope.launch {
            try {
                val c = repo.chapter(workId, ref.position)
                blocks = ContentParser.parse(c.html)
                title = c.title.ifBlank { ref.title }
                position = ref.position
                val saved = if (restoreSaved) repo.progress(workId)?.takeIf { it.position == ref.position }?.blockIndex else null
                restoreIndex = saved ?: 0
                generation++
                repo.saveProgress(workId, ref.position, restoreIndex, blocks.size)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Throwable) { error = e.message ?: "Could not load chapter" }
            finally { loading = false }
        }
    }

    fun next() { chapters.firstOrNull { it.position > position }?.let { open(it.position) } }
    fun prev() { chapters.lastOrNull { it.position < position }?.let { open(it.position) } }
    val hasNext get() = chapters.any { it.position > position }
    val hasPrev get() = chapters.any { it.position < position }

    fun saveScroll(index: Int) {
        if (!loading && blocks.isNotEmpty()) repo.saveProgress(workId, position, index, blocks.size)
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun ReaderScreen(state: ReaderState, prefs: ReaderPrefs, onPrefs: (ReaderPrefs) -> Unit, onBack: () -> Unit) {
    var chrome by remember { mutableStateOf(true) }
    var showPrefs by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    PlatformBackHandler(true, onBack)

    // Restore scroll when a chapter finishes loading; persist (debounced) while reading.
    LaunchedEffect(state.generation) {
        listState.scrollToItem(state.restoreIndex.coerceIn(0, (state.blocks.size - 1).coerceAtLeast(0)))
    }
    LaunchedEffect(state.generation) {
        snapshotFlow { listState.firstVisibleItemIndex }.debounce(300).collect { state.saveScroll(it) }
    }
    // Leaving the reader (back, app switch, process teardown of the screen) must not lose the last <300 ms of scrolling.
    DisposableEffect(state) {
        onDispose { state.saveScroll(listState.firstVisibleItemIndex) }
    }

    Scaffold(
        topBar = {
            if (chrome) TopAppBar(
                title = { Column { Text(state.title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                    Text("${state.position}/${state.chapters.size}", style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton({ showPrefs = true }) { Icon(Icons.Default.TextFormat, "Text settings") } },
            )
        },
        bottomBar = {
            if (chrome) BottomAppBar {
                IconButton(state::prev, enabled = state.hasPrev) { Icon(Icons.Default.ChevronLeft, "Previous chapter") }
                TextButton({ showChapters = true }, Modifier.weight(1f)) { Text("Chapters") }
                IconButton(state::next, enabled = state.hasNext) { Icon(Icons.Default.ChevronRight, "Next chapter") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                state.error != null && state.blocks.isEmpty() -> ErrorBox(state.error!!) { state.open(state.position) }
                state.loading && state.blocks.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Fetching the work from AO3…\nLong fics can take a minute.", Modifier.padding(top = 16.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { chrome = !chrome },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy((8 * prefs.fontScale).dp),
                ) {
                    itemsIndexed(state.blocks) { _, b -> BlockView(b, prefs) }
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                            if (state.hasNext) Button(state::next) { Text("Next chapter") }
                            else Text("End of the story so far.", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (state.loading && state.blocks.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (showPrefs) ModalBottomSheet({ showPrefs = false }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Text size", style = MaterialTheme.typography.labelLarge)
            Slider(prefs.fontScale, { onPrefs(prefs.copy(fontScale = it)) }, valueRange = 0.8f..1.8f)
            Text("Line spacing", style = MaterialTheme.typography.labelLarge)
            Slider(prefs.lineSpacing, { onPrefs(prefs.copy(lineSpacing = it)) }, valueRange = 1.2f..2.0f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Serif font", Modifier.weight(1f)); Switch(prefs.serif, { onPrefs(prefs.copy(serif = it)) })
            }
        }
    }
    if (showChapters) ModalBottomSheet({ showChapters = false }) {
        androidx.compose.foundation.lazy.LazyColumn {
            itemsIndexed(state.chapters) { _, ch ->
                ListItem(
                    headlineContent = { Text(ch.title.ifBlank { "Chapter ${ch.position}" }, fontWeight = if (ch.position == state.position) FontWeight.Bold else null) },
                    modifier = Modifier.clickable { showChapters = false; state.open(ch.position) },
                )
            }
        }
    }
}

@Composable
private fun BlockView(block: Block, prefs: ReaderPrefs) {
    val family = if (prefs.serif) FontFamily.Serif else FontFamily.SansSerif
    val size = (17 * prefs.fontScale).sp
    val lineHeight = size * prefs.lineSpacing
    val onSurface = MaterialTheme.colorScheme.onSurface
    when (block) {
        is Block.Paragraph -> Text(
            remember(block) { block.spans.toAnnotated() }, fontFamily = family, fontSize = size, lineHeight = lineHeight,
            textAlign = if (block.center) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth(),
        )
        is Block.Heading -> Text(
            remember(block) { block.spans.toAnnotated() }, fontFamily = family, fontWeight = FontWeight.Bold,
            fontSize = size * (if (block.level <= 2) 1.4f else 1.15f), modifier = Modifier.padding(top = 8.dp),
        )
        is Block.Quote -> Row(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(end = 10.dp).width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            Text(remember(block) { block.spans.toAnnotated() }, fontFamily = family, fontSize = size, lineHeight = lineHeight, fontStyle = FontStyle.Italic, color = onSurface.copy(alpha = 0.85f))
        }
        is Block.ListItem -> Row(Modifier.padding(start = 12.dp)) {
            Text(block.marker + " ", fontFamily = family, fontSize = size)
            Text(remember(block) { block.spans.toAnnotated() }, fontFamily = family, fontSize = size, lineHeight = lineHeight)
        }
        Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 12.dp))
    }
}

private fun List<Span>.toAnnotated(): AnnotatedString = buildAnnotatedString {
    for (s in this@toAnnotated) {
        val deco = buildList {
            if (s.underline) add(TextDecoration.Underline)
            if (s.strike) add(TextDecoration.LineThrough)
        }
        withStyle(
            SpanStyle(
                fontWeight = if (s.bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
            ),
        ) { append(s.text) }
    }
}
