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

package ch.deletescape.lawnchair.smartspace.accu.model;

import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuAreaGSon;
import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuGeoPositionGSon;
import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuTimeZoneGSon;
import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuUnitGSon;
import java.util.List;

public class AccuCommonLocalGSon extends GSonBase {
    AccuAreaGSon AdministrativeArea;
    AccuAreaGSon Country;
    String EnglishName;
    long EpochTime;
    AccuGeoPositionGSon GeoPosition;
    boolean IsAlias;
    String IsDayTime;
    String Key;
    String Link;
    String LocalObservationDateTime;
    String LocalizedName;
    String MobileLink;
    String PrimaryPostalCode;
    int Rank;
    AccuAreaGSon Region;
    List<AccuAreaGSon> SupplementalAdminAreas;
    AccuUnitGSon Temperature;
    AccuTimeZoneGSon TimeZone;
    String Type;
    int Version;
    int WeatherIcon;
    String WeatherText;

    public AccuAreaGSon getAdministrativeArea() {
        return this.AdministrativeArea;
    }

    public void setAdministrativeArea(AccuAreaGSon administrativeArea) {
        this.AdministrativeArea = administrativeArea;
    }

    public AccuAreaGSon getCountry() {
        return this.Country;
    }

    public void setCountry(AccuAreaGSon country) {
        this.Country = country;
    }

    public String getEnglishName() {
        return this.EnglishName;
    }

    public void setEnglishName(String englishName) {
        this.EnglishName = englishName;
    }

    public long getEpochTime() {
        return this.EpochTime;
    }

    public void setEpochTime(long epochTime) {
        this.EpochTime = epochTime;
    }

    public AccuGeoPositionGSon getGeoPosition() {
        return this.GeoPosition;
    }

    public void setGeoPosition(AccuGeoPositionGSon geoPosition) {
        this.GeoPosition = geoPosition;
    }

    public boolean isAlias() {
        return this.IsAlias;
    }

    public void setIsAlias(boolean isAlias) {
        this.IsAlias = isAlias;
    }

    public String getIsDayTime() {
        return this.IsDayTime;
    }

    public void setIsDayTime(String isDayTime) {
        this.IsDayTime = isDayTime;
    }

    public String getKey() {
        return this.Key;
    }

    public void setKey(String key) {
        this.Key = key;
    }

    public String getLink() {
        return this.Link;
    }

    public void setLink(String link) {
        this.Link = link;
    }

    public String getLocalizedName() {
        return this.LocalizedName;
    }

    public void setLocalizedName(String localizedName) {
        this.LocalizedName = localizedName;
    }

    public String getLocalObservationDateTime() {
        return this.LocalObservationDateTime;
    }

    public void setLocalObservationDateTime(String localObservationDateTime) {
        this.LocalObservationDateTime = localObservationDateTime;
    }

    public String getMobileLink() {
        return this.MobileLink;
    }

    public void setMobileLink(String mobileLink) {
        this.MobileLink = mobileLink;
    }

    public String getPrimaryPostalCode() {
        return this.PrimaryPostalCode;
    }

    public void setPrimaryPostalCode(String primaryPostalCode) {
        this.PrimaryPostalCode = primaryPostalCode;
    }

    public int getRank() {
        return this.Rank;
    }

    public void setRank(int rank) {
        this.Rank = rank;
    }

    public AccuAreaGSon getRegion() {
        return this.Region;
    }

    public void setRegion(AccuAreaGSon region) {
        this.Region = region;
    }

    public List<AccuAreaGSon> getSupplementalAdminAreas() {
        return this.SupplementalAdminAreas;
    }

    public void setSupplementalAdminAreas(List<AccuAreaGSon> supplementalAdminAreas) {
        this.SupplementalAdminAreas = supplementalAdminAreas;
    }

    public AccuUnitGSon getTemperature() {
        return this.Temperature;
    }

    public void setTemperature(AccuUnitGSon temperature) {
        this.Temperature = temperature;
    }

    public AccuTimeZoneGSon getTimeZone() {
        return this.TimeZone;
    }

    public void setTimeZone(AccuTimeZoneGSon timeZone) {
        this.TimeZone = timeZone;
    }

    public String getType() {
        return this.Type;
    }

    public void setType(String type) {
        this.Type = type;
    }

    public int getVersion() {
        return this.Version;
    }

    public void setVersion(int version) {
        this.Version = version;
    }

    public int getWeatherIcon() {
        return this.WeatherIcon;
    }

    public void setWeatherIcon(int weatherIcon) {
        this.WeatherIcon = weatherIcon;
    }

    public String getWeatherText() {
        return this.WeatherText;
    }

    public void setWeatherText(String weatherText) {
        this.WeatherText = weatherText;
    }
}
