package app.yaooo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.yaooo.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Survives navigation (lives in [App]) so returning from a work keeps results and scroll position. */
class SearchState(private val repo: Repository, private val scope: CoroutineScope) {
    var query by mutableStateOf(SearchQuery())
    var results by mutableStateOf<List<WorkInfo>>(emptyList())
    var total by mutableStateOf<Long?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasSearched by mutableStateOf(false)
    val listState = LazyListState()

    private var page = 0
    private var totalPages = 1
    private var job: Job? = null

    // tag entry
    var tagType by mutableStateOf(TagType.FANDOM)
    var tagInput by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList())
    private var suggestJob: Job? = null

    fun submit() {
        job?.cancel()
        results = emptyList(); total = null; page = 0; totalPages = 1; error = null; hasSearched = true
        loadMore()
    }

    fun loadMore() {
        if (loading || page >= totalPages) return
        loading = true
        val snapshot = query
        job = scope.launch {
            try {
                val res = repo.search(snapshot, page + 1)
                page++
                totalPages = res.totalPages
                total = res.totalFound ?: total
                results = results + res.works.filter { n -> results.none { it.id == n.id } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Search failed"
            } finally {
                loading = false
            }
        }
    }

    fun onTagInput(text: String) {
        tagInput = text
        suggestJob?.cancel()
        if (text.length < 2) { suggestions = emptyList(); return }
        suggestJob = scope.launch {
            delay(300)
            suggestions = runCatching { repo.autocomplete(tagType, text) }.getOrDefault(emptyList()).take(8)
        }
    }

    fun addTag(name: String, exclude: Boolean = false, type: TagType = tagType) {
        val t = TagFilter(type, name.trim(), exclude)
        if (t.name.isNotEmpty() && t !in query.tags) query = query.copy(tags = query.tags + t)
        tagInput = ""; suggestions = emptyList()
    }

    fun removeTag(t: TagFilter) { query = query.copy(tags = query.tags - t) }

    fun searchForTag(type: TagType, name: String) {
        query = SearchQuery(tags = listOf(TagFilter(type, name)), sort = SortColumn.UPDATED)
        submit()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(state: SearchState, library: Map<Long, LibraryWork>, onOpen: (WorkInfo) -> Unit) {
    var showFilters by remember { mutableStateOf(false) }
    val q = state.query

    Column(Modifier.fillMaxSize()) {
        // Search bar + filter button
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = q.text,
                onValueChange = { state.query = q.copy(text = it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search AO3…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.submit() }),
            )
            BadgedBox(badge = { if (q.activeFilterCount > 0) Badge { Text("${q.activeFilterCount}") } }) {
                IconButton(onClick = { showFilters = true }) { Icon(Icons.Default.FilterList, "Filters") }
            }
        }

        // Tag entry
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { menu = true }, label = { Text(state.tagType.label) })
                    DropdownMenu(menu, { menu = false }) {
                        TagType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = { state.tagType = t; state.onTagInput(state.tagInput); menu = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = state.tagInput,
                    onValueChange = state::onTagInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add tag…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { state.addTag(state.tagInput) }),
                )
            }
            if (state.suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.suggestions.forEach { s ->
                        SuggestionChip(onClick = { state.addTag(s) }, label = { Text(s) })
                        SuggestionChip(onClick = { state.addTag(s, exclude = true) }, label = { Text("−") })
                    }
                }
            }
            if (q.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    q.tags.forEach { t ->
                        InputChip(
                            selected = !t.exclude,
                            onClick = { state.removeTag(t) },
                            label = { Text((if (t.exclude) "NOT " else "") + t.name) },
                            trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }

        Button(onClick = { state.submit() }, Modifier.padding(12.dp, 4.dp).fillMaxWidth()) { Text("Search") }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.hasSearched -> Text(
                    "Search by keyword, add fandom/character/relationship/tags, or open filters.\n" +
                        "Tap − next to a suggestion to exclude a tag.",
                    Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.results.isEmpty() && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.results.isEmpty() && state.error != null -> ErrorBox(state.error!!, state::submit)
                state.results.isEmpty() -> Text("No works found.", Modifier.padding(24.dp))
                else -> LazyColumn(state = state.listState) {
                    state.total?.let { item { Text("$it works", Modifier.padding(16.dp, 4.dp), style = MaterialTheme.typography.labelMedium) } }
                    items(state.results, key = { it.id }) { w -> WorkCard(w, library[w.id], onClick = { onOpen(w) }) }
                    item {
                        when {
                            state.loading -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                            state.error != null -> ErrorBox(state.error!!, state::loadMore)
                            else -> LaunchedEffect(state.results.size) { state.loadMore() }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FilterSheet(q, onChange = { state.query = it }, onApply = { showFilters = false; state.submit() })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(q: SearchQuery, onChange: (SearchQuery) -> Unit, onApply: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(SearchQuery(text = q.text, tags = q.tags)) }) { Text("Reset") }
        }
        OutlinedTextField(q.title, { onChange(q.copy(title = it)) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(q.author, { onChange(q.copy(author = it)) }, label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionLabel("Rating")
        MultiChips(Rating.entries, q.ratings, { it.label }) { onChange(q.copy(ratings = it)) }
        SectionLabel("Archive warnings")
        MultiChips(Warning.entries, q.warnings, { it.label }) { onChange(q.copy(warnings = it)) }
        SectionLabel("Category")
        MultiChips(Category.entries, q.categories, { it.label }) { onChange(q.copy(categories = it)) }

        SectionLabel("Status")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Completion.entries.forEach { c ->
                FilterChip(q.completion == c, { onChange(q.copy(completion = c)) }, label = { Text(c.label) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(q.excludeCrossovers, { onChange(q.copy(excludeCrossovers = !q.excludeCrossovers)) }, label = { Text("No crossovers") })
            FilterChip(q.singleChapterOnly, { onChange(q.copy(singleChapterOnly = !q.singleChapterOnly)) }, label = { Text("One-shots only") })
        }

        SectionLabel("Numbers")
        OutlinedTextField(q.wordCount, { onChange(q.copy(wordCount = it)) }, label = { Text("Word count (e.g. 10000-50000, >100000)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(q.minKudos, { onChange(q.copy(minKudos = it)) }, label = { Text("Min kudos") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(q.updatedWithin, { onChange(q.copy(updatedWithin = it)) }, label = { Text("Updated (e.g. <1 week, >1 year)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(q.language, { onChange(q.copy(language = it)) }, label = { Text("Language code (e.g. en)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionLabel("Sort by")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortColumn.entries.forEach { s -> FilterChip(q.sort == s, { onChange(q.copy(sort = s)) }, label = { Text(s.label) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Descending", Modifier.weight(1f))
            Switch(q.sortDescending, { onChange(q.copy(sortDescending = it)) })
        }
        Button(onApply, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Apply & search") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiChips(all: List<T>, selected: Set<T>, label: (T) -> String, onChange: (Set<T>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        all.forEach { item ->
            FilterChip(item in selected, { onChange(if (item in selected) selected - item else selected + item) }, label = { Text(label(item)) })
        }
    }
}
