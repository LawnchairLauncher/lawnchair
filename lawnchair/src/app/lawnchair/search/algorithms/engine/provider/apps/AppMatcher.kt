package app.lawnchair.search.algorithms.engine.provider.apps

import java.util.Locale
import kotlin.math.max
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

internal object AppMatcher {
    private const val FUZZY_SCORE_CUTOFF = 65

    fun match(appName: String, query: String): MatchResult {
        val app = appName.lowercase(Locale.getDefault())

        // Rule 0: Exact Match
        if (app == query) return MatchResult(1.0f, MatchType.EXACT_MATCH)

        // Rule 1: Direct Prefix
        if (app.startsWith(query)) {
            val ratio = query.length.toFloat() / app.length
            val score = (0.9f + 0.05f * ratio).coerceAtMost(0.95f)
            return MatchResult(score, MatchType.DIRECT_PREFIX)
        }

        // Tokenize once and reuse
        val tokens by lazy { app.split(" ").filter { it.isNotBlank() } }
        val qTokens by lazy { query.split(" ").filter { it.isNotBlank() } }

        // Rule 2: Initials (for single-token queries)
        if (query.none { it.isWhitespace() }) {
            val initials by lazy { tokens.joinToString("") { it.first().toString() } }
            if (initials.isNotEmpty() && initials.startsWith(query)) {
                return MatchResult(0.88f, MatchType.INITIALS)
            }
        }

        // Rule 3: Token Prefix (Ordered)
        if (qTokens.isNotEmpty() && qTokens.size <= tokens.size) {
            if (qTokens.indices.all { i -> tokens[i].startsWith(qTokens[i]) }) {
                return MatchResult(0.82f, MatchType.TOKEN_PREFIX_ORDERED)
            }
        }

        // Rule 4: Substring
        if (app.contains(query)) return MatchResult(0.72f, MatchType.SUBSTRING)

        // Rule 5: All Tokens Present (Order-agnostic)
        if (qTokens.isNotEmpty() && qTokens.all { qTok -> tokens.any { it.startsWith(qTok) } }) {
            return MatchResult(0.68f, MatchType.ALL_TOKENS_PRESENT)
        }

        // Rule 6: Fuzzy Search
        val fuzzyWhole = FuzzySearch.ratio(app, query)
        // Avoid re-calculating max if no tokens exist
        val fuzzyToken = if (tokens.isEmpty()) 0 else tokens.maxOfOrNull { FuzzySearch.ratio(it, query) } ?: 0
        val fuzzyScore = max(fuzzyWhole, fuzzyToken)

        if (fuzzyScore >= FUZZY_SCORE_CUTOFF) {
            val normalized = 0.5f + ((fuzzyScore - FUZZY_SCORE_CUTOFF) / (100f - FUZZY_SCORE_CUTOFF)) * 0.15f
            return MatchResult(normalized, MatchType.FUZZY)
        }

        return MatchResult(0f, MatchType.NO_MATCH)
    }
}
