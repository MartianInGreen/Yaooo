package app.yaooo.data

import app.yaooo.db.Work
import app.yaooo.db.YaoooDb
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

private const val SEP = "\n"

class Repository(
    private val db: YaoooDb,
    private val client: Ao3Client,
    private val dispatcher: CoroutineDispatcher,
) {
    private val q get() = db.libraryQueries

    // ---- remote -----------------------------------------------------------------------------------------------

    suspend fun search(query: SearchQuery, page: Int) = client.search(query, page)
    suspend fun autocomplete(type: TagType, term: String) = client.autocomplete(type, term)

    /** Small in-memory cache so opening a work, then its chapters, costs one download. */
    private val cache = LinkedHashMap<Long, WorkPage>()
    private val lock = Mutex()
    private val inflight = HashMap<Long, Deferred<WorkPage>>()
    private val fetchScope = CoroutineScope(SupervisorJob() + dispatcher)

    /**
     * One shared download per work: the detail screen and the reader both ask for it, and a second caller joins
     * the request already in flight instead of starting another (large fics can take a while to generate).
     * The fetch is not cancelled if the screen that asked for it goes away.
     */
    private suspend fun fetchPage(id: Long, seed: WorkInfo?): WorkPage {
        lock.withLock { cache[id] }?.let { return it }
        val job = lock.withLock {
            inflight.getOrPut(id) {
                fetchScope.async {
                    val prior = libraryWork(id)?.info ?: seed
                    var p = client.work(id)
                    // A copy with fewer chapters than we already know about is stale: ask once more.
                    if (prior != null && p.chapters.size < prior.chaptersCurrent)
                        p = client.work(id, Clock.System.now().epochSeconds + 1)
                    val page = p.copy(info = p.info.withStatsFrom(prior))
                    lock.withLock {
                        cache[id] = page
                        while (cache.size > 3) cache.remove(cache.keys.first())
                    }
                    page
                }
            }
        }
        try {
            return job.await()
        } finally {
            lock.withLock { if (job.isCompleted) inflight.remove(id) }
        }
    }

    /**
     * Metadata + chapters for a work. Offline copies are served from the database; otherwise the work is fetched
     * once via AO3's HTML download and kept in memory. [seed] supplies stats the download doesn't carry
     * (kudos, hits, …) from the search result the user came from.
     */
    suspend fun loadWork(id: Long, seed: WorkInfo? = null): WorkDetail {
        val offline = offlineChapters(id)
        val known = libraryWork(id)
        if (known != null && known.downloaded && offline.isNotEmpty()) return WorkDetail(known.info, offline)
        val page = fetchPage(id, seed)
        if (known != null) upsert(page.info)
        return WorkDetail(page.info, page.chapters.map { ChapterRef(it.position, it.ao3Id, it.title) })
    }

    /** Chapter body from the offline copy if present, otherwise from the freshly downloaded work. */
    suspend fun chapter(workId: Long, position: Int): ChapterContent {
        q.selectChapter(workId, position.toLong()).executeAsOneOrNull()?.let {
            return ChapterContent(position, it.ao3_id, it.title, it.content)
        }
        val page = fetchPage(workId, null)
        return page.chapters.firstOrNull { it.position == position }
            ?: throw Ao3Exception("Chapter $position not found.")
    }

    // ---- library ----------------------------------------------------------------------------------------------

    fun observeLibrary(): Flow<List<LibraryWork>> =
        q.observeLibrary().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toLibraryWork() } }

    fun observeWork(id: Long): Flow<LibraryWork?> =
        q.observeWork(id).asFlow().mapToOneOrNull(dispatcher).map { it?.toLibraryWork() }

    fun libraryWork(id: Long): LibraryWork? = q.selectWork(id).executeAsOneOrNull()?.toLibraryWork()

    fun setBookmarked(info: WorkInfo, bookmarked: Boolean) {
        db.transaction {
            upsert(info)
            val now = Clock.System.now().toEpochMilliseconds()
            q.setBookmarked(if (bookmarked) 1 else 0, if (bookmarked) now else 0, if (bookmarked) 1 else 0, info.id)
        }
    }

    fun setFinished(id: Long, finished: Boolean) {
        val v = if (finished) 1L else 0L
        q.setFinished(v, v, id)
    }

    fun setNotify(id: Long, notify: Boolean) = q.setNotify(if (notify) 1 else 0, id)

    suspend fun download(id: Long, seed: WorkInfo? = null) {
        val page = fetchPage(id, seed)
        val info = page.info.withStatsFrom(libraryWork(id)?.info ?: seed)
        db.transaction {
            upsert(info)
            q.deleteChaptersFor(id)
            page.chapters.forEach { q.insertChapter(id, it.position.toLong(), it.ao3Id, it.title, it.html) }
            q.setDownloaded(1, id)
            q.clearUnread(id)
        }
        lock.withLock { cache.remove(id) }
    }

    fun removeDownload(id: Long) {
        db.transaction {
            q.deleteChaptersFor(id)
            q.setDownloaded(0, id)
        }
    }

    fun downloadedChapters(id: Long): Long = q.countChapters(id).executeAsOne()

    fun offlineChapters(id: Long): List<ChapterRef> =
        q.selectChapterTitles(id).executeAsList().map { ChapterRef(it.position.toInt(), it.ao3_id, it.title) }

    fun markRead(id: Long) = q.clearUnread(id)

    // ---- progress ---------------------------------------------------------------------------------------------

    fun saveProgress(workId: Long, position: Int, blockIndex: Int, blockCount: Int) =
        q.upsertProgress(workId, position.toLong(), blockIndex.toLong(), blockCount.toLong(), Clock.System.now().toEpochMilliseconds())

    fun progress(workId: Long): ReadProgress? =
        q.selectProgress(workId).executeAsOneOrNull()?.toReadProgress()

    fun observeProgress(): Flow<Map<Long, ReadProgress>> =
        q.observeProgress().asFlow().mapToList(dispatcher).map { rows -> rows.associate { it.work_id to it.toReadProgress() } }

    private fun app.yaooo.db.Progress.toReadProgress() = ReadProgress(position.toInt(), block_index.toInt(), block_count.toInt())

    // ---- update checking --------------------------------------------------------------------------------------

    fun notifyTargets(): List<LibraryWork> = q.selectNotifyTargets().executeAsList().map { it.toLibraryWork() }

    suspend fun chapterCountOnline(id: Long): List<ChapterRef> = client.chapterList(id)

    fun recordCheck(id: Long, remoteCount: Int, newChapters: Int, now: Long) {
        if (newChapters > 0) q.recordNewChapters(remoteCount.toLong(), newChapters.toLong(), now, id)
        else q.markChecked(now, id)
    }

    // ---- settings ---------------------------------------------------------------------------------------------

    fun setting(key: String): String? = q.getSetting(key).executeAsOneOrNull()
    fun putSetting(key: String, value: String) = q.putSetting(key, value)

    // ---- mapping ----------------------------------------------------------------------------------------------

    private fun saveMetadataIfKnown(info: WorkInfo) {
        if (q.selectWork(info.id).executeAsOneOrNull() != null) upsert(info)
    }

    private fun upsert(i: WorkInfo) {
        val authors = i.authors.join(); val fandoms = i.fandoms.join(); val warnings = i.warnings.join()
        val cats = i.categories.join(); val rels = i.relationships.join(); val chars = i.characters.join()
        val free = i.freeforms.join(); val complete = if (i.complete) 1L else 0L
        val cur = i.chaptersCurrent.toLong(); val tot = i.chaptersTotal?.toLong()
        db.transaction {
            q.insertWork(i.id, i.title, authors, fandoms, i.rating, warnings, cats, rels, chars, free, i.summary,
                i.language, i.words, cur, tot, complete, i.kudos, i.hits, i.bookmarks, i.comments, i.updated)
            q.updateWork(i.id, i.title, authors, fandoms, i.rating, warnings, cats, rels, chars, free, i.summary,
                i.language, i.words, cur, tot, complete, i.kudos, i.hits, i.bookmarks, i.comments, i.updated)
        }
    }

    private fun List<String>.join() = joinToString(SEP)
    private fun String.split2() = if (isEmpty()) emptyList() else split(SEP)

    private fun Work.toLibraryWork() = LibraryWork(
        info = WorkInfo(
            id, title, authors.split2(), fandoms.split2(), rating, warnings.split2(), categories.split2(),
            relationships.split2(), characters.split2(), freeforms.split2(), summary, language, words,
            chapters_current.toInt(), chapters_total?.toInt(), complete == 1L, kudos, hits, bookmarks, comments, updated,
        ),
        bookmarked = bookmarked == 1L,
        bookmarkedAt = bookmarked_at,
        notify = notify == 1L,
        downloaded = downloaded == 1L,
        knownChapters = known_chapters.toInt(),
        unreadNew = unread_new.toInt(),
        finished = finished == 1L,
    )
}

data class WorkDetail(val info: WorkInfo, val chapters: List<ChapterRef>)

/** The HTML download has no kudos/hits/etc.; carry them over from a previous copy when we have one. */
private fun WorkInfo.withStatsFrom(old: WorkInfo?): WorkInfo =
    if (old == null) this else copy(
        kudos = if (kudos == 0L) old.kudos else kudos,
        hits = if (hits == 0L) old.hits else hits,
        bookmarks = if (bookmarks == 0L) old.bookmarks else bookmarks,
        comments = if (comments == 0L) old.comments else comments,
        rating = rating.ifBlank { old.rating },
    )
