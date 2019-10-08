/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep.util;

import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.android.launcher3.config.FeatureFlags;

/**
 * Utility class to test and check binder calls during development.
 */
public class BinderTracker {

    private static final String TAG = "BinderTracker";

    public static void start() {
        if (!FeatureFlags.IS_DOGFOOD_BUILD) {
            Log.wtf(TAG, "Accessing tracker in released code.", new Exception());
            return;
        }

        Binder.setProxyTransactListener(new Tracker());
    }

    public static void stop() {
        if (!FeatureFlags.IS_DOGFOOD_BUILD) {
            Log.wtf(TAG, "Accessing tracker in released code.", new Exception());
            return;
        }
        Binder.setProxyTransactListener(null);
    }

    private static class Tracker implements Binder.ProxyTransactListener {

        @Override
        public Object onTransactStarted(IBinder iBinder, int code) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Log.e(TAG, "Binder call on ui thread", new Exception());
            }
            return null;
        }

        @Override
        public void onTransactEnded(Object session) { }
    }
}
