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

public class AccuTimeZoneGSon {
    String Code;
    String GmtOffset;
    String IsDaylightSaving;
    String Name;
    String NextOffsetChange;

    public String getCode() {
        return this.Code;
    }

    public void setCode(String code) {
        this.Code = code;
    }

    public String getName() {
        return this.Name;
    }

    public void setName(String name) {
        this.Name = name;
    }

    public String getGmtOffset() {
        return this.GmtOffset;
    }

    public void setGmtOffset(String gmtOffset) {
        this.GmtOffset = gmtOffset;
    }

    public String getIsDaylightSaving() {
        return this.IsDaylightSaving;
    }

    public void setIsDaylightSaving(String isDaylightSaving) {
        this.IsDaylightSaving = isDaylightSaving;
    }

    public String getNextOffsetChange() {
        return this.NextOffsetChange;
    }

    public void setNextOffsetChange(String nextOffsetChange) {
        this.NextOffsetChange = nextOffsetChange;
    }
}
