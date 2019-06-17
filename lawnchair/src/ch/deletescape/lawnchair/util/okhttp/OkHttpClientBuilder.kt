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

package ch.deletescape.lawnchair.util.okhttp

import android.content.Context
import ch.deletescape.lawnchair.lawnchairPrefs
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class OkHttpClientBuilder {
    private val builder = OkHttpClient.Builder()
    private val queryParams = mutableMapOf<String, String>()

    fun addQueryParam(param: Pair<String, String>): OkHttpClientBuilder {
        queryParams.putAll(arrayOf(param))
        return this
    }

    fun build(context: Context?): OkHttpClient {
        if (queryParams.isNotEmpty()) {
            builder.addInterceptor {
                val urlBuilder = it.request().url.newBuilder()
                for (param in queryParams) {
                    urlBuilder.addQueryParameter(param.key, param.value)
                }
                it.proceed(it.request().newBuilder().url(urlBuilder.build()).build())
            }
        }
        builder.addInterceptor(HttpLoggingInterceptor().apply {
            level = if (context?.lawnchairPrefs?.debugOkHttp == true) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        })
        return builder.build()
    }
}