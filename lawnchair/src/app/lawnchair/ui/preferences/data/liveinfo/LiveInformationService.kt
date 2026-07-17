package app.lawnchair.ui.preferences.data.liveinfo

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface LiveInformationService {

    @GET
    suspend fun getLiveInformation(@Url endpoint: String): Response<ResponseBody>
}
