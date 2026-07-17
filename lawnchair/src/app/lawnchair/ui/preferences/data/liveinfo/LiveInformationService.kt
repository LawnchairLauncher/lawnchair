package app.lawnchair.ui.preferences.data.liveinfo

import app.lawnchair.ui.preferences.data.liveinfo.model.LiveInformation
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface LiveInformationService {

    @GET
    suspend fun getLiveInformation(@Url endpoint: String): Response<LiveInformation>
}
