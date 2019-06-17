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


import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuUnitGSon;
import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuUnitValueGSon;

public class AccuHourlyForecastGSon extends GSonBase {
    AccuUnitValueGSon Ceiling;
    int CloudCover;
    String DateTime;
    AccuUnitValueGSon DewPoint;
    long EpochDateTime;
    AccuUnitValueGSon Ice;
    int IceProbability;
    String IconPhrase;
    boolean IsDaylight;
    String Link;
    String MobileLink;
    int PrecipitationProbability;
    AccuUnitValueGSon Rain;
    int RainProbability;
    AccuUnitValueGSon RealFeelTemperature;
    int RelativeHumidity;
    AccuUnitValueGSon Snow;
    int SnowProbability;
    AccuUnitValueGSon Temperature;
    AccuUnitValueGSon TotalLiquid;
    int UVIndex;
    String UVIndexText;
    AccuUnitValueGSon Visibility;
    int WeatherIcon;
    AccuUnitValueGSon WetBulbTemperature;
    AccuUnitGSon Wind;
    AccuUnitGSon WindGust;

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

    public String getDateTime() {
        return this.DateTime;
    }

    public void setDateTime(String dateTime) {
        this.DateTime = dateTime;
    }

    public AccuUnitValueGSon getDewPoint() {
        return this.DewPoint;
    }

    public void setDewPoint(AccuUnitValueGSon dewPoint) {
        this.DewPoint = dewPoint;
    }

    public long getEpochDateTime() {
        return this.EpochDateTime;
    }

    public void setEpochDateTime(long epochDateTime) {
        this.EpochDateTime = epochDateTime;
    }

    public AccuUnitValueGSon getIce() {
        return this.Ice;
    }

    public void setIce(AccuUnitValueGSon ice) {
        this.Ice = ice;
    }

    public int getIceProbability() {
        return this.IceProbability;
    }

    public void setIceProbability(int iceProbability) {
        this.IceProbability = iceProbability;
    }

    public String getIconPhrase() {
        return this.IconPhrase;
    }

    public void setIconPhrase(String iconPhrase) {
        this.IconPhrase = iconPhrase;
    }

    public boolean isDaylight() {
        return this.IsDaylight;
    }

    public void setIsDaylight(boolean isDaylight) {
        this.IsDaylight = isDaylight;
    }

    public String getLink() {
        return this.Link;
    }

    public void setLink(String link) {
        this.Link = link;
    }

    public String getMobileLink() {
        return this.MobileLink;
    }

    public void setMobileLink(String mobileLink) {
        this.MobileLink = mobileLink;
    }

    public int getPrecipitationProbability() {
        return this.PrecipitationProbability;
    }

    public void setPrecipitationProbability(int precipitationProbability) {
        this.PrecipitationProbability = precipitationProbability;
    }

    public AccuUnitValueGSon getRain() {
        return this.Rain;
    }

    public void setRain(AccuUnitValueGSon rain) {
        this.Rain = rain;
    }

    public int getRainProbability() {
        return this.RainProbability;
    }

    public void setRainProbability(int rainProbability) {
        this.RainProbability = rainProbability;
    }

    public AccuUnitValueGSon getRealFeelTemperature() {
        return this.RealFeelTemperature;
    }

    public void setRealFeelTemperature(AccuUnitValueGSon realFeelTemperature) {
        this.RealFeelTemperature = realFeelTemperature;
    }

    public int getRelativeHumidity() {
        return this.RelativeHumidity;
    }

    public void setRelativeHumidity(int relativeHumidity) {
        this.RelativeHumidity = relativeHumidity;
    }

    public AccuUnitValueGSon getSnow() {
        return this.Snow;
    }

    public void setSnow(AccuUnitValueGSon snow) {
        this.Snow = snow;
    }

    public int getSnowProbability() {
        return this.SnowProbability;
    }

    public void setSnowProbability(int snowProbability) {
        this.SnowProbability = snowProbability;
    }

    public AccuUnitValueGSon getTemperature() {
        return this.Temperature;
    }

    public void setTemperature(AccuUnitValueGSon temperature) {
        this.Temperature = temperature;
    }

    public AccuUnitValueGSon getTotalLiquid() {
        return this.TotalLiquid;
    }

    public void setTotalLiquid(AccuUnitValueGSon totalLiquid) {
        this.TotalLiquid = totalLiquid;
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

    public AccuUnitGSon getWindGust() {
        return this.WindGust;
    }

    public void setWindGust(AccuUnitGSon windGust) {
        this.WindGust = windGust;
    }
}
