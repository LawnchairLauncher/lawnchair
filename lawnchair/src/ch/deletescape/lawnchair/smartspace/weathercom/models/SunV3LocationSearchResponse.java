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

import androidx.annotation.Nullable;
import java.util.List;

public class SunV3LocationSearchResponse extends BaseModel {
    public LocationList location;

    public static class LocationList extends BaseModel {
        public List<String> address;
        public List<String> adminDistrict;
        public List<String> adminDistrictCode;
        @Nullable
        public String airportCode;
        public List<String> city;
        public List<String> country;
        public List<String> countryCode;
        public List<String> displayName;
        public List<String> ianaTimeZone;
        @Nullable
        public List<String> neighborhood;
        public List<String> postalCode;
        public List<String> postalKey;
        public List<String> placeId;
        public List<String> type;

        public List<Double> latitude;
        public List<Double> longitude;

        public List<Locale> locale;

        public static class Locale extends BaseModel {
            public String locale1;
            public String locale2;
            public String locale3;
            public String locale4;
        }
    }
}
