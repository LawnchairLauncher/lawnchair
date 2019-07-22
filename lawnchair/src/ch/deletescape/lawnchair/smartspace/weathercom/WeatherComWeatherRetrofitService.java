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

package ch.deletescape.lawnchair.smartspace.weathercom;

import ch.deletescape.lawnchair.smartspace.weathercom.models.SunV1CurrentConditionsResponse;
import ch.deletescape.lawnchair.smartspace.weathercom.models.SunV3LocationSearchResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface WeatherComWeatherRetrofitService {
    @GET("v1/geocode/{latitude}/{longitude}/observations.json")
    Call<SunV1CurrentConditionsResponse> getCurrentConditions(
            @Path(value = "latitude") double latitude, @Path(value = "longitude") double longitude);

    @GET("v3/location/search")
    Call<SunV3LocationSearchResponse> searchLocationByName(
            @Query(value = "query") String query, @Query(value = "locationType") String locationType, @Query(value = "language") String locale, @Query(value = "format") String format) ;

}
