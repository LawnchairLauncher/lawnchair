package app.lawnchair.search.data.suggestion

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface StartPageService {
    @GET("suggestions")
    suspend fun getStartPageSuggestions(
        @Query("q") query: String,
        @Query("segment") segment: String,
        @Query("partner") partner: String,
        @Query("format") format: String,
    ): Response<ResponseBody>
}
