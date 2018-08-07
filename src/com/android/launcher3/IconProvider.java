package com.android.launcher3;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.util.Locale;

public class IconProvider {

    protected String mSystemState;

    public static IconProvider newInstance(Context context) {
        IconProvider provider = Utilities.getOverrideObject(
                IconProvider.class, context, R.string.icon_provider_class);
        provider.updateSystemStateString(context);
        return provider;
    }

    public IconProvider() { }

    public void updateSystemStateString(Context context) {
        final String locale;
        if (Utilities.ATLEAST_NOUGAT) {
            locale = context.getResources().getConfiguration().getLocales().toLanguageTags();
        } else {
            locale = Locale.getDefault().toString();
        }

        mSystemState = locale + "," + Build.VERSION.SDK_INT;
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
