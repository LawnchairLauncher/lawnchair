/*
 *     Copyright (c) 2017-2019 the Lawnchair team
 *     Copyright (c)  2019 oldosfan (would)
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.smartspace.weathercom

import android.text.TextUtils
import ch.deletescape.lawnchair.util.okhttp.OkHttpClientBuilder
import com.android.launcher3.LauncherAppState
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object WeatherComRetrofitServiceFactory {
    private var API_KEY: Pair<String, String> = Pair("apiKey", Constants.WeatherComConstants.WEATHER_COM_API_KEY)
    private val BASE_URL = "https://api.weather.com"
    private var okHttpClient: OkHttpClient? = null

    val weatherComWeatherRetrofitService by lazy {
        getRetrofitService(WeatherComWeatherRetrofitService::class.java)
    }

    fun setApiKey(apiKey: String) {
        if (!TextUtils.isEmpty(apiKey)) {
            API_KEY = Pair("apiKey", apiKey)
        }
    }

    private fun <T> getRetrofitService(serviceClass: Class<T>): T {
        val client = buildOkHttpClient()
        return Retrofit.Builder().baseUrl(BASE_URL).addConverterFactory(GsonConverterFactory.create()).client(client).build().create(serviceClass)
    }

    private fun buildOkHttpClient(): OkHttpClient? {
        if (okHttpClient == null) {
            synchronized(WeatherComRetrofitServiceFactory::class.java) {
                if (okHttpClient == null) {
                    okHttpClient = OkHttpClientBuilder().addQueryParam(API_KEY).build(LauncherAppState.getInstanceNoCreate()?.context)
                }
            }
        }
        return okHttpClient
    }
}