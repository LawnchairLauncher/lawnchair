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

public class AccuDetailGSon {
    String BandMap;
    String CanonicalLocationKey;
    String CanonicalPostalCode;
    String Climo;
    AccuAreaGSon DMA;
    String Key;
    String LocalRadar;
    String MarineStation;
    double MarineStationGMTOffset;
    String MediaRegion;
    String Metar;
    String NXMetro;
    String NXState;
    String PartnerID;
    long Population;
    String PrimaryWarningCountyCode;
    String PrimaryWarningZoneCode;
    String Satellite;
    List<AccuSourcesGSon> Sources;
    String StationCode;
    double StationGmtOffset;
    String Synoptic;
    String VideoCode;

    public String getBandMap() {
        return this.BandMap;
    }

    public void setBandMap(String bandMap) {
        this.BandMap = bandMap;
    }

    public String getCanonicalLocationKey() {
        return this.CanonicalLocationKey;
    }

    public void setCanonicalLocationKey(String canonicalLocationKey) {
        this.CanonicalLocationKey = canonicalLocationKey;
    }

    public String getCanonicalPostalCode() {
        return this.CanonicalPostalCode;
    }

    public void setCanonicalPostalCode(String canonicalPostalCode) {
        this.CanonicalPostalCode = canonicalPostalCode;
    }

    public String getClimo() {
        return this.Climo;
    }

    public void setClimo(String climo) {
        this.Climo = climo;
    }

    public AccuAreaGSon getDMA() {
        return this.DMA;
    }

    public void setDMA(AccuAreaGSon DMA2) {
        this.DMA = DMA2;
    }

    public String getKey() {
        return this.Key;
    }

    public void setKey(String key) {
        this.Key = key;
    }

    public String getLocalRadar() {
        return this.LocalRadar;
    }

    public void setLocalRadar(String localRadar) {
        this.LocalRadar = localRadar;
    }

    public String getMarineStation() {
        return this.MarineStation;
    }

    public void setMarineStation(String marineStation) {
        this.MarineStation = marineStation;
    }

    public double getMarineStationGMTOffset() {
        return this.MarineStationGMTOffset;
    }

    public void setMarineStationGMTOffset(double marineStationGMTOffset) {
        this.MarineStationGMTOffset = marineStationGMTOffset;
    }

    public String getMediaRegion() {
        return this.MediaRegion;
    }

    public void setMediaRegion(String mediaRegion) {
        this.MediaRegion = mediaRegion;
    }

    public String getMetar() {
        return this.Metar;
    }

    public void setMetar(String metar) {
        this.Metar = metar;
    }

    public String getNXMetro() {
        return this.NXMetro;
    }

    public void setNXMetro(String NXMetro2) {
        this.NXMetro = NXMetro2;
    }

    public String getNXState() {
        return this.NXState;
    }

    public void setNXState(String NXState2) {
        this.NXState = NXState2;
    }

    public String getPartnerID() {
        return this.PartnerID;
    }

    public void setPartnerID(String partnerID) {
        this.PartnerID = partnerID;
    }

    public long getPopulation() {
        return this.Population;
    }

    public void setPopulation(long population) {
        this.Population = population;
    }

    public String getPrimaryWarningCountyCode() {
        return this.PrimaryWarningCountyCode;
    }

    public void setPrimaryWarningCountyCode(String primaryWarningCountyCode) {
        this.PrimaryWarningCountyCode = primaryWarningCountyCode;
    }

    public String getPrimaryWarningZoneCode() {
        return this.PrimaryWarningZoneCode;
    }

    public void setPrimaryWarningZoneCode(String primaryWarningZoneCode) {
        this.PrimaryWarningZoneCode = primaryWarningZoneCode;
    }

    public String getSatellite() {
        return this.Satellite;
    }

    public void setSatellite(String satellite) {
        this.Satellite = satellite;
    }

    public List<AccuSourcesGSon> getSources() {
        return this.Sources;
    }

    public void setSources(List<AccuSourcesGSon> sources) {
        this.Sources = sources;
    }

    public String getStationCode() {
        return this.StationCode;
    }

    public void setStationCode(String stationCode) {
        this.StationCode = stationCode;
    }

    public double getStationGmtOffset() {
        return this.StationGmtOffset;
    }

    public void setStationGmtOffset(double stationGmtOffset) {
        this.StationGmtOffset = stationGmtOffset;
    }

    public String getSynoptic() {
        return this.Synoptic;
    }

    public void setSynoptic(String synoptic) {
        this.Synoptic = synoptic;
    }

    public String getVideoCode() {
        return this.VideoCode;
    }

    public void setVideoCode(String videoCode) {
        this.VideoCode = videoCode;
    }
}
