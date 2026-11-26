package app.yaooo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.yaooo.AppContainer
import app.yaooo.PlatformBackHandler
import app.yaooo.ThemeMode
import app.yaooo.data.TagType
import app.yaooo.data.WorkInfo

private sealed interface Route {
    data class Work(val id: Long, val seed: WorkInfo?) : Route
    data class Reader(val id: Long, val position: Int) : Route
}

private enum class Tab(val label: String, val icon: ImageVector) {
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Library", Icons.Default.LocalLibrary),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun YaoooTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFFF8A80), onPrimary = Color(0xFF3B0000), primaryContainer = Color(0xFF5C1A1A),
        background = Color(0xFF121212), surface = Color(0xFF121212),
    ) else lightColorScheme(
        primary = Color(0xFF990000), primaryContainer = Color(0xFFFFDAD6),
        background = Color(0xFFFFFBFF), surface = Color(0xFFFFFBFF),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** @param openWorkId set when launched from a notification to jump straight to that fic. */
@Composable
fun App(container: AppContainer, openWorkId: Long? = null) {
    val settings by container.settings.collectAsState()
    YaoooTheme(settings.theme) {
        Surface {
            val repo = container.repository
            val library by repo.observeLibrary().collectAsState(emptyList())
            val progress by repo.observeProgress().collectAsState(emptyMap())
            val libraryById = remember(library) { library.associateBy { it.info.id } }
            val search = remember { SearchState(repo, container.scope) }
            var tab by remember { mutableStateOf(Tab.SEARCH) }
            val stack = remember { mutableStateListOf<Route>() }

            LaunchedEffect(openWorkId) { if (openWorkId != null) { stack.clear(); stack += Route.Work(openWorkId, null) } }
            PlatformBackHandler(stack.isNotEmpty()) { stack.removeLast() }

            when (val route = stack.lastOrNull()) {
                is Route.Work -> {
                    val ws = remember(route.id) { WorkState(route.id, route.seed, repo, container.scope) }
                    WorkScreen(
                        state = ws, repo = repo, seed = route.seed,
                        onBack = { stack.removeLast() },
                        onRead = { pos -> stack += Route.Reader(route.id, pos) },
                        onTag = { type: TagType, name -> stack.clear(); tab = Tab.SEARCH; search.searchForTag(type, name) },
                    )
                }
                is Route.Reader -> {
                    val rs = remember(route.id) { ReaderState(route.id, route.position, repo, container.scope) }
                    ReaderScreen(rs, settings.reader, { p -> container.update { it.copy(reader = p) } }, onBack = { stack.removeLast() })
                }
                null -> Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t, onClick = { tab = t },
                                    icon = {
                                        BadgedBox(badge = {
                                            if (t == Tab.LIBRARY && library.any { it.unreadNew > 0 }) Badge()
                                        }) { Icon(t.icon, t.label) }
                                    },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    },
                ) { pad ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(pad)) {
                        val open = { w: WorkInfo -> stack += Route.Work(w.id, w); Unit }
                        when (tab) {
                            Tab.SEARCH -> SearchScreen(search, libraryById, open)
                            Tab.LIBRARY -> LibraryScreen(library, progress, repo, open)
                            Tab.SETTINGS -> SettingsScreen(container)
                        }
                    }
                }
            }
        }
    }
}
