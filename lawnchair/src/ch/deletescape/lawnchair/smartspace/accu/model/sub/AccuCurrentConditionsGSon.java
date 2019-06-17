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

package ch.deletescape.lawnchair.smartspace.accu.model.sub;

import ch.deletescape.lawnchair.smartspace.accu.model.GSonBase;
import java.util.List;

public class AccuCurrentConditionsGSon extends GSonBase {
    AccuUnitValueGSon ApparentTemperature;
    AccuUnitValueGSon Ceiling;
    int CloudCover;
    AccuUnitValueGSon DewPoint;
    long EpochTime;
    boolean IsDayTime;
    String Link;
    String LocalObservationDateTime;
    String MobileLink;
    String ObstructionsToVisibility;
    AccuUnitValueGSon Past24HourTemperatureDeparture;
    List<AccuPhotoGSon> Photos;
    AccuUnitValueGSon Precip1hr;
    AccuPrecipitationSummaryGSon PrecipitationSummary;
    AccuUnitValueGSon Pressure;
    AccuPressureTendencyGson PressureTendency;
    AccuUnitValueGSon RealFeelTemperature;
    AccuUnitValueGSon RealFeelTemperatureShade;
    String RelativeHumidity;
    AccuUnitValueGSon Temperature;
    AccuTemperatureSummaryGSon TemperatureSummary;
    int UVIndex;
    String UVIndexText;
    AccuUnitValueGSon Visibility;
    int WeatherIcon;
    String WeatherText;
    AccuUnitValueGSon WetBulbTemperature;
    AccuUnitGSon Wind;
    AccuUnitValueGSon WindChillTemperature;
    AccuUnitGSon WindGust;

    public AccuUnitValueGSon getApparentTemperature() {
        return this.ApparentTemperature;
    }

    public void setApparentTemperature(AccuUnitValueGSon apparentTemperature) {
        this.ApparentTemperature = apparentTemperature;
    }

    public AccuUnitValueGSon getCeiling() {
        return this.Ceiling;
    }

    public void setCeiling(AccuUnitValueGSon ceiling) {
        this.Ceiling = ceiling;
    }

    public int getCloudCover() {
        return this.CloudCover;
    }

    public void setCloudCover(int cloudCover) {
        this.CloudCover = cloudCover;
    }

    public AccuUnitValueGSon getDewPoint() {
        return this.DewPoint;
    }

    public void setDewPoint(AccuUnitValueGSon dewPoint) {
        this.DewPoint = dewPoint;
    }

    public long getEpochTime() {
        return this.EpochTime;
    }

    public void setEpochTime(long epochTime) {
        this.EpochTime = epochTime;
    }

    public boolean isDayTime() {
        return this.IsDayTime;
    }

    public void setIsDayTime(boolean isDayTime) {
        this.IsDayTime = isDayTime;
    }

    public String getLink() {
        return this.Link;
    }

    public void setLink(String link) {
        this.Link = link;
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

    public String getObstructionsToVisibility() {
        return this.ObstructionsToVisibility;
    }

    public void setObstructionsToVisibility(String obstructionsToVisibility) {
        this.ObstructionsToVisibility = obstructionsToVisibility;
    }

    public AccuUnitValueGSon getPast24HourTemperatureDeparture() {
        return this.Past24HourTemperatureDeparture;
    }

    public void setPast24HourTemperatureDeparture(AccuUnitValueGSon past24HourTemperatureDeparture) {
        this.Past24HourTemperatureDeparture = past24HourTemperatureDeparture;
    }

    public List<AccuPhotoGSon> getPhotos() {
        return this.Photos;
    }

    public void setPhotos(List<AccuPhotoGSon> photos) {
        this.Photos = photos;
    }

    public AccuUnitValueGSon getPrecip1hr() {
        return this.Precip1hr;
    }

    public void setPrecip1hr(AccuUnitValueGSon precip1hr) {
        this.Precip1hr = precip1hr;
    }

    public AccuPrecipitationSummaryGSon getPrecipitationSummary() {
        return this.PrecipitationSummary;
    }

    public void setPrecipitationSummary(AccuPrecipitationSummaryGSon precipitationSummary) {
        this.PrecipitationSummary = precipitationSummary;
    }

    public AccuUnitValueGSon getPressure() {
        return this.Pressure;
    }

    public void setPressure(AccuUnitValueGSon pressure) {
        this.Pressure = pressure;
    }

    public AccuPressureTendencyGson getPressureTendency() {
        return this.PressureTendency;
    }

    public void setPressureTendency(AccuPressureTendencyGson pressureTendency) {
        this.PressureTendency = pressureTendency;
    }

    public AccuUnitValueGSon getRealFeelTemperature() {
        return this.RealFeelTemperature;
    }

    public void setRealFeelTemperature(AccuUnitValueGSon realFeelTemperature) {
        this.RealFeelTemperature = realFeelTemperature;
    }

    public AccuUnitValueGSon getRealFeelTemperatureShade() {
        return this.RealFeelTemperatureShade;
    }

    public void setRealFeelTemperatureShade(AccuUnitValueGSon realFeelTemperatureShade) {
        this.RealFeelTemperatureShade = realFeelTemperatureShade;
    }

    public String getRelativeHumidity() {
        return this.RelativeHumidity;
    }

    public void setRelativeHumidity(String relativeHumidity) {
        this.RelativeHumidity = relativeHumidity;
    }

    public AccuUnitValueGSon getTemperature() {
        return this.Temperature;
    }

    public void setTemperature(AccuUnitValueGSon temperature) {
        this.Temperature = temperature;
    }

    public AccuTemperatureSummaryGSon getTemperatureSummary() {
        return this.TemperatureSummary;
    }

    public void setTemperatureSummary(AccuTemperatureSummaryGSon temperatureSummary) {
        this.TemperatureSummary = temperatureSummary;
    }

    public int getUVIndex() {
        return this.UVIndex;
    }

    public void setUVIndex(int UVIndex2) {
        this.UVIndex = UVIndex2;
    }

    public String getUVIndexText() {
        return this.UVIndexText;
    }

    public void setUVIndexText(String UVIndexText2) {
        this.UVIndexText = UVIndexText2;
    }

    public AccuUnitValueGSon getVisibility() {
        return this.Visibility;
    }

    public void setVisibility(AccuUnitValueGSon visibility) {
        this.Visibility = visibility;
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

    public AccuUnitValueGSon getWetBulbTemperature() {
        return this.WetBulbTemperature;
    }

    public void setWetBulbTemperature(AccuUnitValueGSon wetBulbTemperature) {
        this.WetBulbTemperature = wetBulbTemperature;
    }

    public AccuUnitGSon getWind() {
        return this.Wind;
    }

    public void setWind(AccuUnitGSon wind) {
        this.Wind = wind;
    }

    public AccuUnitValueGSon getWindChillTemperature() {
        return this.WindChillTemperature;
    }

    public void setWindChillTemperature(AccuUnitValueGSon windChillTemperature) {
        this.WindChillTemperature = windChillTemperature;
    }

    public AccuUnitGSon getWindGust() {
        return this.WindGust;
    }

    public void setWindGust(AccuUnitGSon windGust) {
        this.WindGust = windGust;
    }
}
