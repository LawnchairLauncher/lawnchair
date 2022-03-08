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

package com.android.launcher3.uioverrides;

import android.annotation.TargetApi;
import android.os.Build;
import android.provider.DeviceConfig;

import com.android.launcher3.config.FeatureFlags.DebugFlag;

@TargetApi(Build.VERSION_CODES.P)
public class DeviceFlag extends DebugFlag {

    public static final String NAMESPACE_LAUNCHER = "launcher";

    private final boolean mDefaultValueInCode;

    public DeviceFlag(String key, boolean defaultValue, String description) {
        super(key, getDeviceValue(key, defaultValue), description);
        mDefaultValueInCode = defaultValue;
    }

    @Override
    protected StringBuilder appendProps(StringBuilder src) {
        return super.appendProps(src).append(", mDefaultValueInCode=").append(mDefaultValueInCode);
    }

    @Override
    public boolean get() {
        // Override this method in order to let Robolectric ShadowDeviceFlag to stub it.
        return super.get();
    }

    protected static boolean getDeviceValue(String key, boolean defaultValue) {
        return DeviceConfig.getBoolean(NAMESPACE_LAUNCHER, key, defaultValue);
    }
}
