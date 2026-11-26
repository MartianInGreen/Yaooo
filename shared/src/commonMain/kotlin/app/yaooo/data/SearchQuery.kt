package app.yaooo.data

enum class Rating(val id: Int, val label: String) {
    GENERAL(9, "General"), TEEN(10, "Teen"), MATURE(11, "Mature"), EXPLICIT(12, "Explicit"), NOT_RATED(13, "Not rated")
}

enum class Warning(val id: Int, val label: String) {
    NONE_APPLY(16, "No warnings apply"),
    VIOLENCE(17, "Graphic violence"),
    MCD(18, "Major character death"),
    NON_CON(19, "Rape/Non-con"),
    UNDERAGE(20, "Underage"),
    NOT_USED(14, "Creator chose not to warn"),
}

enum class Category(val id: Int, val label: String) {
    FF(116, "F/F"), FM(22, "F/M"), GEN(21, "Gen"), MM(23, "M/M"), MULTI(2246, "Multi"), OTHER(24, "Other")
}

enum class Completion(val label: String, val value: String) {
    ANY("Any", ""), COMPLETE("Complete", "T"), IN_PROGRESS("In progress", "F")
}

enum class SortColumn(val label: String, val value: String) {
    BEST_MATCH("Best match", "_score"),
    UPDATED("Date updated", "revised_at"),
    POSTED("Date posted", "created_at"),
    KUDOS("Kudos", "kudos_count"),
    HITS("Hits", "hits"),
    BOOKMARKS("Bookmarks", "bookmarks_count"),
    COMMENTS("Comments", "comments_count"),
    WORDS("Word count", "word_count"),
    TITLE("Title", "title_to_sort_on"),
}

data class SearchQuery(
    val text: String = "",
    val title: String = "",
    val author: String = "",
    val tags: List<TagFilter> = emptyList(),
    val ratings: Set<Rating> = emptySet(),
    val warnings: Set<Warning> = emptySet(),
    val categories: Set<Category> = emptySet(),
    val completion: Completion = Completion.ANY,
    val excludeCrossovers: Boolean = false,
    val singleChapterOnly: Boolean = false,
    /** Free-form AO3 range syntax, e.g. "1000-5000", ">10000". */
    val wordCount: String = "",
    val updatedWithin: String = "",
    val language: String = "",
    val minKudos: String = "",
    val sort: SortColumn = SortColumn.BEST_MATCH,
    val sortDescending: Boolean = true,
) {
    val isEmpty: Boolean
        get() = this == SearchQuery()

    val activeFilterCount: Int
        get() = listOf(
            ratings.isNotEmpty(), warnings.isNotEmpty(), categories.isNotEmpty(),
            completion != Completion.ANY, excludeCrossovers, singleChapterOnly,
            wordCount.isNotBlank(), updatedWithin.isNotBlank(), language.isNotBlank(),
            minKudos.isNotBlank(), title.isNotBlank(), author.isNotBlank(),
        ).count { it }

    /** Flattens to AO3's `work_search[...]` form parameters (keys are returned already wrapped). */
    fun toParameters(): List<Pair<String, String>> {
        val p = mutableListOf<Pair<String, String>>()
        fun add(key: String, value: String) { if (value.isNotBlank()) p += "work_search[$key]" to value.trim() }

        // AO3 has no dedicated "exclude tag" field, so exclusions are expressed as negated terms in the query.
        val excluded = tags.filter { it.exclude }.joinToString(" ") { "-\"${it.name}\"" }
        add("query", listOf(text, excluded).filter { it.isNotBlank() }.joinToString(" "))
        add("title", title)
        add("creators", author)
        TagType.entries.forEach { type ->
            add(type.param, tags.filter { it.type == type && !it.exclude }.joinToString(",") { it.name })
        }
        ratings.forEach { p += "work_search[rating_ids][]" to it.id.toString() }
        warnings.forEach { p += "work_search[archive_warning_ids][]" to it.id.toString() }
        categories.forEach { p += "work_search[category_ids][]" to it.id.toString() }
        add("complete", completion.value)
        if (excludeCrossovers) add("crossover", "F")
        if (singleChapterOnly) add("single_chapter", "1")
        add("word_count", wordCount)
        add("revised_at", updatedWithin)
        add("language_id", language)
        add("kudos_count", minKudos.let { if (it.isNotBlank() && it.all(Char::isDigit)) ">$it" else it })
        add("sort_column", sort.value)
        add("sort_direction", if (sortDescending) "desc" else "asc")
        return p
    }
}
