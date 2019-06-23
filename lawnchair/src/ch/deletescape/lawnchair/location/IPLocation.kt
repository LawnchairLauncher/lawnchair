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

package ch.deletescape.lawnchair.location

import android.content.Context
import android.location.Location
import android.os.NetworkOnMainThreadException
import android.os.SystemClock
import ch.deletescape.lawnchair.perms.CustomPermissionManager
import ch.deletescape.lawnchair.perms.checkCustomPermission
import ch.deletescape.lawnchair.runOnUiWorkerThread
import ch.deletescape.lawnchair.util.okhttp.OkHttpClientBuilder
import okhttp3.*
import okhttp3.internal.http.promisesBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.TimeUnit

class IPLocation(private val context: Context, private val cacheValidityMs: Long = TimeUnit.MINUTES.toMillis(30)) {
    private val permissionManager = CustomPermissionManager.getInstance(context)
    private val client = OkHttpClientBuilder().build(context)

    private val cacheValid get() = cache != null && timeLast + cacheValidityMs > SystemClock.uptimeMillis()
    private var timeLast = 0L
    private var cache: Result? = null
        set(value) {
            timeLast = SystemClock.uptimeMillis()
            field = value
        }

    fun get(): Result {
        var success = false
        var lat = .0
        var lon = .0

        if (!context.checkCustomPermission(CustomPermissionManager.PERMISSION_IPLOCATE)) {
            return Result(success, lat, lon)
        }

        if (!cacheValid) {
            for (url in URLS) {
                try {
                    val response = client.newCall(getRequest(url)).execute()
                    if (response.isSuccessful && response.body != null) {
                        val json = JSONObject(response.body?.string())
                        lat = json.getDouble("latitude")
                        lon = json.getDouble("longitude")
                        success = true
                        break
                    }
                } catch (e: Exception) {
                    if (e is NetworkOnMainThreadException)
                        throw e
                }
            }

            cache = Result(success, lat, lon)
        }

        return cache ?: Result(success, lat, lon).apply {
            cache = this
        }
    }

    private fun getRequest(url: String) = Request.Builder().url(url).get().build()

    data class Result(val success: Boolean, val lat: Double, val lon: Double)

    companion object {
        private val URLS = arrayOf("https://freegeoip.app/json/",
                "https://geoip-db.com/json/",
                "https://api.iplocate.app/json/")
    }
}