package app.lawnchair.ui.preferences.data.liveinfo

import android.util.Log
import app.lawnchair.ui.preferences.data.liveinfo.model.LiveInformation
import app.lawnchair.util.kotlinxJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

private val retrofit = Retrofit.Builder()
    .baseUrl("https://lawnchair.app/")
    .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
    .build()

val liveInformationService: LiveInformationService = retrofit.create()

suspend fun getLiveInformation(): LiveInformation? = withContext(Dispatchers.IO) {
    try {
        val response: Response<ResponseBody> = liveInformationService.getLiveInformation()

        if (response.isSuccessful) {
            val responseBody = response.body()?.string() ?: return@withContext null

            val liveInformation = Json.decodeFromString<LiveInformation>(responseBody)
            Log.v("LiveInformation", "getLiveInformation: $liveInformation")

            return@withContext liveInformation
        } else {
            Log.d("LiveInformation", "getLiveInformation: response code ${response.code()}")
            return@withContext null
        }
    } catch (e: Exception) {
        Log.e("LiveInformation", "getLiveInformation: Error during news retrieval: ${e.message}")
        return@withContext null
    }
}
