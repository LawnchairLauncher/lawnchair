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
import android.content.Context;
import android.os.Build;
import android.provider.DeviceConfig;

import com.android.launcher3.config.FeatureFlags.DebugFlag;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.P)
public class DeviceFlag extends DebugFlag {

    public static final String NAMESPACE_LAUNCHER = "launcher";

    private final boolean mDefaultValueInCode;
    ArrayList<Runnable> mListeners;

    public DeviceFlag(String key, boolean defaultValue, String description) {
        super(key, getDeviceValue(key, defaultValue), description);
        mDefaultValueInCode = defaultValue;
    }

    @Override
    protected StringBuilder appendProps(StringBuilder src) {
        return super.appendProps(src).append(", mDefaultValueInCode=").append(mDefaultValueInCode);
    }

    @Override
    public void initialize(Context context) {
        super.initialize(context);
        if (mListeners == null) {
            mListeners = new ArrayList<>();
            registerDeviceConfigChangedListener(context);
        }
    }

    @Override
    public void addChangeListener(Context context, Runnable r) {
        if (mListeners == null) {
            initialize(context);
        }
        mListeners.add(r);
    }

    private void registerDeviceConfigChangedListener(Context context) {
        DeviceConfig.addOnPropertiesChangedListener(
                NAMESPACE_LAUNCHER,
                context.getMainExecutor(),
                properties -> {
                    if (!NAMESPACE_LAUNCHER.equals(properties.getNamespace())
                            || !properties.getKeyset().contains(key)) {
                        return;
                    }
                    defaultValue = getDeviceValue(key, mDefaultValueInCode);
                    initialize(context);
                    for (Runnable r: mListeners) {
                        r.run();
                    }
                });
    }

    protected static boolean getDeviceValue(String key, boolean defaultValue) {
        return DeviceConfig.getBoolean(NAMESPACE_LAUNCHER, key, defaultValue);
    }
}
