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

public class AccuSunnMoonGSon {
    String Age;
    String EpochRise;
    String EpochSet;
    String Phase;
    String Rise;
    String Set;

    public String getRise() {
        return this.Rise;
    }

    public void setRise(String rise) {
        this.Rise = rise;
    }

    public String getEpochRise() {
        return this.EpochRise;
    }

    public void setEpochRise(String epochRise) {
        this.EpochRise = epochRise;
    }

    public String getSet() {
        return this.Set;
    }

    public void setSet(String set) {
        this.Set = set;
    }

    public String getEpochSet() {
        return this.EpochSet;
    }

    public void setEpochSet(String epochSet) {
        this.EpochSet = epochSet;
    }

    public String getPhase() {
        return this.Phase;
    }

    public void setPhase(String phase) {
        this.Phase = phase;
    }

    public String getAge() {
        return this.Age;
    }

    public void setAge(String age) {
        this.Age = age;
    }
}
