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

private val retrofit = Retrofit.Builder()
    .baseUrl("https://www.startpage.com/")
    .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
    .build()

val startPageService: StartPageService = retrofit.create()

suspend fun getStartPageSuggestions(query: String, max: Int): List<String> = withContext(Dispatchers.IO) {
    if (query.isEmpty() || query.isBlank() || max <= 0) {
        return@withContext emptyList()
    }

    try {
        val response: Response<ResponseBody> = startPageService.getStartPageSuggestions(
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

interface StartPageService {
    @GET("suggestions")
    suspend fun getStartPageSuggestions(
        @Query("q") query: String,
        @Query("segment") segment: String,
        @Query("partner") partner: String,
        @Query("format") format: String,
    ): Response<ResponseBody>
}
