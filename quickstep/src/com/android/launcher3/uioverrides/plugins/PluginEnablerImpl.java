/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.launcher3.uioverrides.plugins;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.launcher3.Utilities;
import com.android.systemui.shared.plugins.PluginEnabler;

public class PluginEnablerImpl implements PluginEnabler {

    final private SharedPreferences mSharedPrefs;

    public PluginEnablerImpl(Context context) {
        mSharedPrefs = Utilities.getDevicePrefs(context);
    }

    @Override
    public void setEnabled(ComponentName component, boolean enabled) {
        mSharedPrefs.edit().putBoolean(toPrefString(component), enabled).apply();
    }

    @Override
    public boolean isEnabled(ComponentName component) {
        return mSharedPrefs.getBoolean(toPrefString(component), true);
    }

    private String toPrefString(ComponentName component) {
        return "PLUGIN_ENABLED_" + component.flattenToString();
    }
}
