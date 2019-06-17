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

public class AccuUnitGSon {
    AccuUnitGSon Cooling;
    AccuDirectionGSon Direction;
    AccuUnitGSon Heating;
    AccuUnitValueGSon Imperial;
    AccuUnitValueGSon Maximum;
    AccuUnitValueGSon Metric;
    AccuUnitValueGSon Minimum;
    AccuUnitValueGSon Speed;

    public AccuUnitValueGSon getImperial() {
        return this.Imperial;
    }

    public void setImperial(AccuUnitValueGSon imperial) {
        this.Imperial = imperial;
    }

    public AccuUnitValueGSon getMetric() {
        return this.Metric;
    }

    public void setMetric(AccuUnitValueGSon metric) {
        this.Metric = metric;
    }

    public AccuDirectionGSon getDirection() {
        return this.Direction;
    }

    public void setDirection(AccuDirectionGSon direction) {
        this.Direction = direction;
    }

    public AccuUnitValueGSon getSpeed() {
        return this.Speed;
    }

    public void setSpeed(AccuUnitValueGSon speed) {
        this.Speed = speed;
    }

    public AccuUnitValueGSon getMaximum() {
        return this.Maximum;
    }

    public void setMaximum(AccuUnitValueGSon maximum) {
        this.Maximum = maximum;
    }

    public AccuUnitValueGSon getMinimum() {
        return this.Minimum;
    }

    public void setMinimum(AccuUnitValueGSon minimum) {
        this.Minimum = minimum;
    }

    public AccuUnitGSon getCooling() {
        return this.Cooling;
    }

    public void setCooling(AccuUnitGSon cooling) {
        this.Cooling = cooling;
    }

    public AccuUnitGSon getHeating() {
        return this.Heating;
    }

    public void setHeating(AccuUnitGSon heating) {
        this.Heating = heating;
    }
}
