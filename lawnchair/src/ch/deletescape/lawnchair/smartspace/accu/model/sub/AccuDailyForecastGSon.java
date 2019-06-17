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

import java.util.List;

public class AccuDailyForecastGSon {
    List<AccuCategoryGSon> AirAndPollen;
    String Date;
    AccuDaynNightGSon Day;
    AccuUnitGSon DegreeDaySummary;
    long EpochDate;
    double HoursOfSun;
    String Link;
    String MobileLink;
    AccuSunnMoonGSon Moon;
    AccuDaynNightGSon Night;
    AccuUnitGSon RealFeelTemperature;
    AccuUnitGSon RealFeelTemperatureShade;
    AccuSunnMoonGSon Sun;
    AccuUnitGSon Temperature;

    public List<AccuCategoryGSon> getAirAndPollen() {
        return this.AirAndPollen;
    }

    public void setAirAndPollen(List<AccuCategoryGSon> airAndPollen) {
        this.AirAndPollen = airAndPollen;
    }

    public String getDate() {
        return this.Date;
    }

    public void setDate(String date) {
        this.Date = date;
    }

    public AccuDaynNightGSon getDay() {
        return this.Day;
    }

    public void setDay(AccuDaynNightGSon day) {
        this.Day = day;
    }

    public AccuUnitGSon getDegreeDaySummary() {
        return this.DegreeDaySummary;
    }

    public void setDegreeDaySummary(AccuUnitGSon degreeDaySummary) {
        this.DegreeDaySummary = degreeDaySummary;
    }

    public long getEpochDate() {
        return this.EpochDate;
    }

    public void setEpochDate(long epochDate) {
        this.EpochDate = epochDate;
    }

    public double getHoursOfSun() {
        return this.HoursOfSun;
    }

    public void setHoursOfSun(double hoursOfSun) {
        this.HoursOfSun = hoursOfSun;
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

    public AccuSunnMoonGSon getMoon() {
        return this.Moon;
    }

    public void setMoon(AccuSunnMoonGSon moon) {
        this.Moon = moon;
    }

    public AccuDaynNightGSon getNight() {
        return this.Night;
    }

    public void setNight(AccuDaynNightGSon night) {
        this.Night = night;
    }

    public AccuUnitGSon getRealFeelTemperature() {
        return this.RealFeelTemperature;
    }

    public void setRealFeelTemperature(AccuUnitGSon realFeelTemperature) {
        this.RealFeelTemperature = realFeelTemperature;
    }

    public AccuUnitGSon getRealFeelTemperatureShade() {
        return this.RealFeelTemperatureShade;
    }

    public void setRealFeelTemperatureShade(AccuUnitGSon realFeelTemperatureShade) {
        this.RealFeelTemperatureShade = realFeelTemperatureShade;
    }

    public AccuSunnMoonGSon getSun() {
        return this.Sun;
    }

    public void setSun(AccuSunnMoonGSon sun) {
        this.Sun = sun;
    }

    public AccuUnitGSon getTemperature() {
        return this.Temperature;
    }

    public void setTemperature(AccuUnitGSon temperature) {
        this.Temperature = temperature;
    }
}
