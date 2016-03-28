package com.android.launcher3;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.compat.LauncherActivityInfoCompat;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

public class IconProvider {

    private static final boolean DBG = false;
    private static final String TAG = "IconProvider";

    protected String mSystemState;

    public IconProvider() {
        updateSystemStateString();
    }

    public static IconProvider loadByName(String className, Context context) {
        if (TextUtils.isEmpty(className)) return new IconProvider();
        if (DBG) Log.d(TAG, "Loading IconProvider: " + className);
        try {
            Class<?> cls = Class.forName(className);
            return (IconProvider) cls.getDeclaredConstructor(Context.class).newInstance(context);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | ClassCastException | NoSuchMethodException | InvocationTargetException e) {
            Log.e(TAG, "Bad IconProvider class", e);
            return new IconProvider();
        }
    }

    public void updateSystemStateString() {
        mSystemState = Locale.getDefault().toString();
    }

    public String getIconSystemState(String packageName) {
        return mSystemState;
    }


    public Drawable getIcon(LauncherActivityInfoCompat info, int iconDpi) {
        return info.getIcon(iconDpi);
    }
}
