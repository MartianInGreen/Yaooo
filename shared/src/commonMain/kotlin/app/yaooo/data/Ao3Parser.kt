package app.yaooo.data

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

/** Pure HTML -> model functions. No I/O, so everything here is unit-testable with saved pages. */
object Ao3Parser {

    fun parseSearchPage(html: String): SearchPage {
        val doc = Ksoup.parse(html)
        val works = doc.select("li.work.blurb, li.bookmark.blurb").mapNotNull { parseBlurb(it) }
        val heading = doc.selectFirst("h3.heading")?.text().orEmpty()
        val total = Regex("of ([\\d,]+) Works?").find(heading)?.groupValues?.get(1)?.toLongOrNull0()
            ?: Regex("^([\\d,]+) Works?").find(heading.trim())?.groupValues?.get(1)?.toLongOrNull0()
        val pages = doc.select("ol.pagination li a")
            .mapNotNull { it.text().trim().toIntOrNull() }
            .maxOrNull() ?: 1
        return SearchPage(works, total, pages)
    }

    fun parseBlurb(li: Element): WorkInfo? {
        val id = li.id().removePrefix("work_").toLongOrNull()
            ?: li.selectFirst("h4.heading a[href^=/works/]")?.attr("href")?.workIdFromHref()
            ?: return null
        val titleLink = li.selectFirst("h4.heading a[href^=/works/]") ?: return null
        val authors = li.select("h4.heading a[rel=author]").map { it.text() }
            .ifEmpty { if (li.selectFirst("h4.heading")?.text()?.contains("Anonymous") == true) listOf("Anonymous") else emptyList() }
        val required = li.select("ul.required-tags span.text").map { it.text() }
        val rating = li.selectFirst("ul.required-tags span.rating span.text")?.text().orEmpty()
        val categories = li.selectFirst("ul.required-tags span.category span.text")?.text()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val complete = li.selectFirst("ul.required-tags span.iswip")?.hasClass("complete-yes") == true ||
            required.any { it.equals("Complete Work", ignoreCase = true) }

        fun tags(cls: String) = li.select("ul.tags li.$cls a.tag").map { it.text() }

        val (cur, total) = parseChapters(li.selectFirst("dl.stats dd.chapters")?.text().orEmpty())
        return WorkInfo(
            id = id,
            title = titleLink.text(),
            authors = authors,
            fandoms = li.select("h5.fandoms a.tag").map { it.text() },
            rating = rating,
            warnings = tags("warnings"),
            categories = categories,
            relationships = tags("relationships"),
            characters = tags("characters"),
            freeforms = tags("freeforms"),
            summary = li.selectFirst("blockquote.summary")?.let { paragraphsText(it) }.orEmpty(),
            language = li.selectFirst("dl.stats dd.language")?.text().orEmpty(),
            words = stat(li, "words"),
            chaptersCurrent = cur,
            chaptersTotal = total,
            complete = complete,
            kudos = stat(li, "kudos"),
            hits = stat(li, "hits"),
            bookmarks = stat(li, "bookmarks"),
            comments = stat(li, "comments"),
            updated = li.selectFirst("p.datetime")?.text().orEmpty(),
        )
    }

    /**
     * Parses AO3's HTML download (`/downloads/ID/x.html`): plain XHTML with a `#preface` block (metadata) and a
     * `#chapters` block holding one `div.meta` heading + one `div.userstuff` body per chapter.
     * Kudos/hits/bookmarks/comments are not part of the download and are returned as 0.
     */
    fun parseDownload(html: String, workId: Long): WorkPage {
        val doc = Ksoup.parse(html)
        val root = doc.selectFirst("#chapters") ?: throw Ao3Exception(diagnose(html, doc), retryable = true)
        val preface = doc.selectFirst("#preface")

        val tags = mutableMapOf<String, List<String>>()
        val plain = mutableMapOf<String, String>()
        preface?.selectFirst("dl.tags")?.let { dl ->
            var label: String? = null
            for (c in dl.children()) when (c.normalName()) {
                "dt" -> label = c.text().trim().trimEnd(':').lowercase()
                "dd" -> label?.let { l ->
                    tags[l] = c.select("a").map { it.text() }
                    plain[l] = c.text().replace(Regex("\\s+"), " ").trim()
                }
            }
        }
        fun tag(vararg names: String) = names.firstNotNullOfOrNull { tags[it] }.orEmpty()

        val stats = plain["stats"].orEmpty()
        fun stat(name: String) = Regex("$name:\\s*(\\S+)").find(stats)?.groupValues?.get(1)
        val (cur, total) = parseChapters(Regex("Chapters:\\s*(\\S+)").find(stats)?.groupValues?.get(1)?.let { "Chapters $it" } ?: "")
        val chapterSpec = stat("Chapters")
        val chaptersNow = chapterSpec?.substringBefore('/')?.replace(",", "")?.toIntOrNull() ?: cur
        val chaptersTot = chapterSpec?.substringAfter('/', "")?.replace(",", "")?.toIntOrNull()

        val info = WorkInfo(
            id = workId,
            title = preface?.selectFirst("div.meta h1")?.text()?.trim().orEmpty().ifBlank { doc.title().substringBefore(" - ") },
            authors = preface?.select("div.byline a[rel=author]")?.map { it.text() }.orEmpty().ifEmpty { listOf("Anonymous") },
            fandoms = tag("fandoms", "fandom"),
            rating = tag("rating").firstOrNull().orEmpty(),
            warnings = tag("archive warning", "archive warnings"),
            categories = tag("category", "categories"),
            relationships = tag("relationships", "relationship"),
            characters = tag("characters", "character"),
            freeforms = tag("additional tags", "additional tag"),
            summary = preface?.selectFirst("blockquote.userstuff")?.let { paragraphsText(it) }.orEmpty(),
            language = plain["language"].orEmpty(),
            words = stat("Words")?.toLongOrNull0() ?: 0,
            chaptersCurrent = chaptersNow,
            chaptersTotal = chaptersTot,
            complete = stat("Completed") != null || (chaptersTot != null && chaptersNow >= chaptersTot),
            kudos = 0, hits = 0, bookmarks = 0, comments = 0,
            updated = stat("Updated") ?: stat("Completed") ?: stat("Published").orEmpty(),
        )

        val chapters = mutableListOf<ChapterContent>()
        var pendingTitle: String? = null
        for (child in root.children()) {
            when {
                child.normalName() == "div" && child.hasClass("meta") ->
                    pendingTitle = child.selectFirst("h2")?.text()?.replace(Regex("\\s+"), " ")?.trim()
                child.normalName() == "div" && child.hasClass("userstuff") -> {
                    val n = chapters.size + 1
                    chapters += ChapterContent(n, null, pendingTitle?.ifBlank { null } ?: "Chapter $n", child.html())
                    pendingTitle = null
                }
            }
        }
        if (chapters.isEmpty() && root.selectFirst("p") != null) {
            chapters += ChapterContent(1, null, "Chapter 1", root.html())
        }
        if (chapters.isEmpty()) throw Ao3Exception(diagnose(html, doc), retryable = true)
        return WorkPage(info.copy(chaptersCurrent = maxOf(info.chaptersCurrent, chapters.size)), chapters)
    }

    /** Parses `/works/ID/navigate`: one entry per published chapter. */
    fun parseNavigate(html: String): List<ChapterRef> {
        val doc = Ksoup.parse(html)
        return doc.select("ol.chapter.index li").mapIndexedNotNull { i, li ->
            val a = li.selectFirst("a") ?: return@mapIndexedNotNull null
            ChapterRef(
                position = i + 1,
                ao3Id = a.attr("href").substringAfter("/chapters/", "").takeWhile { it.isDigit() }.toLongOrNull(),
                title = a.text().trim(),
                date = li.selectFirst("span.datetime")?.text()?.trim('(', ')', ' ').orEmpty(),
            )
        }
    }

    /** True when AO3 shows the "this work could have adult content" interstitial or a login wall. */
    fun isRestricted(html: String): Boolean =
        html.contains("This work is only available to registered users") ||
            html.contains("id=\"tos_prompt\"") && !html.contains("dl class=\"work meta")

    private fun diagnose(html: String, doc: com.fleeksoft.ksoup.nodes.Document): String {
        val ids = Regex("id=\"([^\"]{1,30})\"").findAll(html).map { it.groupValues[1] }.distinct().take(12).joinToString(",")
        return "Unrecognised work page (${html.length} chars). title=\"${doc.title().take(50)}\" " +
            "rawHasMeta=${html.contains("work meta")} rawHasWorkskin=${html.contains("workskin")} " +
            "parsedDl=${doc.select("dl").size} parsedDiv=${doc.select("div").size} ids=[$ids]"
    }

    /** AO3's anti-bot throttle page ("The bot servers are currently overloaded..."). */
    fun isBotBlock(body: String): Boolean =
        body.length < 2000 && body.contains("bot servers are currently overloaded", ignoreCase = true)

    fun parseAutocomplete(json: String): List<String> =
        Regex("\"name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(json)
            .map { unescapeJson(it.groupValues[1]) }
            .toList()

    // ---- helpers ----------------------------------------------------------------------------------------------

    private fun paragraphsText(el: Element): String =
        el.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf(el.text().trim()) }.joinToString("\n\n")

    private fun stat(li: Element, cls: String): Long =
        li.selectFirst("dl.stats dd.$cls")?.text()?.toLongOrNull0() ?: 0L

    /** "3/10" -> (3, 10); "5/?" -> (5, null). */
    internal fun parseChapters(text: String): Pair<Int, Int?> {
        val m = Regex("(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*|\\?)").find(text) ?: return 1 to 1
        val cur = m.groupValues[1].replace(",", "").toIntOrNull() ?: 1
        val tot = m.groupValues[2].replace(",", "").toIntOrNull()
        return cur to tot
    }

    private fun String.workIdFromHref(): Long? =
        Regex("/works/(\\d+)").find(this)?.groupValues?.get(1)?.toLongOrNull()

    private fun unescapeJson(s: String): String =
        s.replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\")
            .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { it.groupValues[1].toInt(16).toChar().toString() }
}

internal fun String.toLongOrNull0(): Long? = replace(",", "").replace(".", "").trim().toLongOrNull()
