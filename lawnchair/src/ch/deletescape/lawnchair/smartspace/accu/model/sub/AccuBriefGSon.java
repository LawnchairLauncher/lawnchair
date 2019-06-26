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

import ch.deletescape.lawnchair.smartspace.accu.model.GSonBase;

public class AccuBriefGSon extends GSonBase {
    String Link;
    String MobileLink;
    AccuSatelliteGSon Radar;
    AccuSatelliteGSon Satellite;

    public String getMobileLink() {
        return this.MobileLink;
    }

    public void setMobileLink(String mobileLink) {
        this.MobileLink = mobileLink;
    }

    public String getLink() {
        return this.Link;
    }

    public void setLink(String link) {
        this.Link = link;
    }

    public AccuSatelliteGSon getRadar() {
        return this.Radar;
    }

    public void setRadar(AccuSatelliteGSon radar) {
        this.Radar = radar;
    }

    public AccuSatelliteGSon getSatellite() {
        return this.Satellite;
    }

    public void setSatellite(AccuSatelliteGSon satellite) {
        this.Satellite = satellite;
    }
}
