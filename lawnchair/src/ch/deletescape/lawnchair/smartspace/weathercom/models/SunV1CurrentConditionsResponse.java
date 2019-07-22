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

package ch.deletescape.lawnchair.smartspace.weathercom.models;

import com.google.gson.annotations.SerializedName;

public class SunV1CurrentConditionsResponse extends BaseModel {

    public SuccessMetadataSchema metadata;
    public ObservationsSchema observation;

    public static class SuccessMetadataSchema extends BaseModel {
        public String version;
        public String transaction_id;
        public String location_id;
        public String language;
        public String units;
        public String areaID;

        public Long expire_time_gmt;
        public Integer status_code;

        public Double longitude, latitude;

        public boolean success;
        public ErrorResponse error;
    }

    public static class ObservationsSchema extends BaseModel {
        @SerializedName("blunt_phrase")
        public String bluntPhrase;

        @SerializedName("class")
        public String propertyClass;

        @SerializedName("clds")
        public String clds;

        @SerializedName("day_ind")
        public String dayInd;

        @SerializedName("dewpt")
        public Integer dewpt;

        @SerializedName("expire_time_gmt")
        public Integer expireTimeGmt;

        @SerializedName("feels_like")
        public Integer feelsLike;

        @SerializedName("gust")
        public Integer gust;

        @SerializedName("heat_index")
        public Integer heatIndex;

        @SerializedName("icon_extd")
        public Integer iconExtd;

        @SerializedName("key")
        public String key;

        @SerializedName("max_temp")
        public Integer maxTemp;

        @SerializedName("min_temp")
        public Integer minTemp;

        @SerializedName("obs_id")
        public String obsId;

        @SerializedName("obs_name")
        public String obsName;

        @SerializedName("precip_hrly")
        public Integer precipHrly;

        @SerializedName("precip_total")
        public Integer precipTotal;

        @SerializedName("pressure")
        public Double pressure;

        @SerializedName("pressure_desc")
        public String pressureDesc;

        @SerializedName("pressure_tend")
        public Integer pressureTend;

        @SerializedName("qualifier")
        public String qualifier;

        @SerializedName("qualifier_svrty")
        public String qualifierSvrty;

        @SerializedName("rh")
        public Integer rh;

        @SerializedName("snow_hrly")
        public Integer snowHrly;

        @SerializedName("temp")
        public Integer temp;

        @SerializedName("terse_phrase")
        public String tersePhrase;

        @SerializedName("uv_desc")
        public String uvDesc;

        @SerializedName("uv_index")
        public Integer uvIndex;

        @SerializedName("valid_time_gmt")
        public Integer validTimeGmt;

        @SerializedName("vis")
        public Integer vis;

        @SerializedName("wc")
        public Integer wc;

        @SerializedName("wdir")
        public Integer wdir;

        @SerializedName("wdir_cardinal")
        public String wdirCardinal;

        @SerializedName("wspd")
        public Integer wspd;

        @SerializedName("wx_icon")
        public Integer wxIcon;

        @SerializedName("wx_phrase")
        public String wxPhrase;

        @SerializedName("water_temp")
        public Integer waterTemp;

        @SerializedName("primary_wave_period")
        public Integer primaryWavePeriod;

        @SerializedName("primary_wave_height")
        public Integer primaryWaveHeight;

        @SerializedName("primary_swell_period")
        public Integer primarySwellPeriod;

        @SerializedName("primary_swell_height")
        public Integer primarySwellHeight;

        @SerializedName("primary_swell_direction")
        public Integer primarySwellDirection;

        @SerializedName("secondary_swell_period")
        public Integer secondarySwellPeriod;

        @SerializedName("secondary_swell_height")
        public Integer secondarySwellHeight;

        @SerializedName("secondary_swell_direction")
        public Integer secondarySwellDirection;
    }

}
