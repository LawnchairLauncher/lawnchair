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

package ch.deletescape.lawnchair.smartspace.accu

import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuLocationGSon
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface AccuSearchRetrofitService {
    @GET("locations/v1/cities/autocomplete.json")
    fun getAutoComplete(@Query("q") query: String, @Query("language") language: String): Call<List<AccuLocationGSon>>

    @GET("locations/v1/cities/geoposition/search.json")
    fun getGeoPosition(@Query("q") query: String, @Query("language") language: String): Call<AccuLocationGSon>

    @GET("locations/v1/search")
    fun search(@Query("q") query: String, @Query("language") language: String): Call<List<AccuLocationGSon>>
}