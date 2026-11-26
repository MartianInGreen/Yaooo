package app.yaooo.util

/** Extracts a work id from anything a user might paste: work/chapter/download URLs, or a bare number. */
object Ao3Url {
    private val workUrl = Regex("""(?:archiveofourown\.(?:org|com|gay)|ao3\.org)/(?:collections/[^/\s]+/)?(?:works|downloads)/(\d+)""", RegexOption.IGNORE_CASE)

    fun workId(input: String): Long? {
        val t = input.trim()
        t.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        return workUrl.find(t)?.groupValues?.get(1)?.toLongOrNull()
    }
}
