package app.yaooo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yaooo.AppContainer
import app.yaooo.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val s by container.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionLabel("Theme")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m ->
                FilterChip(s.theme == m, { container.update { it.copy(theme = m) } }, label = { Text(m.name.lowercase().replaceFirstChar { c -> c.uppercase() }) })
            }
        }

        SectionLabel("New chapter notifications")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check bookmarked fics for updates", Modifier.weight(1f))
            Switch(s.notificationsEnabled, { v -> container.update { it.copy(notificationsEnabled = v) } })
        }
        Text("Check every", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 6, 12, 24).forEach { h ->
                FilterChip(s.checkIntervalHours == h, { container.update { it.copy(checkIntervalHours = h) } }, label = { Text("${h}h") }, enabled = s.notificationsEnabled)
            }
        }
        Text(
            "The OS decides the exact time; checks are batched and only run with a network connection. " +
                "Only bookmarked, unfinished fics with notifications switched on are checked, one light request each.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        OutlinedButton(
            onClick = {
                checking = true; status = null
                scope.launch {
                    status = try {
                        val r = container.updateChecker.run()
                        if (r.isNotEmpty() && s.notificationsEnabled) container.notifier.notifyNewChapters(r)
                        if (r.isEmpty()) "No new chapters." else "${r.size} fic(s) updated."
                    } catch (e: Exception) { "Check failed: ${e.message}" }
                    checking = false
                }
            },
            enabled = !checking,
        ) { Text(if (checking) "Checking…" else "Check now") }
        status?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall) }

        SectionLabel("Reading")
        Text("Text size and spacing can be changed inside the reader.", style = MaterialTheme.typography.bodySmall)

        SectionLabel("About")
        Text(
            "Yaooo is an unofficial reader for Archive of Our Own. It is not affiliated with the OTW. " +
                "Content is fetched from archiveofourown.org at a throttled rate; please respect their Terms of Service.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
