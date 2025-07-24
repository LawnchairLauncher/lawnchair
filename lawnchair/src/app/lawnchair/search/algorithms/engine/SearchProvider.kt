package app.lawnchair.search.algorithms.engine

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * The contract for a single, self-contained source of search results.
 * Providers are stateless. All required context is passed into the search method.
 */
interface SearchProvider {

    /**
     * A unique identifier for the provider.
     */
    val id: String

    /**
     * Searches for results based on the given query.
     *
     * This function should be main-safe and handle its own threading.
     *
     * @param context The application context.
     * @param query The user's search query.
     * @return A Flow that emits a list of [SearchResult]s.
     */
    fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>>
}
