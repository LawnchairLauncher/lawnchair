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

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.os.Parcelable;

/**
 * A wrapper around platform implementation of PinItemRequestCompat until the
 * updated SDK is available.
 */
public class PinItemRequestCompat {

    public static final String EXTRA_PIN_ITEM_REQUEST = "android.content.pm.extra.PIN_ITEM_REQUEST";

    public static final int REQUEST_TYPE_SHORTCUT = 1;
    public static final int REQUEST_TYPE_APPWIDGET = 2;

    private final Parcelable mObject;

    private PinItemRequestCompat(Parcelable object) {
        mObject = object;
    }

    public int getRequestType() {
        return (Integer) invokeMethod("getRequestType");
    }

    public ShortcutInfo getShortcutInfo() {
        return (ShortcutInfo) invokeMethod("getShortcutInfo");
    }

    public AppWidgetProviderInfo getAppWidgetProviderInfo(Context context) {
        try {
            return (AppWidgetProviderInfo) mObject.getClass()
                    .getDeclaredMethod("getAppWidgetProviderInfo", Context.class)
                    .invoke(mObject, context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isValid() {
        return (Boolean) invokeMethod("isValid");
    }

    public boolean accept() {
        return (Boolean) invokeMethod("accept");
    }

    public boolean accept(Bundle options) {
        try {
            return (Boolean) mObject.getClass().getDeclaredMethod("accept", Bundle.class)
                    .invoke(mObject, options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object invokeMethod(String methodName) {
        try {
            return mObject.getClass().getDeclaredMethod(methodName).invoke(mObject);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static PinItemRequestCompat getPinItemRequest(Intent intent) {
        Parcelable extra = intent.getParcelableExtra(EXTRA_PIN_ITEM_REQUEST);
        return extra == null ? null : new PinItemRequestCompat(extra);
    }
}
