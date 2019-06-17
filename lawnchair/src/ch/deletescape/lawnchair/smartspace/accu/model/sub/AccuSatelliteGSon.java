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

import java.util.List;

public class AccuSatelliteGSon {
    List<AccuImageGSon> Images;
    String Size;

    public String getSize() {
        return this.Size;
    }

    public void setSize(String size) {
        this.Size = size;
    }

    public List<AccuImageGSon> getImages() {
        return this.Images;
    }

    public void setImages(List<AccuImageGSon> images) {
        this.Images = images;
    }
}
