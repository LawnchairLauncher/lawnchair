/**
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

package com.android.launcher3.uioverrides;

import android.os.IBinder;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A binder proxy transaction listener for tracking non-whitelisted binder calls.
 */
public class DejankBinderTracker {
    public static void whitelistIpcs(Runnable runnable) {}

    public static <T> T whitelistIpcs(Supplier<T> supplier) {
        return  null;
    }

    public static void allowBinderTrackingInTests() {}

    public static void disallowBinderTrackingInTests() {}

    public DejankBinderTracker(BiConsumer<String, Integer> unexpectedTransactionCallback) {    }

    public void startTracking() {}

    public void stopTracking() {}

    public Object onTransactStarted(IBinder binder, int transactionCode, int flags) {
        return null;
    }

    public Object onTransactStarted(IBinder binder, int transactionCode) {
        return null;
    }

    public void onTransactEnded(Object session) {}

    public static boolean isMainThread() {
        return true;
    }
}
