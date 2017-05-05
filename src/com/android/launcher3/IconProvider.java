package com.android.launcher3;

import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;

import java.util.Locale;

public class IconProvider {

    private static final boolean DBG = false;
    private static final String TAG = "IconProvider";

    protected String mSystemState;

    public IconProvider() {
        updateSystemStateString();
    }

    public void updateSystemStateString() {
        mSystemState = Locale.getDefault().toString();
    }

    public String getIconSystemState(String packageName) {
        return mSystemState;
    }


    public Drawable getIcon(LauncherActivityInfo info, int iconDpi) {
        return info.getIcon(iconDpi);
    }
}
