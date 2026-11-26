package app.yaooo.data

/** A work as shown in search results / on the detail page. Mirrors the `work` table minus library state. */
data class WorkInfo(
    val id: Long,
    val title: String,
    val authors: List<String>,
    val fandoms: List<String>,
    val rating: String,
    val warnings: List<String>,
    val categories: List<String>,
    val relationships: List<String>,
    val characters: List<String>,
    val freeforms: List<String>,
    val summary: String,
    val language: String,
    val words: Long,
    val chaptersCurrent: Int,
    /** null when the author has not set a planned chapter count ("3/?"). */
    val chaptersTotal: Int?,
    val complete: Boolean,
    val kudos: Long,
    val hits: Long,
    val bookmarks: Long,
    val comments: Long,
    val updated: String,
)

/** Library flags layered on top of [WorkInfo]. */
data class LibraryWork(
    val info: WorkInfo,
    val bookmarked: Boolean,
    val bookmarkedAt: Long,
    val notify: Boolean,
    val downloaded: Boolean,
    val knownChapters: Int,
    val unreadNew: Int,
    val finished: Boolean,
)

data class ChapterRef(val position: Int, val ao3Id: Long?, val title: String, val date: String = "")

data class ChapterContent(val position: Int, val ao3Id: Long?, val title: String, val html: String)

data class SearchPage(val works: List<WorkInfo>, val totalFound: Long?, val totalPages: Int)

/** Result of parsing a `/works/ID` page; contains whichever chapters were present on that page. */
data class WorkPage(val info: WorkInfo, val chapters: List<ChapterContent>)

enum class TagType(val label: String, val autocompletePath: String, val param: String) {
    FANDOM("Fandom", "fandom", "fandom_names"),
    CHARACTER("Character", "character", "character_names"),
    RELATIONSHIP("Relationship", "relationship", "relationship_names"),
    FREEFORM("Additional tag", "freeform", "freeform_names"),
}

data class TagFilter(val type: TagType, val name: String, val exclude: Boolean = false)

/** Where the reader stopped: chapter [position], paragraph [blockIndex] of [blockCount]. */
data class ReadProgress(val position: Int, val blockIndex: Int, val blockCount: Int) {
    /** 0..1 over the whole story; unfinished/unknown chapter totals fall back to the chapters published so far. */
    fun fraction(info: WorkInfo): Float {
        val total = (info.chaptersTotal ?: info.chaptersCurrent).coerceAtLeast(1)
        val within = if (blockCount > 0) blockIndex.toFloat() / blockCount else 0f
        return (((position - 1) + within) / total).coerceIn(0f, 1f)
    }
}
