package app.yaooo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yaooo.data.LibraryWork
import app.yaooo.data.ReadProgress
import app.yaooo.data.WorkInfo
import app.yaooo.util.compact

@Composable
fun WorkCard(
    work: WorkInfo,
    library: LibraryWork?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: ReadProgress? = null,
) {
    Card(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(work.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "by " + work.authors.joinToString().ifBlank { "Anonymous" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (library?.bookmarked == true) Icon(Icons.Default.Bookmark, "Bookmarked", Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                if (library?.downloaded == true) Icon(Icons.Default.DownloadDone, "Downloaded", Modifier.size(18.dp).padding(start = 2.dp), MaterialTheme.colorScheme.tertiary)
                if ((library?.unreadNew ?: 0) > 0) Badge(Modifier.padding(start = 6.dp)) { Text("+${library!!.unreadNew}") }
            }
            if (work.fandoms.isNotEmpty())
                Text(work.fandoms.joinToString(", "), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RatingPill(work.rating)
                if (work.complete) Icon(Icons.Default.CheckCircle, "Complete", Modifier.size(16.dp), MaterialTheme.colorScheme.tertiary)
                Text(work.categories.joinToString(", "), style = MaterialTheme.typography.labelSmall)
            }
            if (work.relationships.isNotEmpty())
                Text(work.relationships.take(3).joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (work.summary.isNotBlank())
                Text(work.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(statsLine(work), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (progress != null) {
                val f = progress.fraction(work)
                LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Chapter ${progress.position}/${work.chaptersTotal ?: work.chaptersCurrent} · ${(f * 100).toInt()}% read",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

fun statsLine(w: WorkInfo): String = buildString {
    append(compact(w.words)).append(" words · ")
    append("ch ").append(w.chaptersCurrent).append('/').append(w.chaptersTotal ?: "?")
    append(" · ♥ ").append(compact(w.kudos))
    append(" · ").append(w.updated)
}

@Composable
fun RatingPill(rating: String) {
    val (label, color) = when {
        rating.startsWith("General") -> "G" to MaterialTheme.colorScheme.tertiaryContainer
        rating.startsWith("Teen") -> "T" to MaterialTheme.colorScheme.secondaryContainer
        rating.startsWith("Mature") -> "M" to MaterialTheme.colorScheme.primaryContainer
        rating.startsWith("Explicit") -> "E" to MaterialTheme.colorScheme.errorContainer
        else -> "NR" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = RoundedCornerShape(6.dp)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFlow(tags: List<String>, modifier: Modifier = Modifier, onTag: ((String) -> Unit)? = null) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        tags.forEach { t ->
            AssistChip(onClick = { onTag?.invoke(t) }, label = { Text(t, style = MaterialTheme.typography.labelSmall) }, enabled = onTag != null)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) {
            Spacer(Modifier.size(8.dp))
            androidx.compose.material3.OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
