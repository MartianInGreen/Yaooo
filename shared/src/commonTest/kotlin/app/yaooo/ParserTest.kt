package app.yaooo

import app.yaooo.data.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserTest {
    private val searchHtml = """
    <html><body><div id="main" class="works-index">
    <h3 class="heading">1 - 20 of 1,234 Works found</h3>
    <ol class="pagination actions"><li class="previous">← Previous</li><li><span class="current">1</span></li>
      <li><a href="/works/search?page=2">2</a></li><li><a href="/works/search?page=62">62</a></li><li class="next"><a rel="next">Next →</a></li></ol>
    <ol class="work index group">
    <li id="work_555" class="work blurb group work-555 user-9" role="article">
      <div class="header module">
        <h4 class="heading"><a href="/works/555">A Long Night</a> by <a rel="author" href="/users/a/pseuds/a">alice</a>, <a rel="author" href="/users/b/pseuds/b">bob</a></h4>
        <h5 class="fandoms heading"><span class="landmark">Fandoms:</span> <a class="tag" href="/tags/x">Good Omens</a>, <a class="tag" href="/tags/y">Sherlock</a></h5>
        <ul class="required-tags">
          <li><a class="help symbol question modal modal-attached" href="/help/symbols-key.html"><span class="rating-teen rating" title="Teen And Up Audiences"><span class="text">Teen And Up Audiences</span></span></a></li>
          <li><a class="help symbol question modal modal-attached" href="/help/symbols-key.html"><span class="warnings-yes warnings" title="Choose"><span class="text">Creator Chose Not To Use Archive Warnings</span></span></a></li>
          <li><a class="help symbol question modal modal-attached" href="/help/symbols-key.html"><span class="category-mm category" title="M/M"><span class="text">M/M, Gen</span></span></a></li>
          <li><a class="help symbol question modal modal-attached" href="/help/symbols-key.html"><span class="complete-no iswip" title="Work in Progress"><span class="text">Work in Progress</span></span></a></li>
        </ul>
        <p class="datetime">12 Jan 2024</p>
      </div>
      <h6 class="landmark heading">Tags</h6>
      <ul class="tags commas">
        <li class="warnings"><strong><a class="tag" href="/t">Creator Chose Not To Use Archive Warnings</a></strong></li>
        <li class="relationships"><a class="tag" href="/t">Aziraphale/Crowley</a></li>
        <li class="characters"><a class="tag" href="/t">Aziraphale</a></li><li class="characters"><a class="tag" href="/t">Crowley</a></li>
        <li class="freeforms"><a class="tag" href="/t">Fluff</a></li>
      </ul>
      <h6 class="landmark heading">Summary</h6>
      <blockquote class="userstuff summary"><p>First para.</p><p>Second para.</p></blockquote>
      <dl class="stats">
        <dt class="language">Language:</dt><dd class="language" lang="en">English</dd>
        <dt class="words">Words:</dt><dd class="words">12,345</dd>
        <dt class="chapters">Chapters:</dt><dd class="chapters"><a href="/works/555/chapters/1">3</a>/?</dd>
        <dt class="comments">Comments:</dt><dd class="comments"><a href="#">12</a></dd>
        <dt class="kudos">Kudos:</dt><dd class="kudos"><a href="#">1,000</a></dd>
        <dt class="bookmarks">Bookmarks:</dt><dd class="bookmarks"><a href="#">7</a></dd>
        <dt class="hits">Hits:</dt><dd class="hits">5,678</dd>
      </dl>
    </li></ol></div></body></html>
    """

    @Test fun parsesSearchResults() {
        val page = Ao3Parser.parseSearchPage(searchHtml)
        assertEquals(1234L, page.totalFound)
        assertEquals(62, page.totalPages)
        val w = page.works.single()
        assertEquals(555L, w.id)
        assertEquals("A Long Night", w.title)
        assertEquals(listOf("alice", "bob"), w.authors)
        assertEquals(listOf("Good Omens", "Sherlock"), w.fandoms)
        assertEquals("Teen And Up Audiences", w.rating)
        assertEquals(listOf("M/M", "Gen"), w.categories)
        assertEquals(listOf("Aziraphale/Crowley"), w.relationships)
        assertEquals(listOf("Aziraphale", "Crowley"), w.characters)
        assertEquals("First para.\n\nSecond para.", w.summary)
        assertEquals(12345L, w.words)
        assertEquals(3, w.chaptersCurrent)
        assertNull(w.chaptersTotal)
        assertEquals(1000L, w.kudos)
        assertEquals(false, w.complete)
        assertEquals("12 Jan 2024", w.updated)
    }

    @Test fun parsesChapterCounts() {
        assertEquals(1 to 1, Ao3Parser.parseChapters("1/1"))
        assertEquals(5 to 20, Ao3Parser.parseChapters("5/20"))
        assertEquals(5 to null, Ao3Parser.parseChapters("5/?"))
        assertEquals(1234 to 2000, Ao3Parser.parseChapters("1,234/2,000"))
    }

    // Trimmed from a real AO3 HTML download (/downloads/10000/work.html).
    private val downloadHtml = """
    <html><head><title>Letters to a Dear Stranger - wanderlustlover - Smallville - Fandom</title></head><body>
    <div id="preface"><h2 class="toc-heading">Preface</h2>
      <p class="message"><b>Letters to a Dear Stranger</b><br />Posted originally on AO3</p>
      <div class="meta"><dl class="tags">
          <dt>Rating:</dt><dd><a href="x">Teen And Up Audiences</a></dd>
          <dt>Archive Warning:</dt><dd><a href="x">No Archive Warnings Apply</a></dd>
          <dt>Category:</dt><dd><a href="x">Multi</a></dd>
          <dt>Fandoms:</dt><dd><a href="x">Smallville</a>, <a href="x">Superman - Fandom</a></dd>
          <dt>Relationships:</dt><dd><a href="x">Chloe Sullivan/Clark Kent</a>, <a href="x">Clark Kent/Lex Luthor</a></dd>
          <dt>Characters:</dt><dd><a href="x">Chloe Sullivan</a>, <a href="x">Lex Luthor</a></dd>
          <dt>Additional Tags:</dt><dd><a href="x">Angst</a></dd>
          <dt>Language:</dt><dd>English</dd>
          <dt>Stats:</dt><dd>
            Published: 2009-11-15
            Updated: 2010-01-02
            Words: 11,743
            Chapters: 2/?
          </dd></dl>
        <h1>Letters to a Dear Stranger</h1>
        <div class="byline">by <a rel="author" href="x">wanderlustlover</a></div>
        <p>Summary</p><blockquote class="userstuff"><p>A correspondence.</p></blockquote>
      </div></div>
    <div id="chapters" class="userstuff">
    <div class="meta group"><h2 class="heading">
        Chapter 1: Love Always
    </h2></div>
    <!--chapter content--><div class="userstuff"><p>Dear <em>Clark</em>,</p></div><!--/chapter content-->
    <div class="meta group"><h2 class="heading">Chapter 2</h2></div>
    <!--chapter content--><div class="userstuff"><p>Dear Chloe,</p></div><!--/chapter content-->
    </div>
    <div id="afterword"><h2 class="toc-heading">Afterword</h2></div></body></html>
    """

    @Test fun parsesDownload() {
        val page = Ao3Parser.parseDownload(downloadHtml, 10000)
        val i = page.info
        assertEquals("Letters to a Dear Stranger", i.title)
        assertEquals(listOf("wanderlustlover"), i.authors)
        assertEquals("Teen And Up Audiences", i.rating)
        assertEquals(listOf("No Archive Warnings Apply"), i.warnings)
        assertEquals(listOf("Multi"), i.categories)
        assertEquals(listOf("Smallville", "Superman - Fandom"), i.fandoms)
        assertEquals(listOf("Chloe Sullivan/Clark Kent", "Clark Kent/Lex Luthor"), i.relationships)
        assertEquals(listOf("Angst"), i.freeforms)
        assertEquals("English", i.language)
        assertEquals("A correspondence.", i.summary)
        assertEquals(11743L, i.words)
        assertEquals(2, i.chaptersCurrent); assertNull(i.chaptersTotal)
        assertEquals(false, i.complete)
        assertEquals("2010-01-02", i.updated)
        assertEquals(2, page.chapters.size)
        assertEquals("Chapter 1: Love Always", page.chapters[0].title)
        assertEquals("Chapter 2", page.chapters[1].title)
        assertTrue("<em>Clark</em>" in page.chapters[0].html)
    }

    @Test fun parsesSingleChapterDownload() {
        val page = Ao3Parser.parseDownload(
            """<div id="preface"><div class="meta"><dl class="tags"><dt>Stats:</dt><dd>Published: 2020-01-01 Words: 50 Chapters: 1/1</dd></dl><h1>Short</h1></div></div>
               <div id="chapters" class="userstuff"><div class="userstuff"><p>Only text.</p></div></div>""", 5,
        )
        assertEquals(1, page.chapters.size)
        assertEquals("Chapter 1", page.chapters[0].title)
        assertEquals(true, page.info.complete)
        assertEquals("Anonymous", page.info.authors.single())
    }

    @Test fun parsesNavigate() {
        val refs = Ao3Parser.parseNavigate(
            """<ol class="chapter index group"><li><a href="/works/1/chapters/101">1. One</a> <span class="datetime">(2020-01-01)</span></li>
               <li><a href="/works/1/chapters/102">2. Two</a> <span class="datetime">(2020-02-01)</span></li></ol>""",
        )
        assertEquals(2, refs.size)
        assertEquals(102L, refs[1].ao3Id)
        assertEquals("2020-02-01", refs[1].date)
        assertEquals(2, refs[1].position)
    }

    @Test fun parsesAutocomplete() {
        assertEquals(listOf("Good Omens - Neil Gaiman", "A \"quoted\" one"),
            Ao3Parser.parseAutocomplete("""[{"id":"Good Omens - Neil Gaiman","name":"Good Omens - Neil Gaiman"},{"id":"x","name":"A \"quoted\" one"}]"""))
    }

    @Test fun contentBlocks() {
        val blocks = ContentParser.parse(
            """<p>Hi <strong>bold</strong> and <em>it</em><br>next line</p><hr><blockquote><p>Quoted</p></blockquote>
               <div align="center">Centered</div><h3>Head</h3>bare text<p> </p>""",
        )
        val p = blocks[0] as Block.Paragraph
        assertEquals("Hi bold and it\nnext line", p.spans.joinToString("") { it.text })
        assertTrue(p.spans.any { it.bold && it.text == "bold" })
        assertTrue(p.spans.any { it.italic && it.text == "it" })
        assertEquals(Block.Rule, blocks[1])
        assertTrue(blocks[2] is Block.Quote)
        assertTrue((blocks[3] as Block.Paragraph).center)
        assertTrue(blocks[4] is Block.Heading)
        assertEquals("bare text", ((blocks[5]) as Block.Paragraph).spans.single().text)
        assertEquals(6, blocks.size)
    }

    @Test fun queryToParameters() {
        val q = SearchQuery(
            text = "coffee shop",
            tags = listOf(TagFilter(TagType.FANDOM, "Good Omens"), TagFilter(TagType.FREEFORM, "Angst", exclude = true)),
            ratings = setOf(Rating.TEEN, Rating.MATURE), completion = Completion.COMPLETE, minKudos = "100",
            sort = SortColumn.KUDOS,
        )
        val p = q.toParameters()
        assertTrue("work_search[query]" to "coffee shop -\"Angst\"" in p)
        assertTrue("work_search[fandom_names]" to "Good Omens" in p)
        assertTrue(p.none { it.first == "work_search[freeform_names]" })
        assertEquals(listOf("10", "11"), p.filter { it.first == "work_search[rating_ids][]" }.map { it.second })
        assertTrue("work_search[complete]" to "T" in p)
        assertTrue("work_search[kudos_count]" to ">100" in p)
        assertTrue("work_search[sort_column]" to "kudos_count" in p)
    }

    @Test fun parsesAo3Urls() {
        assertEquals(123L, app.yaooo.util.Ao3Url.workId("https://archiveofourown.org/works/123"))
        assertEquals(123L, app.yaooo.util.Ao3Url.workId("archiveofourown.org/works/123/chapters/456#workskin"))
        assertEquals(123L, app.yaooo.util.Ao3Url.workId("https://archiveofourown.org/collections/foo/works/123?view_adult=true"))
        assertEquals(77L, app.yaooo.util.Ao3Url.workId("https://download.archiveofourown.org/downloads/77/x.html"))
        assertEquals(5L, app.yaooo.util.Ao3Url.workId(" 5 "))
        assertNull(app.yaooo.util.Ao3Url.workId("https://example.com/works/9"))
        assertNull(app.yaooo.util.Ao3Url.workId("https://archiveofourown.org/tags/Foo"))
    }

    @Test fun readProgressFraction() {
        val w = Ao3Parser.parseSearchPage(searchHtml).works.single().copy(chaptersCurrent = 4, chaptersTotal = 10)
        assertEquals(0.2f, ReadProgress(3, 0, 10).fraction(w), 0.001f)
        assertEquals(0.25f, ReadProgress(3, 5, 10).fraction(w), 0.001f)
        assertEquals(1f, ReadProgress(10, 10, 10).fraction(w), 0.001f)
        assertEquals(0.25f, ReadProgress(2, 0, 0).fraction(w.copy(chaptersTotal = null, chaptersCurrent = 4)), 0.001f)
    }
}
