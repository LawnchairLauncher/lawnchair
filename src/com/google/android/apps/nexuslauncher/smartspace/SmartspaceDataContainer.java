package com.google.android.apps.nexuslauncher.smartspace;

public class SmartspaceDataContainer {
    SmartspaceCard dO;
    SmartspaceCard dP;

    public SmartspaceDataContainer() {
        dO = null;
        dP = null;
    }

    public boolean isWeatherAvailable() {
        return dO != null;
    }

    public boolean cS() {
        return dP != null;
    }

    public long cT() {
        final long currentTimeMillis = System.currentTimeMillis();
        if (cS() && isWeatherAvailable()) {
            return Math.min(dP.cF(), dO.cF()) - currentTimeMillis;
        }
        if (cS()) {
            return dP.cF() - currentTimeMillis;
        }
        if (isWeatherAvailable()) {
            return dO.cF() - currentTimeMillis;
        }
        return 0L;
    }

    public boolean cU() {
        final boolean b = true;
        boolean b2 = false;
        if (isWeatherAvailable() && dO.cM()) {
            dO = null;
            b2 = b;
        }
        if (cS() && dP.cM()) {
            dP = null;
            b2 = b;
        }
        return b2;
    }

    public String toString() {
        return "{" + dP + "," + dO + "}";
    }
}
