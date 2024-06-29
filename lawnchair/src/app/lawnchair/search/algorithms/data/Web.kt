package app.lawnchair.search.algorithms.data

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import org.json.JSONArray
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

    companion object {
        fun fromString(value: String): WebSearchProvider = when (value) {
            "google" -> Google
            "duckduckgo" -> DuckDuckGo
            else -> StartPage
        }

        /**
         * The list of available web search providers
         */
        fun values() = listOf(
            Google,
            StartPage,
            DuckDuckGo,
        )
    }
}

/**
 * A popular search engine
 */
data object Google : WebSearchProvider() {
    override val label = R.string.search_provider_google

    override val iconRes = R.drawable.ic_super_g_color

    override val baseUrl = "https://www.google.com/"

    override val service: GoogleService
        get() = retrofit.create()

    override suspend fun getSuggestions(query: String, maxSuggestions: Int): List<String> =
        withContext(Dispatchers.IO) {
            if (query.isBlank() || maxSuggestions <= 0) {
                return@withContext emptyList()
            }

            try {
                val response: Response<ResponseBody> = service.getSuggestions(query = query)

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

    override fun toString() = "google"
}

/**
 * A Google-like search engine.
 */
data object StartPage : WebSearchProvider() {
    override val label = R.string.search_provider_startpage

    override val iconRes = R.drawable.ic_startpage

    override val baseUrl = "https://www.startpage.com"

    override val service: StartPageService = retrofit.create()

    override suspend fun getSuggestions(query: String, maxSuggestions: Int): List<String> =
        withContext(Dispatchers.IO) {
            if (query.isBlank() || maxSuggestions <= 0) {
                return@withContext emptyList()
            }

            try {
                val response: Response<ResponseBody> = service.getSuggestions(
                    query = query,
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

    override suspend fun getSuggestions(query: String, maxSuggestions: Int): List<String> =
        withContext(Dispatchers.IO) {
            if (query.isBlank() || maxSuggestions <= 0) {
                return@withContext emptyList()
            }

            try {
                val response: Response<ResponseBody> = service.getSuggestions(query = query)

                if (response.isSuccessful) {
                    val responseBody = response.body()?.string() ?: return@withContext emptyList()

                    val jsonArray = JSONArray(responseBody)
                    val suggestionsArray =
                        jsonArray.optJSONArray(1) ?: return@withContext emptyList()

                    return@withContext (0 until suggestionsArray.length()
                        .coerceAtMost(maxSuggestions))
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

    override fun toString() = "duckduckgo"
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
