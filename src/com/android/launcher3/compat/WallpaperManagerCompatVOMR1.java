/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.compat;

import android.annotation.TargetApi;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@TargetApi(Build.VERSION_CODES.O)
public class WallpaperManagerCompatVOMR1 extends WallpaperManagerCompat {

    private static final String TAG = "WMCompatVOMR1";

    private final WallpaperManager mWm;

    private final Class mOCLClass;
    private final Method mAddOCLMethod;

    private final Method mWCGetMethod;
    private final Method mWCGetPrimaryColorMethod;
    private final Method mWCGetSecondaryColorMethod;
    private final Method mWCGetTertiaryColorMethod;
    private final Method mWCColorHintsMethod;

    WallpaperManagerCompatVOMR1(Context context) throws Exception {
        mWm = context.getSystemService(WallpaperManager.class);

        mOCLClass = Class.forName("android.app.WallpaperManager$OnColorsChangedListener");
        mAddOCLMethod = WallpaperManager.class.getDeclaredMethod(
                "addOnColorsChangedListener", mOCLClass, Handler.class);
        mWCGetMethod = WallpaperManager.class.getDeclaredMethod("getWallpaperColors", int.class);
        Class wallpaperColorsClass = mWCGetMethod.getReturnType();
        mWCGetPrimaryColorMethod = wallpaperColorsClass.getDeclaredMethod("getPrimaryColor");
        mWCGetSecondaryColorMethod = wallpaperColorsClass.getDeclaredMethod("getSecondaryColor");
        mWCGetTertiaryColorMethod = wallpaperColorsClass.getDeclaredMethod("getTertiaryColor");
        mWCColorHintsMethod = wallpaperColorsClass.getDeclaredMethod("getColorHints");
    }

    @Nullable
    @Override
    public WallpaperColorsCompat getWallpaperColors(int which) {
        try {
            return convertColorsObject(mWCGetMethod.invoke(mWm, which));
        } catch (Exception e) {
            Log.e(TAG, "Error calling wallpaper API", e);
            return null;
        }
    }

    @Override
    public void addOnColorsChangedListener(final OnColorsChangedListenerCompat listener) {
        Object onChangeListener = Proxy.newProxyInstance(
                WallpaperManager.class.getClassLoader(),
                new Class[]{mOCLClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object o, Method method, Object[] objects)
                            throws Throwable {
                        String methodName = method.getName();
                        if ("onColorsChanged".equals(methodName)) {
                            listener.onColorsChanged(
                                    convertColorsObject(objects[0]), (Integer) objects[1]);
                        } else if ("toString".equals(methodName)) {
                            return listener.toString();
                        }
                        return null;
                    }
                });
        try {
            mAddOCLMethod.invoke(mWm, onChangeListener, null);
        } catch (Exception e) {
            Log.e(TAG, "Error calling wallpaper API", e);
        }
    }

    private WallpaperColorsCompat convertColorsObject(Object colors) throws Exception {
        if (colors == null) {
            return null;
        }
        Color primary = (Color) mWCGetPrimaryColorMethod.invoke(colors);
        Color secondary = (Color) mWCGetSecondaryColorMethod.invoke(colors);
        Color tertiary = (Color) mWCGetTertiaryColorMethod.invoke(colors);
        int primaryVal = primary != null ? primary.toArgb() : 0;
        int secondaryVal = secondary != null ? secondary.toArgb() : 0;
        int tertiaryVal = tertiary != null ? tertiary.toArgb() : 0;
        int colorHints = (Integer) mWCColorHintsMethod.invoke(colors);
        return new WallpaperColorsCompat(primaryVal, secondaryVal, tertiaryVal, colorHints);
    }
}
