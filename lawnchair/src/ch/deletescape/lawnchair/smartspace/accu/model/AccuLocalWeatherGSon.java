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

import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuBriefGSon;
import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuCurrentConditionsGSon;
import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuForecastSummaryGSon;
import ch.deletescape.lawnchair.smartspace.accu.model.sub.AccuLocationGSon;
import java.util.List;

public class AccuLocalWeatherGSon extends GSonBase {
    AccuCurrentConditionsGSon CurrentConditions;
    AccuForecastSummaryGSon ForecastSummary;
    AccuLocationGSon Location;
    AccuBriefGSon Maps;
    List<AccuHourlyForecastGSon> hour;

    public AccuLocationGSon getLocation() {
        return this.Location;
    }

    public void setLocation(AccuLocationGSon location) {
        this.Location = location;
    }

    public AccuCurrentConditionsGSon getCurrentConditions() {
        return this.CurrentConditions;
    }

    public void setCurrentConditions(AccuCurrentConditionsGSon currentConditions) {
        this.CurrentConditions = currentConditions;
    }

    public AccuForecastSummaryGSon getForecastSummary() {
        return this.ForecastSummary;
    }

    public void setForecastSummary(AccuForecastSummaryGSon forecastSummary) {
        this.ForecastSummary = forecastSummary;
    }

    public AccuBriefGSon getMaps() {
        return this.Maps;
    }

    public void setMaps(AccuBriefGSon maps) {
        this.Maps = maps;
    }

    public List<AccuHourlyForecastGSon> getHour() {
        return this.hour;
    }

    public void setHour(List<AccuHourlyForecastGSon> hour2) {
        this.hour = hour2;
    }
}
