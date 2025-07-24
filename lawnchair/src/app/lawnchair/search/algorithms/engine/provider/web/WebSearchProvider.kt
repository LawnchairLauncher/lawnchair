package app.lawnchair.search.algorithms.engine.provider.web

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.Flow

/**
 * A clean interface for any provider that can fetch web search suggestions.
 */
interface WebSearchProvider {

    /**
     * Human-readable label used by the preference UI
     */
    @get:StringRes
    val label: Int

    /**
     * Icon resource used by the drawer search bar
     */
    @get:DrawableRes
    val iconRes: Int

    /**
     * A unique, stable ID for this provider (e.g., "google", "duckduckgo", "custom").
     */
    val id: String

    fun configure(context: Context): WebSearchProvider = this

    /**
     * Fetches search suggestions for the given query.
     *
     * @param query The user's search query.
     * @return A Flow that emits a list of suggestion strings.
     */
    fun getSuggestions(query: String): Flow<List<String>>

    /**
     * Constructs the final search URL for a given query.
     *
     * @param query The user's search query.
     * @return The fully-formed URL string to open in a browser.
     */
    fun getSearchUrl(query: String): String

    override fun toString(): String

    companion object WebSearchProviderCompanion {
        fun values(): List<WebSearchProvider> = listOf(
            GoogleWebSearchProvider,
            DuckDuckGoWebSearchProvider,
            StartPageWebSearchProvider,
            KagiWebSearchProvider,
            CustomWebSearchProvider,
        )

        fun fromString(value: String): WebSearchProvider = when (value) {
            "google" -> GoogleWebSearchProvider
            "duckduckgo" -> DuckDuckGoWebSearchProvider
            "startpage" -> StartPageWebSearchProvider
            "kagi" -> KagiWebSearchProvider
            "custom" -> CustomWebSearchProvider
            else -> GoogleWebSearchProvider
        }
    }
}
