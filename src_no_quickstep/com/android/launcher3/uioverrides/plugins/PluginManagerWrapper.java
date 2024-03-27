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

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.PluginListener;

import java.util.Collections;
import java.util.Set;

import androidx.preference.PreferenceDataStore;

public class PluginManagerWrapper {

    public static final MainThreadInitializedObject<PluginManagerWrapper> INSTANCE =
            new MainThreadInitializedObject<>(PluginManagerWrapper::new);

    private static final String PREFIX_PLUGIN_ENABLED = "PLUGIN_ENABLED_";
    public static final String PLUGIN_CHANGED = "com.android.systemui.action.PLUGIN_CHANGED";

    private PluginManagerWrapper(Context c) {
    }

    public void addPluginListener(PluginListener<? extends Plugin> listener, Class<?> pluginClass) {
    }

    public void addPluginListener(PluginListener<? extends Plugin> listener, Class<?> pluginClass,
            boolean allowMultiple) {
    }

    public void removePluginListener(PluginListener<? extends Plugin> listener) { }

    public Set<String> getPluginActions() {
        return Collections.emptySet();
    }

    public PreferenceDataStore getPluginEnabler() {
        return new PreferenceDataStore() { };
    }

    public static String pluginEnabledKey(ComponentName cn) {
        return PREFIX_PLUGIN_ENABLED + cn.flattenToString();
    }

    public static boolean hasPlugins(Context context) {
        return false;
    }
}
