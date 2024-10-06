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

import com.android.launcher3.LauncherPrefs;
import com.android.systemui.shared.plugins.PluginEnabler;

public class PluginEnablerImpl implements PluginEnabler {

    private static final String PREFIX_PLUGIN_ENABLED = "PLUGIN_ENABLED_";

    final private SharedPreferences mSharedPrefs;

    public PluginEnablerImpl(Context context) {
        mSharedPrefs = LauncherPrefs.getDevicePrefs(context);
    }

    @Override
    public void setEnabled(ComponentName component) {
        setState(component, true);
    }

    @Override
    public void setDisabled(ComponentName component, int reason) {
        setState(component, reason == ENABLED);
    }

    private void setState(ComponentName component, boolean enabled) {
        mSharedPrefs.edit().putBoolean(pluginEnabledKey(component), enabled).apply();
    }

    @Override
    public boolean isEnabled(ComponentName component) {
        return mSharedPrefs.getBoolean(pluginEnabledKey(component), true);
    }

    @Override
    public int getDisableReason(ComponentName componentName) {
        return isEnabled(componentName) ? ENABLED : DISABLED_MANUALLY;
    }

    private static String pluginEnabledKey(ComponentName cn) {
        return PREFIX_PLUGIN_ENABLED + cn.flattenToString();
    }
}
