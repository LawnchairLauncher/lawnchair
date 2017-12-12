package com.android.launcher3;

import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.util.Locale;

public class IconProvider {

    private static final boolean DBG = false;
    private static final String TAG = "IconProvider";

    protected String mSystemState;

    public IconProvider() {
        updateSystemStateString();
    }

    public void updateSystemStateString() {
        mSystemState = Locale.getDefault().toString() + "," + Build.VERSION.SDK_INT;
    }

    public String getIconSystemState(String packageName) {
        return mSystemState;
    }

    /**
     * @param flattenDrawable true if the caller does not care about the specification of the
     *                        original icon as long as the flattened version looks the same.
     */
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        return info.getIcon(iconDpi);
    }
}
