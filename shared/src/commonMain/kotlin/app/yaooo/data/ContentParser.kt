package app.yaooo.data

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
)

sealed interface Block {
    data class Paragraph(val spans: List<Span>, val center: Boolean = false) : Block
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class Quote(val spans: List<Span>) : Block
    data class ListItem(val spans: List<Span>, val marker: String) : Block
    data object Rule : Block
}

/**
 * Converts AO3's chapter HTML into a flat list of blocks so the reader can use one lazy list item per paragraph
 * (cheap to compose and measure, even for 100k-word chapters).
 */
object ContentParser {
    fun parse(html: String): List<Block> {
        val body = Ksoup.parse(html).body()
        val out = mutableListOf<Block>()
        walkContainer(body, out)
        return out
    }

    private val blockTags = setOf(
        "p", "div", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "ul", "ol", "li", "pre", "center", "table",
    )

    private fun walkContainer(container: Element, out: MutableList<Block>, center: Boolean = false) {
        val inline = mutableListOf<Node>()
        fun flush() {
            if (inline.isNotEmpty()) {
                val spans = collectSpans(inline)
                if (spans.any { it.text.isNotBlank() }) out += Block.Paragraph(spans, center)
                inline.clear()
            }
        }
        for (node in container.childNodes()) {
            if (node is Element && node.normalName() in blockTags) {
                flush()
                handleBlock(node, out, center)
            } else inline += node
        }
        flush()
    }

    private fun handleBlock(el: Element, out: MutableList<Block>, center: Boolean) {
        val isCentered = center || el.normalName() == "center" ||
            el.attr("align") == "center" || el.attr("style").replace(" ", "").contains("text-align:center")
        when (el.normalName()) {
            "hr" -> out += Block.Rule
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val spans = collectSpans(el.childNodes())
                if (spans.any { it.text.isNotBlank() }) out += Block.Heading(el.normalName()[1] - '0', spans)
            }
            "blockquote" -> {
                if (el.children().any { it.normalName() in blockTags }) {
                    val inner = mutableListOf<Block>()
                    walkContainer(el, inner, isCentered)
                    inner.forEach { b ->
                        out += if (b is Block.Paragraph) Block.Quote(b.spans) else b
                    }
                } else {
                    val spans = collectSpans(el.childNodes())
                    if (spans.any { it.text.isNotBlank() }) out += Block.Quote(spans)
                }
            }
            "ul", "ol" -> {
                var n = 0
                for (li in el.children().filter { it.normalName() == "li" }) {
                    n++
                    val marker = if (el.normalName() == "ol") "$n." else "•"
                    out += Block.ListItem(collectSpans(li.childNodes()), marker)
                }
            }
            "li" -> out += Block.ListItem(collectSpans(el.childNodes()), "•")
            "pre" -> out += Block.Paragraph(listOf(Span(el.wholeText())), isCentered)
            else -> {
                // p, div, center, table: paragraph if it only has inline content, else recurse.
                if (el.children().any { it.normalName() in blockTags }) walkContainer(el, out, isCentered)
                else {
                    val spans = collectSpans(el.childNodes())
                    if (spans.any { it.text.isNotBlank() }) out += Block.Paragraph(spans, isCentered)
                }
            }
        }
    }

    private data class Style(val b: Boolean = false, val i: Boolean = false, val u: Boolean = false, val s: Boolean = false)

    private fun collectSpans(nodes: List<Node>): List<Span> {
        val spans = mutableListOf<Span>()
        fun walk(node: Node, st: Style) {
            when (node) {
                is TextNode -> {
                    val t = node.text().replace(Regex("\\s+"), " ")
                    if (t.isNotEmpty()) spans += Span(t, st.b, st.i, st.u, st.s)
                }
                is Element -> when (node.normalName()) {
                    "br" -> spans += Span("\n", st.b, st.i, st.u, st.s)
                    "b", "strong" -> node.childNodes().forEach { walk(it, st.copy(b = true)) }
                    "i", "em", "cite" -> node.childNodes().forEach { walk(it, st.copy(i = true)) }
                    "u", "ins" -> node.childNodes().forEach { walk(it, st.copy(u = true)) }
                    "s", "strike", "del" -> node.childNodes().forEach { walk(it, st.copy(s = true)) }
                    else -> node.childNodes().forEach { walk(it, st) }
                }
            }
        }
        nodes.forEach { walk(it, Style()) }
        return trim(merge(spans))
    }

    private fun merge(spans: List<Span>): List<Span> {
        val out = mutableListOf<Span>()
        for (s in spans) {
            val last = out.lastOrNull()
            if (last != null && last.bold == s.bold && last.italic == s.italic &&
                last.underline == s.underline && last.strike == s.strike
            ) out[out.lastIndex] = last.copy(text = last.text + s.text) else out += s
        }
        return out
    }

    private fun trim(spans: List<Span>): List<Span> {
        if (spans.isEmpty()) return spans
        val list = spans.toMutableList()
        list[0] = list[0].copy(text = list[0].text.trimStart())
        list[list.lastIndex] = list.last().copy(text = list.last().text.trimEnd())
        return list.filter { it.text.isNotEmpty() }
    }
}
