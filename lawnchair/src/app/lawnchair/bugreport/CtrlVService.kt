package app.lawnchair.bugreport

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface CtrlVService {

    @FormUrlEncoded
    @POST("api")
    suspend fun upload(
        @Field("title") title: String,
        @Field("content") content: String
    ): UploadResult

    companion object {
        fun create(): CtrlVService = Retrofit.Builder()
            .baseUrl("https://api.ctrl-v.app/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create()
    }
}

data class UploadResult(
    val hash: String
)
