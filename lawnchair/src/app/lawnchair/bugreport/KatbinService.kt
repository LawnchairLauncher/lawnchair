package app.lawnchair.bugreport

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.POST

interface KatbinService {

    @POST("api/paste")
    suspend fun upload(@Body body: KatbinUploadBody): KatbinUploadResult

    companion object {
        fun create(): KatbinService = Retrofit.Builder()
            .baseUrl("https://katb.in/")
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }
}

@Serializable
data class KatbinUploadBody(
    val paste: KatbinPaste
)

@Serializable
data class KatbinPaste(
    val content: String
)

@Serializable
data class KatbinUploadResult(
    val id: String
)
