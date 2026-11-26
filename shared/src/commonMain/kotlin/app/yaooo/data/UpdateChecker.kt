package app.yaooo.data

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock

data class NewChapters(val work: WorkInfo, val added: Int)

/**
 * Compares the chapter count AO3 reports against what we last knew for each bookmarked, notify-enabled WIP.
 * Platform code (WorkManager / BGTaskScheduler) calls [run] and shows notifications for the result.
 */
class UpdateChecker(private val repo: Repository, private val autoRedownload: Boolean = true) {

    /** @return works with new chapters; throws only if AO3 is unreachable for the very first request. */
    suspend fun run(): List<NewChapters> {
        val found = mutableListOf<NewChapters>()
        var firstError: Ao3Exception? = null
        var succeeded = 0
        for (w in repo.notifyTargets()) {
            try {
                val remote = repo.chapterCountOnline(w.info.id).size
                val now = Clock.System.now().toEpochMilliseconds()
                val added = (remote - w.knownChapters).coerceAtLeast(0)
                repo.recordCheck(w.info.id, maxOf(remote, w.knownChapters), added, now)
                succeeded++
                if (added > 0) {
                    if (w.downloaded && autoRedownload) runCatching { repo.download(w.info.id) }
                    found += NewChapters(w.info, added)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Ao3Exception) {
                firstError = firstError ?: e
                if (e.retryable && succeeded == 0) break // network is down; don't hammer AO3 or burn battery
            }
        }
        if (succeeded == 0 && firstError != null && found.isEmpty()) throw firstError
        return found
    }
}
