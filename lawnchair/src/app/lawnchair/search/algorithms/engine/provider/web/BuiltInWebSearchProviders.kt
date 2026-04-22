package app.lawnchair.search.algorithms.engine.provider.web

import android.net.Uri
import android.util.Log
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R
import java.lang.reflect.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import org.json.JSONArray
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Provides web search suggestions from Google.
 */
object GoogleWebSearchProvider : WebSearchProvider {

    override val label = R.string.search_provider_google

    override val iconRes = R.drawable.ic_super_g_color

    override val id: String = "google"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.google.com/")
            // Note: We use a custom string converter because Google's response is not valid JSON.
            .addConverterFactory(StringConverterFactory.create())
            .build()
    }

    private val service: GoogleService by lazy {
        retrofit.create(GoogleService::class.java)
    }

    /**
     * The Retrofit service interface for Google suggestions.
     */
    private interface GoogleService {
        @GET("complete/search")
        suspend fun getSuggestions(
            @Query("client") client: String = "firefox",
            @Query("q") query: String,
        ): Response<String>
    }

    override fun getSuggestions(query: String): Flow<List<String>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            val encodedQuery = Uri.encode(query)
            val response = service.getSuggestions(query = encodedQuery)

            if (response.isSuccessful) {
                val responseBody = response.body() ?: ""
                val suggestions = parseGoogleResponse(responseBody)
                emit(suggestions)
            } else {
                Log.w(TAG, "Failed to retrieve suggestions: ${response.code()}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during suggestion retrieval", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getSearchUrl(query: String): String {
        val encodedQuery = Uri.encode(query)
        return "https://google.com/search?q=$encodedQuery"
    }

    /**
     * Parses the unusual, non-JSON format that the Google suggest API returns.
     * Example: `["query", ["suggestion1", "suggestion2"], [], {"some_metadata": "..."}]`
     */
    private fun parseGoogleResponse(responseBody: String): List<String> {
        return try {
            val jsonArray = JSONArray(responseBody)
            val suggestionsArray = jsonArray.getJSONArray(1)
            (0 until suggestionsArray.length()).map { suggestionsArray.getString(it) }
        } catch (e: Exception) {
            Log.e("GoogleWebSearchProvider", "Failed to parse Google response", e)
            emptyList()
        }
    }

    private const val TAG = "GoogleWebSearchProvider"

    override fun toString(): String = id
}

/**
 * Provides web search suggestions from DuckDuckGo.
 */
object DuckDuckGoWebSearchProvider : WebSearchProvider {
    override val label = R.string.search_provider_duckduckgo

    override val iconRes = R.drawable.ic_duckduckgo

    override val id: String = "duckduckgo"

    // 1. Use the standard kotlinx.serialization converter since DDG's API is clean.
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://ac.duckduckgo.com/")
            .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private val service: DuckDuckGoService by lazy {
        retrofit.create(DuckDuckGoService::class.java)
    }

    /**
     * The Retrofit service interface for DuckDuckGo suggestions.
     * Note: DDG returns a simple JSON array, not a structured object,
     * so we parse it manually from a ResponseBody.
     */
    private interface DuckDuckGoService {
        @GET("ac/")
        suspend fun getSuggestions(
            @Query("q") query: String,
            @Query("type") type: String = "json",
        ): Response<ResponseBody>
    }

    override fun getSuggestions(query: String): Flow<List<String>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            // 2. Call the Retrofit service.
            val encodedQuery = Uri.encode(query)
            val response = service.getSuggestions(query = encodedQuery)

            if (response.isSuccessful) {
                val responseBody = response.body()?.string() ?: ""
                val suggestions = parseDuckDuckGoResponse(responseBody)
                emit(suggestions)
            } else {
                Log.w(TAG, "Failed to retrieve suggestions: ${response.code()}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during suggestion retrieval", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getSearchUrl(query: String): String {
        val encodedQuery = Uri.encode(query)
        return "https://duckduckgo.com/?q=$encodedQuery"
    }

    /**
     * Parses the DDG JSON response.
     * Example: `[{"phrase":"suggestion1"},{"phrase":"suggestion2"}]`
     */
    private fun parseDuckDuckGoResponse(responseBody: String): List<String> {
        return try {
            val jsonElement = Json.parseToJsonElement(responseBody)
            jsonElement.jsonArray.map { it.jsonObject["phrase"]!!.jsonPrimitive.content }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse DDG response", e)
            emptyList()
        }
    }

    private const val TAG = "DuckDuckGoWebSearchProvider"

    override fun toString(): String = id
}

/**
 * Provides web search suggestions from StartPage.
 */
object StartPageWebSearchProvider : WebSearchProvider {
    override val label = R.string.search_provider_startpage

    override val iconRes = R.drawable.ic_startpage

    override val id: String = "startpage"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.startpage.com/")
            .addConverterFactory(StringConverterFactory.create()) // StartPage also returns non-standard JSON
            .build()
    }

    private val service: StartPageService by lazy {
        retrofit.create(StartPageService::class.java)
    }

    /**
     * The Retrofit service interface for StartPage suggestions.
     */
    private interface StartPageService {
        @GET("suggestions")
        suspend fun getSuggestions(
            @Query("q") query: String,
            @Query("segment") segment: String = "startpage.lawnchair", // Identify our app
            @Query("partner") partner: String = "lawnchair",
            @Query("format") format: String = "opensearch",
        ): Response<String>
    }

    override fun getSuggestions(query: String): Flow<List<String>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            val encodedQuery = Uri.encode(query)
            val response = service.getSuggestions(query = encodedQuery)

            if (response.isSuccessful) {
                val responseBody = response.body() ?: ""
                val suggestions = parseStartPageResponse(responseBody)
                emit(suggestions)
            } else {
                Log.w(TAG, "Failed to retrieve suggestions: ${response.code()}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during suggestion retrieval", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getSearchUrl(query: String): String {
        val encodedQuery = Uri.encode(query)
        return "https://www.startpage.com/do/search?query=$encodedQuery&cat=web"
    }

    /**
     * Parses the StartPage OpenSearch JSON response.
     * Example: `["query", ["suggestion1", "suggestion2"]]`
     */
    private fun parseStartPageResponse(responseBody: String): List<String> {
        return try {
            val jsonArray = JSONArray(responseBody)
            val suggestionsArray = jsonArray.getJSONArray(1) // Suggestions are the second element
            (0 until suggestionsArray.length()).map { suggestionsArray.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse StartPage response", e)
            emptyList()
        }
    }

    private const val TAG = "StartPageWebSearchProvider"

    override fun toString(): String = id
}

/**
 * Provides web search suggestions from Kagi.
 */
object KagiWebSearchProvider : WebSearchProvider {
    override val label = R.string.search_provider_kagi

    override val iconRes = R.drawable.ic_kagi

    override val id: String = "kagi"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://kagi.com/")
            // Kagi uses a simple JSON array, but we'll use the string converter for consistency
            // in case their API ever wraps the response.
            .addConverterFactory(StringConverterFactory.create())
            .build()
    }

    private val service: KagiService by lazy {
        retrofit.create(KagiService::class.java)
    }

    /**
     * The Retrofit service interface for Kagi suggestions.
     */
    private interface KagiService {
        @GET("api/autosuggest")
        suspend fun getSuggestions(
            @Query("q") query: String,
        ): Response<String>
    }

    override fun getSuggestions(query: String): Flow<List<String>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            val encodedQuery = Uri.encode(query)
            val response = service.getSuggestions(query = encodedQuery)

            if (response.isSuccessful) {
                val responseBody = response.body() ?: ""
                val suggestions = parseKagiResponse(responseBody)
                emit(suggestions)
            } else {
                Log.w(TAG, "Failed to retrieve suggestions: ${response.code()}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during suggestion retrieval", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getSearchUrl(query: String): String {
        val encodedQuery = Uri.encode(query)
        return "https://kagi.com/search?q=$encodedQuery"
    }

    /**
     * Parses the Kagi JSON response.
     * Example: `["query", ["suggestion1", "suggestion2"]]`
     * Note: This is identical to the StartPage response format.
     */
    private fun parseKagiResponse(responseBody: String): List<String> {
        return try {
            val jsonArray = JSONArray(responseBody)
            val suggestionsArray = jsonArray.getJSONArray(1) // Suggestions are the second element
            (0 until suggestionsArray.length()).map { suggestionsArray.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Kagi response", e)
            emptyList()
        }
    }

    private const val TAG = "KagiWebSearchProvider"

    override fun toString(): String = id
}

private class StringConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (type == String::class.java) {
            return Converter<ResponseBody, String> { value -> value.string() }
        }
        return null
    }

    companion object {
        fun create() = StringConverterFactory()
    }
}
