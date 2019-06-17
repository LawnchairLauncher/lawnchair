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

public class AccuAreaGSon {
    String CountryID;
    String EnglishName;
    String EnglishType;
    String ID;
    String Level;
    String LocalizedName;
    String LocalizedType;

    public String getID() {
        return this.ID;
    }

    public void setID(String iD) {
        this.ID = iD;
    }

    public String getLocalizedName() {
        return this.LocalizedName;
    }

    public void setLocalizedName(String localizedName) {
        this.LocalizedName = localizedName;
    }

    public String getEnglishName() {
        return this.EnglishName;
    }

    public void setEnglishName(String englishName) {
        this.EnglishName = englishName;
    }

    public String getLevel() {
        return this.Level;
    }

    public void setLevel(String level) {
        this.Level = level;
    }

    public String getLocalizedType() {
        return this.LocalizedType;
    }

    public void setLocalizedType(String localizedType) {
        this.LocalizedType = localizedType;
    }

    public String getEnglishType() {
        return this.EnglishType;
    }

    public void setEnglishType(String englishType) {
        this.EnglishType = englishType;
    }

    public String getCountryID() {
        return this.CountryID;
    }

    public void setCountryID(String countryID) {
        this.CountryID = countryID;
    }
}
