package com.android.launcher3;

import android.text.TextUtils;
import android.util.Log;

public class BuildInfo {
    private static final boolean DBG = false;
    private static final String TAG = "BuildInfo";

    public boolean isDogfoodBuild() {
        return false;
    }

    public static BuildInfo loadByName(String className) {
        if (TextUtils.isEmpty(className)) return new BuildInfo();

        if (DBG) Log.d(TAG, "Loading BuildInfo: " + className);
        try {
            Class<?> cls = Class.forName(className);
            return (BuildInfo) cls.newInstance();
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Bad BuildInfo class", e);
        } catch (InstantiationException e) {
            Log.e(TAG, "Bad BuildInfo class", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Bad BuildInfo class", e);
        } catch (ClassCastException e) {
            Log.e(TAG, "Bad BuildInfo class", e);
        }
        return new BuildInfo();
    }
}
