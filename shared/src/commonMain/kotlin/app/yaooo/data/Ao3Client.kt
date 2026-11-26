package app.yaooo.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class Ao3Exception(message: String, val retryable: Boolean = false) : Exception(message)

/**
 * Thin AO3 HTTP layer. AO3 has no official API, so this scrapes HTML. It is deliberately polite:
 * requests are serialised, spaced at least [minIntervalMs] apart, and 429 responses honour `Retry-After`.
 */
class Ao3Client(
    private val http: HttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://archiveofourown.org",
    private val minIntervalMs: Long = 1500,
) {
    private val gate = Mutex()
    private var lastRequestAt = 0L

    suspend fun search(query: SearchQuery, page: Int = 1): SearchPage {
        val html = get("/works/search") {
            query.toParameters().forEach { (k, v) -> parameter(k, v) }
            parameter("page", page)
            parameter("commit", "Search")
        }
        return Ao3Parser.parseSearchPage(html)
    }

    /**
     * Whole work (metadata + every chapter) from AO3's HTML download. One request, simple stable markup,
     * and it is the same file AO3's own "Download" button serves. Any file name works in the URL.
     * AO3 marks these files cacheable for a year, so `updated_at` (which its own button also sends) is passed to
     * avoid receiving an old copy with missing chapters and outdated counts.
     */
    suspend fun work(id: Long, bust: Long = Clock.System.now().epochSeconds): WorkPage =
        Ao3Parser.parseDownload(get("/downloads/$id/work.html") { parameter("updated_at", bust) }, id)

    /** Lightweight chapter listing, used for update checks. */
    suspend fun chapterList(workId: Long): List<ChapterRef> {
        val html = get("/works/$workId/navigate") { parameter("view_adult", "true") }
        return Ao3Parser.parseNavigate(html)
    }

    suspend fun autocomplete(type: TagType, term: String): List<String> {
        if (term.length < 2) return emptyList()
        val json = get("/autocomplete/${type.autocompletePath}") {
            parameter("term", term)
            header(HttpHeaders.Accept, "application/json")
        }
        return Ao3Parser.parseAutocomplete(json)
    }

    private suspend fun get(path: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): String {
        var attempt = 0
        while (true) {
            val resp = gate.withLock {
                val wait = lastRequestAt + minIntervalMs - Clock.System.now().toEpochMilliseconds()
                if (wait > 0) delay(wait)
                try {
                    http.get {
                        url(baseUrl + path)
                        block()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw Ao3Exception("Network error: ${e.message ?: e::class.simpleName}", retryable = true)
                } finally {
                    lastRequestAt = Clock.System.now().toEpochMilliseconds()
                }
            }
            when {
                resp.status == HttpStatusCode.TooManyRequests && attempt < 2 && !Ao3Parser.isBotBlock(resp.bodyAsText()) -> {
                    val secs = resp.headers[HttpHeaders.RetryAfter]?.toLongOrNull() ?: 30
                    attempt++
                    delay(secs.coerceIn(5, 300) * 1000)
                }
                resp.status == HttpStatusCode.TooManyRequests ->
                    throw Ao3Exception("AO3 is rate limiting us (its bot filter may be active, also on VPNs). Try again in a few minutes.", retryable = true)
                resp.status == HttpStatusCode.NotFound -> throw Ao3Exception("Not found on AO3.")
                resp.status.value in 500..599 -> throw Ao3Exception("AO3 is having trouble (${resp.status.value}).", retryable = true)
                resp.status.value !in 200..299 -> throw Ao3Exception("Unexpected response ${resp.status.value}.")
                else -> {
                    val body = resp.bodyAsText()
                    if (Ao3Parser.isBotBlock(body))
                        throw Ao3Exception("AO3's bot filter is limiting requests right now (this can also happen on a VPN). Wait a few minutes and try again.", retryable = true)
                    return body
                }
            }
        }
    }

    companion object {
        fun defaultHttpClient() = HttpClient {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout) {
                requestTimeoutMillis = 180_000
                connectTimeoutMillis = 15_000
            }
            install(ContentEncoding) { gzip(); deflate() }
            defaultRequest()
        }

        private fun io.ktor.client.HttpClientConfig<*>.defaultRequest() {
            install(io.ktor.client.plugins.DefaultRequest) {
                header(HttpHeaders.UserAgent, "Yaooo/0.1 (personal AO3 reader app)")
                header(HttpHeaders.AcceptLanguage, "en")
            }
        }
    }
}
