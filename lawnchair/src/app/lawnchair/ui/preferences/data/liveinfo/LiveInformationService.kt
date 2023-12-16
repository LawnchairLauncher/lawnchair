package app.lawnchair.ui.preferences.data.liveinfo

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

interface LiveInformationService {

    @GET("live-information.json")
    suspend fun getLiveInformation(): Response<ResponseBody>
}
