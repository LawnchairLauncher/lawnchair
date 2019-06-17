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

public class AccuPrecipitationSummaryGSon {
    AccuUnitValueGSon Past12Hours;
    AccuUnitValueGSon Past18Hours;
    AccuUnitValueGSon Past24Hours;
    AccuUnitValueGSon Past3Hours;
    AccuUnitValueGSon Past6Hours;
    AccuUnitValueGSon Past9Hours;
    AccuUnitValueGSon PastHour;
    AccuUnitValueGSon Precipitation;

    public AccuUnitValueGSon getPast12Hours() {
        return this.Past12Hours;
    }

    public void setPast12Hours(AccuUnitValueGSon past12Hours) {
        this.Past12Hours = past12Hours;
    }

    public AccuUnitValueGSon getPast18Hours() {
        return this.Past18Hours;
    }

    public void setPast18Hours(AccuUnitValueGSon past18Hours) {
        this.Past18Hours = past18Hours;
    }

    public AccuUnitValueGSon getPast24Hours() {
        return this.Past24Hours;
    }

    public void setPast24Hours(AccuUnitValueGSon past24Hours) {
        this.Past24Hours = past24Hours;
    }

    public AccuUnitValueGSon getPast3Hours() {
        return this.Past3Hours;
    }

    public void setPast3Hours(AccuUnitValueGSon past3Hours) {
        this.Past3Hours = past3Hours;
    }

    public AccuUnitValueGSon getPast6Hours() {
        return this.Past6Hours;
    }

    public void setPast6Hours(AccuUnitValueGSon past6Hours) {
        this.Past6Hours = past6Hours;
    }

    public AccuUnitValueGSon getPast9Hours() {
        return this.Past9Hours;
    }

    public void setPast9Hours(AccuUnitValueGSon past9Hours) {
        this.Past9Hours = past9Hours;
    }

    public AccuUnitValueGSon getPastHour() {
        return this.PastHour;
    }

    public void setPastHour(AccuUnitValueGSon pastHour) {
        this.PastHour = pastHour;
    }

    public AccuUnitValueGSon getPrecipitation() {
        return this.Precipitation;
    }

    public void setPrecipitation(AccuUnitValueGSon precipitation) {
        this.Precipitation = precipitation;
    }
}
