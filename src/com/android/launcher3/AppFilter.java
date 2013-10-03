package com.android.launcher3;

import android.content.ComponentName;
import android.text.TextUtils;
import android.util.Log;

public abstract class AppFilter {

    private static final boolean DBG = false;
    private static final String TAG = "AppFilter";

    public abstract boolean shouldShowApp(ComponentName app);

    public static AppFilter loadByName(String className) {
        if (TextUtils.isEmpty(className)) return null;
        if (DBG) Log.d(TAG, "Loading AppFilter: " + className);
        try {
            Class<?> cls = Class.forName(className);
            return (AppFilter) cls.newInstance();
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Bad AppFilter class", e);
            return null;
        } catch (InstantiationException e) {
            Log.e(TAG, "Bad AppFilter class", e);
            return null;
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Bad AppFilter class", e);
            return null;
        } catch (ClassCastException e) {
            Log.e(TAG, "Bad AppFilter class", e);
            return null;
        }
    }

}
