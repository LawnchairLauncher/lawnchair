package app.lawnchair.bugreport

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.POST

interface KatbinService {

    @POST("api/paste")
    suspend fun upload(@Body body: KatbinUploadBody): KatbinUploadResult

    companion object {
        fun create(): KatbinService = Retrofit.Builder()
            .baseUrl("https://katb.in/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create()
    }
}

data class KatbinUploadBody(
    val paste: KatbinPaste
)

data class KatbinPaste(
    val content: String
)

data class KatbinUploadResult(
    val id: String
)
