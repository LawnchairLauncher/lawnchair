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

public class AccuTemperatureSummaryGSon {
    AccuUnitGSon Past12HourRange;
    AccuUnitGSon Past24HourRange;
    AccuUnitGSon Past6HourRange;

    public AccuUnitGSon getPast12HourRange() {
        return this.Past12HourRange;
    }

    public void setPast12HourRange(AccuUnitGSon past12HourRange) {
        this.Past12HourRange = past12HourRange;
    }

    public AccuUnitGSon getPast24HourRange() {
        return this.Past24HourRange;
    }

    public void setPast24HourRange(AccuUnitGSon past24HourRange) {
        this.Past24HourRange = past24HourRange;
    }

    public AccuUnitGSon getPast6HourRange() {
        return this.Past6HourRange;
    }

    public void setPast6HourRange(AccuUnitGSon past6HourRange) {
        this.Past6HourRange = past6HourRange;
    }
}
