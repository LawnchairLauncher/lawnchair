package app.lawnchair.search.algorithms.engine.provider.apps

import me.xdrop.fuzzywuzzy.FuzzySearch

internal data class MatchResult(val score: Float, val type: MatchType)
internal enum class MatchType(val priority: Int) {
    EXACT_MATCH(0),
    DIRECT_PREFIX(1),
    INITIALS(2),
    TOKEN_PREFIX_ORDERED(3),
    SUBSTRING(4),
    ALL_TOKENS_PRESENT(5),
    FUZZY(6),
    NO_MATCH(7),
}

internal data class PreparedSearchText(val text: String, val tokens: List<String>, val initials: String) {
    companion object {
        fun from(text: String): PreparedSearchText {
            val normalized = SearchTextNormalizer.normalize(text)
            val tokens = normalized.split(' ').filter { it.isNotEmpty() }
            return PreparedSearchText(normalized, tokens, tokens.joinToString("") { it.take(1) })
        }
    }
}

internal object AppMatcher {
    fun match(appName: String, query: String): MatchResult = match(PreparedSearchText.from(appName), PreparedSearchText.from(query), true)

    fun match(app: PreparedSearchText, query: PreparedSearchText, fuzzy: Boolean): MatchResult {
        val q = query.text
        if (q.isEmpty() || app.text.isEmpty()) return MatchResult(0f, MatchType.NO_MATCH)
        if (app.text == q) return MatchResult(1f, MatchType.EXACT_MATCH)
        if (app.text.startsWith(q)) return MatchResult(.95f, MatchType.DIRECT_PREFIX)
        if (query.tokens.size == 1 && app.initials.startsWith(q)) return MatchResult(.88f, MatchType.INITIALS)
        if (query.tokens.size <= app.tokens.size && query.tokens.indices.all { app.tokens[it].startsWith(query.tokens[it]) }) {
            return MatchResult(.82f, MatchType.TOKEN_PREFIX_ORDERED)
        }
        if (app.text.contains(q)) return MatchResult(.72f, MatchType.SUBSTRING)
        if (query.tokens.all { token -> app.tokens.any { it.startsWith(token) } }) return MatchResult(.68f, MatchType.ALL_TOKENS_PRESENT)
        if (!fuzzy || q.length < 2) return MatchResult(0f, MatchType.NO_MATCH)
        val score = maxOf(FuzzySearch.ratio(app.text, q), app.tokens.maxOfOrNull { FuzzySearch.ratio(it, q) } ?: 0)
        return if (score >= 65) MatchResult(score / 100f, MatchType.FUZZY) else MatchResult(0f, MatchType.NO_MATCH)
    }
}
