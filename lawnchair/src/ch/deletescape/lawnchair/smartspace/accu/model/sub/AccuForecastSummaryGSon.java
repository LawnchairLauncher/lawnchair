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

import ch.deletescape.lawnchair.smartspace.accu.model.AccuHourlyForecastGSon;
import ch.deletescape.lawnchair.smartspace.accu.model.GSonBase;
import java.util.List;

public class AccuForecastSummaryGSon extends GSonBase {
    List<AccuDailyForecastGSon> DailyForecasts;
    AccuHeadlineGSon Headline;
    List<AccuHourlyForecastGSon> HourlyForecasts;

    public AccuHeadlineGSon getHeadline() {
        return this.Headline;
    }

    public void setHeadline(AccuHeadlineGSon headline) {
        this.Headline = headline;
    }

    public List<AccuDailyForecastGSon> getDailyForecasts() {
        return this.DailyForecasts;
    }

    public void setDailyForecasts(List<AccuDailyForecastGSon> dailyForecasts) {
        this.DailyForecasts = dailyForecasts;
    }

    public List<AccuHourlyForecastGSon> getHourlyForecasts() {
        return this.HourlyForecasts;
    }

    public void setHourlyForecasts(List<AccuHourlyForecastGSon> hourlyForecasts) {
        this.HourlyForecasts = hourlyForecasts;
    }
}
