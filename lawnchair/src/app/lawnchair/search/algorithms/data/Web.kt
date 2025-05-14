package app.lawnchair.search.algorithms.data

import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.IOException
import org.json.JSONArray
import org.json.JSONException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * A class to get the current web search provider
 */
sealed class WebSearchProvider {

    /**
     * Human-readable label used by the preference UI
     */
    @get:StringRes
    abstract val label: Int

    /**
     * Icon resource used by the drawer search bar
     */
    @get:DrawableRes
    abstract val iconRes: Int

    /**
     * Base url used for mapping
     */
    abstract val baseUrl: String

    /**
     * [Retrofit] instance used for searching.
     */
    protected val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /**
     * The search service to use.
     */
    protected abstract val service: GenericSearchService

    /**
     * Suspending function to get the list of suggestions from the current suggestion
     * @param query The input text
     * @param maxSuggestions The maximum number of items
     * @return The list of suggestions
     */
    abstract suspend fun getSuggestions(query: String, maxSuggestions: Int): List<String>

    /**
     * Function to get the search URL for the current provider
     * @param query The input text
     */
    abstract fun getSearchUrl(query: String): String

    companion object {
        fun fromString(value: String): WebSearchProvider = when (value) {
            "google" -> Google
            "duckduckgo" -> DuckDuckGo
            "kagi" -> Kagi
            "custom" -> CustomWebSearchProvider
            else -> StartPage
        }

        /**
         * The list of available web search providers
         */
        fun values() = listOf(
            Google,
            DuckDuckGo,
            Kagi,
            StartPage,
            CustomWebSearchProvider,
        )
    }
}

/**
 * A custom search provider defined by user-provided URLs.
 *
 * IMPORTANT ARCHITECTURAL NOTE:
 *
 * The WebSearchProvider interface and the `fromString` factory method are designed
 * for providers with largely static configurations that can be retrieved solely
 * from a string ID.
 *
 * The Custom provider, however, requires dynamic, runtime-specific state:
 * user-defined URLs (from preferences) and an OkHttpClient instance (typically a global singleton).
 * This state cannot be injected by the `fromString` factory when the object is created.
 *
 * To work around this limitation within the existing WebSearchProvider hierarchy:
 *
 * 1. The standard `getSuggestions(query, maxSuggestions)` and `getSearchUrl(query)`
 *    methods on the `Custom` data object are deliberately made to throw errors.
 *    They should *not* be called directly.
 * 2. Dedicated methods, `getCustomSuggestions()` and `getCustomSearchUrl()`, are added
 *    to the `Custom` data object. These methods accept the necessary dynamic state
 *    (OkHttpClient and URL templates) as parameters from the caller.
 *
 * This means code using the WebSearchProvider *must* explicitly check if the
 * provider obtained via `fromString` is `Custom` and call these custom methods,
 * providing the required state.
 *
 * Example:
 * ```kotlin
 * val provider = WebSearchProvider.fromString(providerId)
 * if (provider is Custom) {
 *     // Get URL template and OkHttpClient from preferences/app context
 *     val suggestions = provider.getCustomSuggestions(query, max, okHttpClient, customSuggestionsUrl)
 * } else {
 *     // Call the standard method for built-in providers
 *     val suggestions = provider.getSuggestions(query, max)
 * }
 * ```
 *
 * This pragmatic approach was chosen to integrate the feature with minimal
 * disruption to other parts of the algorithm/codebase, given the constraints
 * of the existing preference system's deserialization and a planned
 * future refactor of the search provider architecture.
 */
data object CustomWebSearchProvider : WebSearchProvider() {
    override val label = R.string.search_provider_custom

    override val iconRes = R.drawable.ic_search

    override val baseUrl = ""

    override val service: GenericSearchService
        by lazy<GenericSearchService> { error("Custom does not use a Retrofit service.") }

    override suspend fun getSuggestions(query: String, maxSuggestions: Int): List<String> = error { "Use getCustomSuggestions() instead." }
    override fun getSearchUrl(query: String) = error { "Use getCustomSearchUrl() instead. " }

    suspend fun getCustomSuggestions(
        query: String,
        maxSuggestions: Int,
        okHttpClient: OkHttpClient,
        suggestionsUrlTemplate: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val encodedQuery = Uri.encode(query)
        val url = suggestionsUrlTemplate.replace("%s", encodedQuery)

        try {
            val request = Request.Builder()
                .url(url)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@withContext emptyList()

                    return@withContext try {
                        val jsonArray = JSONArray(responseBody)
                        val suggestionsArray = jsonArray.optJSONArray(1)

                        suggestionsArray?.let { array ->
                            (0 until array.length()).take(maxSuggestions).map { array.getString(it) }
                        } ?: emptyList()
                    } catch (e: JSONException) {
                        Log.e("CustomSearchProvider", "Error parsing JSON response from $url: ${e.message}", e)
                        emptyList()
                    }
                } else {
                    Log.w(
                        "CustomSearchProvider",
                        "Failed to retrieve suggestions from $url: ${response.code} - ${response.message}",
                    )
                    return@withContext emptyList()
                }
            }
        } catch (e: IOException) { // Catch network errors
            Log.e("CustomSearchProvider", "Network error retrieving suggestions from $url: ${e.message}", e)
            return@withContext emptyList()
        } catch (e: Exception) { // Catch any other unexpected errors
            Log.e("CustomSearchProvider", "Error during custom suggestion retrieval from $url: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    fun getCustomSearchUrl(
        query: String,
        urlTemplate: String,
    ): String {
        val encodedQuery = Uri.encode(query)
        return urlTemplate.replace("%s", encodedQuery)
    }

    override fun toString() = "custom"
}

/**
 * A popular search engine
 */
data object Google : WebSearchProvider() {
    override val label = R.string.search_provider_google

    override val iconRes = R.drawable.ic_super_g_color

    override val baseUrl = "https://www.google.com/"

    override val service: GoogleService by lazy { retrofit.create() }

    override suspend fun getSuggestions(query: String, maxSuggestions: Int): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank() || maxSuggestions <= 0) {
            return@withContext emptyList()
        }

        try {
            val encodedQuery = Uri.encode(query)
            val response: Response<ResponseBody> = service.getSuggestions(query = encodedQuery)

            if (response.isSuccessful) {
                val responseBody = response.body()?.string() ?: return@withContext emptyList()

                val jsonPayload = Regex("\\((.*)\\)").find(responseBody)?.groupValues?.get(1)

                // Manual JSON parsing
                val jsonArray = JSONArray(jsonPayload)
                val suggestionsArray = jsonArray.getJSONArray(1) // Get the suggestions array
                val suggestionsList = mutableListOf<String>()
                for (i in 0 until suggestionsArray.length().coerceAtMost(maxSuggestions)) {
                    suggestionsList.add(suggestionsArray.getString(i))
                }
                return@withContext suggestionsList
            } else {
                Log.w(
                    "GoogleSearchProvider",
                    "Failed to retrieve suggestions: ${response.code()}",
                )
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e("GoogleSearchProvider", "Error during suggestion retrieval: ${e.message}")
            return@withContext emptyList()
        }
    }

    override fun getSearchUrl(query: String) = "https://google.com/search?q=$query"

    override fun toString() = "google"
}

/**
 * A Google-like search engine.
 */
data object StartPage : WebSearchProvider() {
    override val label = R.string.search_provider_startpage

    override val iconRes = R.drawable.ic_startpage

    override val baseUrl = "https://www.startpage.com"

    override val service: StartPageService by lazy { retrofit.create() }

    override suspend fun getSuggestions(query: String, maxSuggestions: Int): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank() || maxSuggestions <= 0) {
            return@withContext emptyList()
        }

        try {
            val encodedQuery = Uri.encode(query)
            val response: Response<ResponseBody> = service.getSuggestions(
                query = encodedQuery,
                segment = "startpage.lawnchair",
                partner = "lawnchair",
                format = "opensearch",
            )

            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                return@withContext JSONArray(responseBody).optJSONArray(1)?.let { array ->
                    (0 until array.length()).take(maxSuggestions).map { array.getString(it) }
                } ?: emptyList()
            } else {
                Log.w(
                    "StartPageSearchProvidr",
                    "Failed to retrieve suggestions: ${response.code()}",
                )
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e("StartPageSearchProvider", "Error during suggestion retrieval: ${e.message}")
            return@withContext emptyList()
        }
    }

    override fun getSearchUrl(query: String) = "https://www.startpage.com/do/search?segment=startpage.lawnchair&query=$query&cat=web"

    override fun toString() = "startpage"
}

/**
 * An fast, alternative engine to Google.
 */
data object DuckDuckGo : WebSearchProvider() {
    override val label = R.string.search_provider_duckduckgo

    override val iconRes = R.drawable.ic_duckduckgo

    override val baseUrl = "https://ac.duckduckgo.com/"

    override val service: DuckDuckGoService by lazy { retrofit.create() }

    override suspend fun getSuggestions(query: String, maxSuggestions: Int): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank() || maxSuggestions <= 0) {
            return@withContext emptyList()
        }

        try {
            val encodedQuery = Uri.encode(query)
            val response: Response<ResponseBody> = service.getSuggestions(query = encodedQuery)

            if (response.isSuccessful) {
                val responseBody = response.body()?.string() ?: return@withContext emptyList()

                val jsonArray = JSONArray(responseBody)
                val suggestionsArray =
                    jsonArray.optJSONArray(1) ?: return@withContext emptyList()

                return@withContext (
                    0 until suggestionsArray.length()
                        .coerceAtMost(maxSuggestions)
                    )
                    .map { suggestionsArray.getString(it) }
            } else {
                Log.w(
                    "DuckDuckGoSearchProvider",
                    "Failed to retrieve suggestions: ${response.code()}",
                )
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e("DuckDuckGoSearchProvider", "Error during suggestion retrieval", e)
            return@withContext emptyList()
        }
    }

    override fun getSearchUrl(query: String) = "https://duckduckgo.com/search?q=$query"

    override fun toString() = "duckduckgo"
}

/**
 * Paid, ad-free search engine.
 */
data object Kagi : WebSearchProvider() {
    override var label = R.string.search_provider_kagi

    override val iconRes = R.drawable.ic_kagi

    override val baseUrl = "https://kagi.com/"

    override val service: KagiService by lazy { retrofit.create() }

    override suspend fun getSuggestions(query: String, maxSuggestions: Int): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank() || maxSuggestions <= 0) {
            return@withContext emptyList()
        }

        try {
            val encodedQuery = Uri.encode(query)
            val response: Response<ResponseBody> = service.getSuggestions(query = encodedQuery)

            if (response.isSuccessful) {
                val responseBody = response.body()?.string() ?: return@withContext emptyList()

                val jsonArray = JSONArray(responseBody)
                val suggestionsArray =
                    jsonArray.optJSONArray(1) ?: return@withContext emptyList()

                return@withContext (
                    0 until suggestionsArray.length()
                        .coerceAtMost(maxSuggestions)
                    )
                    .map { suggestionsArray.getString(it) }
            } else {
                Log.w(
                    "KagiSearchProvider",
                    "Failed to retrieve suggestions: ${response.code()}",
                )
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e("KagiSearchProvider", "Error during suggestion retrieval", e)
            return@withContext emptyList()
        }
    }

    override fun getSearchUrl(query: String) = "https://kagi.com/search?q=$query"

    override fun toString() = "kagi"
}

/**
 * Provides an interface for getting search suggestions from the web.
 */
interface GenericSearchService

/**
 * Web suggestions for [WebSearchProvider.Google]
 */
interface GoogleService : GenericSearchService {
    @GET("complete/search")
    suspend fun getSuggestions(
        @Query("client") client: String = "firefox",
        @Query("q") query: String,
        @Query("callback") callback: String = "json",
    ): Response<ResponseBody>
}

/**
 * Web suggestions for [WebSearchProvider.StartPage].
 */
interface StartPageService : GenericSearchService {
    @GET("suggestions")
    suspend fun getSuggestions(
        @Query("q") query: String,
        @Query("segment") segment: String,
        @Query("partner") partner: String,
        @Query("format") format: String,
    ): Response<ResponseBody>
}

/**
 * Web suggestions for [WebSearchProvider.DuckDuckGo].
 */
interface DuckDuckGoService : GenericSearchService {
    @GET("ac/")
    suspend fun getSuggestions(
        @Query("q") query: String,
        @Query("type") type: String = "list",
        @Query("callback") callback: String = "jsonCallback",
    ): Response<ResponseBody>
}

/**
 * Web suggestions for [WebSearchProvider.Kagi].
 */
interface KagiService : GenericSearchService {
    @GET("api/autosuggest")
    suspend fun getSuggestions(
        @Query("q") query: String,
    ): Response<ResponseBody>
}
