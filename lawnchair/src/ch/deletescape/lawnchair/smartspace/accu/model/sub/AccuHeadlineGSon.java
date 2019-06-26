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

public class AccuHeadlineGSon {
    String Category;
    String EffectiveDate;
    long EffectiveEpochDate;
    String EndDate;
    long EndEpochDate;
    String Link;
    String MobileLink;
    int Severity;
    String Text;

    public String getCategory() {
        return this.Category;
    }

    public void setCategory(String category) {
        this.Category = category;
    }

    public String getEffectiveDate() {
        return this.EffectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.EffectiveDate = effectiveDate;
    }

    public long getEffectiveEpochDate() {
        return this.EffectiveEpochDate;
    }

    public void setEffectiveEpochDate(long effectiveEpochDate) {
        this.EffectiveEpochDate = effectiveEpochDate;
    }

    public String getEndDate() {
        return this.EndDate;
    }

    public void setEndDate(String endDate) {
        this.EndDate = endDate;
    }

    public long getEndEpochDate() {
        return this.EndEpochDate;
    }

    public void setEndEpochDate(long endEpochDate) {
        this.EndEpochDate = endEpochDate;
    }

    public String getLink() {
        return this.Link;
    }

    public void setLink(String link) {
        this.Link = link;
    }

    public String getMobileLink() {
        return this.MobileLink;
    }

    public void setMobileLink(String mobileLink) {
        this.MobileLink = mobileLink;
    }

    public int getSeverity() {
        return this.Severity;
    }

    public void setSeverity(int severity) {
        this.Severity = severity;
    }

    public String getText() {
        return this.Text;
    }

    public void setText(String text) {
        this.Text = text;
    }
}
