package app.lawnchair.ui.preferences.data.liveinfo

import android.util.Log
import app.lawnchair.ui.preferences.data.liveinfo.model.LiveInformation
import app.lawnchair.util.kotlinxJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

private const val TAG = "LiveInformationRequest"

private val retrofit = Retrofit.Builder()
    .baseUrl("https://lawnchair.app/")
    .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
    .build()

val liveInformationService: LiveInformationService = retrofit.create()

suspend fun getLiveInformation(endpoint: String): LiveInformation? = withContext(Dispatchers.IO) {
    try {
        val parsedEndpoint = endpoint.toHttpUrlOrNull()
        if (parsedEndpoint == null || parsedEndpoint.scheme !in setOf("http", "https")) {
            Log.w(TAG, "getLiveInformation: Invalid endpoint")
            return@withContext null
        }

        val response: Response<LiveInformation> = liveInformationService.getLiveInformation(endpoint)

        if (response.isSuccessful) {
            val liveInformation = response.body() ?: return@withContext null
            Log.v(TAG, "getLiveInformation: $liveInformation")

            return@withContext liveInformation
        } else {
            Log.d(TAG, "getLiveInformation: response code ${response.code()}")
            return@withContext null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "getLiveInformation: Error during news retrieval: ${e.message}")
        return@withContext null
    }
}
