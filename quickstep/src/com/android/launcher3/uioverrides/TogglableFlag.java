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

import android.content.Context;
import android.provider.DeviceConfig;
import com.android.launcher3.config.BaseFlags.BaseTogglableFlag;

public class TogglableFlag extends BaseTogglableFlag {
    public static final String NAMESPACE_LAUNCHER = "launcher";
    public static final String TAG = "TogglableFlag";

    public TogglableFlag(String key, boolean defaultValue, String description) {
        super(key, defaultValue, description);
    }

    @Override
    public boolean getOverridenDefaultValue(boolean value) {
        return DeviceConfig.getBoolean(NAMESPACE_LAUNCHER, getKey(), value);
    }

    @Override
    public void addChangeListener(Context context, Runnable r) {
        DeviceConfig.addOnPropertiesChangedListener(
            NAMESPACE_LAUNCHER,
            context.getMainExecutor(),
            (properties) -> {
                if (!NAMESPACE_LAUNCHER.equals(properties.getNamespace())) {
                    return;
                }
                initialize(context);
                r.run();
            });
    }
}
