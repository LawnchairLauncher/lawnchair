/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.util;

import android.os.Looper;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.config.ProviderConfig;

/**
 * A set of utility methods for thread verification.
 */
public class Preconditions {

    public static void assertNotNull(Object o) {
        if (ProviderConfig.IS_DOGFOOD_BUILD && o == null) {
            throw new IllegalStateException();
        }
    }

    public static void assertWorkerThread() {
        if (ProviderConfig.IS_DOGFOOD_BUILD && !isSameLooper(LauncherModel.getWorkerLooper())) {
            throw new IllegalStateException();
        }
    }

    public static void assertUIThread() {
        if (ProviderConfig.IS_DOGFOOD_BUILD && !isSameLooper(Looper.getMainLooper())) {
            throw new IllegalStateException();
        }
    }

    public static void assertNonUiThread() {
        if (ProviderConfig.IS_DOGFOOD_BUILD && isSameLooper(Looper.getMainLooper())) {
            throw new IllegalStateException();
        }
    }

    private static boolean isSameLooper(Looper looper) {
        return Looper.myLooper() == looper;
    }
}
