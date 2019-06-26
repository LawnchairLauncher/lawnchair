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

public class AccuDaynNightGSon {
    String CloudCover;
    String HoursOfPrecipitation;
    String HoursOfRain;
    AccuUnitValueGSon Ice;
    int IceProbability;
    int Icon;
    String IconPhrase;
    String LongPhrase;
    int PrecipitationProbability;
    AccuUnitValueGSon Rain;
    int RainProbability;
    String ShortPhrase;
    AccuUnitValueGSon Snow;
    int SnowProbability;
    int ThunderstormProbability;
    AccuUnitValueGSon TotalLiquid;
    AccuUnitGSon Wind;
    AccuUnitGSon WindGust;

    public String getCloudCover() {
        return this.CloudCover;
    }

    public void setCloudCover(String cloudCover) {
        this.CloudCover = cloudCover;
    }

    public String getHoursOfPrecipitation() {
        return this.HoursOfPrecipitation;
    }

    public void setHoursOfPrecipitation(String hoursOfPrecipitation) {
        this.HoursOfPrecipitation = hoursOfPrecipitation;
    }

    public String getHoursOfRain() {
        return this.HoursOfRain;
    }

    public void setHoursOfRain(String hoursOfRain) {
        this.HoursOfRain = hoursOfRain;
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

    public int getIcon() {
        return this.Icon;
    }

    public void setIcon(int icon) {
        this.Icon = icon;
    }

    public String getIconPhrase() {
        return this.IconPhrase;
    }

    public void setIconPhrase(String iconPhrase) {
        this.IconPhrase = iconPhrase;
    }

    public String getLongPhrase() {
        return this.LongPhrase;
    }

    public void setLongPhrase(String longPhrase) {
        this.LongPhrase = longPhrase;
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

    public String getShortPhrase() {
        return this.ShortPhrase;
    }

    public void setShortPhrase(String shortPhrase) {
        this.ShortPhrase = shortPhrase;
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

    public int getThunderstormProbability() {
        return this.ThunderstormProbability;
    }

    public void setThunderstormProbability(int thunderstormProbability) {
        this.ThunderstormProbability = thunderstormProbability;
    }

    public AccuUnitValueGSon getTotalLiquid() {
        return this.TotalLiquid;
    }

    public void setTotalLiquid(AccuUnitValueGSon totalLiquid) {
        this.TotalLiquid = totalLiquid;
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
