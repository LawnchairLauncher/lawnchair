package app.lawnchair.search.algorithms.data

import android.util.Log
import app.lawnchair.util.kotlinxJson
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

// TODO: Create preferences UI

/**
 * A class to get the current web search provider
 */
sealed class WebSearchProvider {

    /**
     * [Retrofit] instance used for searching.
     */
    abstract val retrofit: Retrofit

    /**
     * The search service to use.
     */
    abstract val service: GenericSearchService

    /**
     * Suspending function to get the list of suggestions from the current suggestion
     * @param query The input text
     * @param max The maximum number of items
     * @return The list of suggestions
     */
    abstract suspend fun getSuggestions(query: String, max: Int): List<String>

    data object Google : WebSearchProvider() {
        override val retrofit: Retrofit
            get() = Retrofit.Builder()
                .baseUrl("https://www.google.com/")
                .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
                .build()

        override val service: GoogleService
            get() = retrofit.create()

        override suspend fun getSuggestions(query: String, max: Int): List<String> = withContext(Dispatchers.IO) {
            if (query.isEmpty() || query.isBlank() || max <= 0) {
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
                    for (i in 0 until suggestionsArray.length().coerceAtMost(max)) {
                        suggestionsList.add(suggestionsArray.getString(i))
                    }
                    return@withContext suggestionsList
                } else {
                    Log.d("Failed to retrieve suggestions", ": ${response.code()}")
                    return@withContext emptyList()
                }
            } catch (e: Exception) {
                Log.e("Exception", "Error during suggestion retrieval: ${e.message}")
                return@withContext emptyList()
            }
        }
    }

    /**
     * A Google-like search engine.
     */
    data object StartPage : WebSearchProvider() {
        override val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://www.startpage.com/")
            .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
            .build()

        override val service: StartPageService = retrofit.create()

        override suspend fun getSuggestions(query: String, max: Int): List<String> = withContext(Dispatchers.IO) {
            if (query.isEmpty() || query.isBlank() || max <= 0) {
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
                        (0 until array.length()).take(max).map { array.getString(it) }
                    } ?: emptyList()
                } else {
                    Log.d("Failed to retrieve suggestions", ": ${response.code()}")
                    return@withContext emptyList()
                }
            } catch (e: Exception) {
                Log.e("Exception", "Error during suggestion retrieval: ${e.message}")
                return@withContext emptyList()
            }
        }

        override fun toString() = "startpage"
    }

    companion object {
        fun fromString(value: String): WebSearchProvider = when (value) {
            "google" -> Google
            else -> StartPage
        }

        fun values() = listOf(
            Google,
            StartPage,
        )
    }
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
        @Query("callback") callback: String = "json"
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
