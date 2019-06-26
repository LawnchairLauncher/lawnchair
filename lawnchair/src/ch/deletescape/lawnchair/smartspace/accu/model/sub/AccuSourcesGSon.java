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

public class AccuSourcesGSon {
    String DataType;
    String Source;
    String SourceId;

    public String getDataType() {
        return this.DataType;
    }

    public void setDataType(String dataType) {
        this.DataType = dataType;
    }

    public String getSource() {
        return this.Source;
    }

    public void setSource(String source) {
        this.Source = source;
    }

    public String getSourceId() {
        return this.SourceId;
    }

    public void setSourceId(String sourceId) {
        this.SourceId = sourceId;
    }
}
