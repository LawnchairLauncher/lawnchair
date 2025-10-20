package app.lawnchair.search.algorithms.engine.provider.apps

import java.util.Locale
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

/**
 * A utility object for matching a search query against an application's name.
 *
 * This object provides a prioritized, rule-based matching algorithm to determine how well a
 * given query string matches an app's name. The matching process is executed in a specific
 * order of rules, from the most to the least specific. The first rule that successfully
 * matches returns a [MatchResult] containing a relevance score and the type of match found.
 *
 * The matching rules are as follows, in order of priority:
 * 1. **Exact Match:** The query is identical to the app name.
 * 2. **Direct Prefix:** The app name starts with the query.
 * 3. **Initials:** The query matches the beginning of the sequence of initials from the app name's tokens (e.g., "G" or "GM" for "Google Maps").
 * 4. **Token Prefix (Ordered):** Each token in the query is a prefix of the corresponding token in the app name (e.g., "goo map" for "Google Maps").
 * 5. **Substring:** The query appears as a substring within the app name.
 * 6. **All Tokens Present:** All tokens from the query are present as prefixes in the app name's tokens, regardless of order.
 * 7. **Fuzzy Match:** A fuzzy string matching algorithm finds a similarity score above a certain cutoff.
 *
 * If none of these rules produce a match, a result indicating no match is returned.
 */
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
        val tokens = app.split(Regex("\\s+")).filter { it.isNotBlank() }
        val qTokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }

        // Rule 2: Initials (for single-token queries)
        if (query.none { it.isWhitespace() }) {
            val initials = tokens.joinToString("") { it.first().toString() }
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
        val fuzzyScore = maxOf(fuzzyWhole, fuzzyToken)

        if (fuzzyScore >= FUZZY_SCORE_CUTOFF) {
            val normalized = 0.5f + ((fuzzyScore - FUZZY_SCORE_CUTOFF) / (100f - FUZZY_SCORE_CUTOFF)) * 0.15f
            return MatchResult(normalized, MatchType.FUZZY)
        }

        return MatchResult(0f, MatchType.NO_MATCH)
    }
}
