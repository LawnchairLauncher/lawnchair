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

public class AccuPhotoGSon {
    String DateTaken;
    String Description;
    String LandscapeLink;
    String PortraitLink;
    String Source;

    public String getDateTaken() {
        return this.DateTaken;
    }

    public void setDateTaken(String dateTaken) {
        this.DateTaken = dateTaken;
    }

    public String getSource() {
        return this.Source;
    }

    public void setSource(String source) {
        this.Source = source;
    }

    public String getDescription() {
        return this.Description;
    }

    public void setDescription(String description) {
        this.Description = description;
    }

    public String getPortraitLink() {
        return this.PortraitLink;
    }

    public void setPortraitLink(String portraitLink) {
        this.PortraitLink = portraitLink;
    }

    public String getLandscapeLink() {
        return this.LandscapeLink;
    }

    public void setLandscapeLink(String landscapeLink) {
        this.LandscapeLink = landscapeLink;
    }
}
