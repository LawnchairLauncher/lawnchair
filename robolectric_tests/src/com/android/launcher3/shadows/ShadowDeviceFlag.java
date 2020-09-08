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

package com.android.launcher3.shadows;

import android.content.Context;

import com.android.launcher3.uioverrides.DeviceFlag;
import com.android.launcher3.util.LooperExecutor;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Shadow for {@link LooperExecutor} to provide reset functionality for static executors.
 */
@Implements(value = DeviceFlag.class, isInAndroidSdk = false)
public class ShadowDeviceFlag {

    /**
     * Mock change listener as it uses internal system classes not available to robolectric
     */
    @Implementation
    protected void addChangeListener(Context context, Runnable r) { }

    @Implementation
    protected static boolean getDeviceValue(String key, boolean defaultValue) {
        return defaultValue;
    }
}
