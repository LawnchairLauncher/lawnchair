package com.android.launcher3.uioverrides;

import android.content.Context;
import com.android.launcher3.Utilities;

public abstract class WallpaperColorInfo {

    private static final Object sInstanceLock = new Object();
    private static WallpaperColorInfo sInstance;

    public static WallpaperColorInfo getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Utilities.ATLEAST_Q && !Utilities.HIDDEN_APIS_ALLOWED) {
                    sInstance = new WallpaperColorInfoVL(context.getApplicationContext());
                } else if (Utilities.ATLEAST_OREO_MR1) {
                    sInstance = new WallpaperColorInfoVOMR1(context.getApplicationContext());
                } else {
                    sInstance = new WallpaperColorInfoVL(context.getApplicationContext());
                }
            }
            return sInstance;
        }
    }

    public abstract int getMainColor();

    public abstract int getActualMainColor();

    public abstract int getSecondaryColor();

    public abstract int getActualSecondaryColor();

    public abstract int getTertiaryColor();

    public abstract boolean isDark();

    public abstract boolean supportsDarkText();

    public abstract void addOnChangeListener(OnChangeListener listener);

    public abstract void removeOnChangeListener(OnChangeListener listener);

    public interface OnChangeListener {
        void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo);
    }
}
