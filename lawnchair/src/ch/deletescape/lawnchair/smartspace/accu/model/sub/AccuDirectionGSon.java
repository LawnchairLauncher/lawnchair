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

public class AccuDirectionGSon {
    int Degrees;
    String English;
    String Localized;

    public int getDegrees() {
        return this.Degrees;
    }

    public void setDegrees(int degrees) {
        this.Degrees = degrees;
    }

    public String getLocalized() {
        return this.Localized;
    }

    public void setLocalized(String localized) {
        this.Localized = localized;
    }

    public String getEnglish() {
        return this.English;
    }

    public void setEnglish(String english) {
        this.English = english;
    }
}
