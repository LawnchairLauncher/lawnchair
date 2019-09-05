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
import com.android.systemui.shared.plugins.PluginManager;
import com.android.systemui.shared.plugins.PluginManagerImpl;
import com.android.systemui.shared.plugins.PluginPrefs;

import java.util.Set;

public class PluginManagerWrapper {

    public static final MainThreadInitializedObject<PluginManagerWrapper> INSTANCE =
            new MainThreadInitializedObject<>(PluginManagerWrapper::new);

    public static final String PLUGIN_CHANGED = PluginManager.PLUGIN_CHANGED;

    private final Context mContext;
    private final PluginManager mPluginManager;
    private final PluginEnablerImpl mPluginEnabler;

    private PluginManagerWrapper(Context c) {
        mContext = c;
        PluginInitializerImpl pluginInitializer  = new PluginInitializerImpl();
        mPluginManager = new PluginManagerImpl(c, pluginInitializer);
        mPluginEnabler = pluginInitializer.getPluginEnabler(c);
    }

    public PluginEnablerImpl getPluginEnabler() {
        return mPluginEnabler;
    }

    public void addPluginListener(PluginListener<? extends Plugin> listener, Class<?> pluginClass) {
        addPluginListener(listener, pluginClass, false);
    }

    public void addPluginListener(PluginListener<? extends Plugin> listener, Class<?> pluginClass,
            boolean allowMultiple) {
        mPluginManager.addPluginListener(listener, pluginClass, allowMultiple);
    }

    public void removePluginListener(PluginListener<? extends Plugin> listener) {
        mPluginManager.removePluginListener(listener);
    }

    public Set<String> getPluginActions() {
        return new PluginPrefs(mContext).getPluginList();
    }

    /**
     * Returns the string key used to store plugin enabled/disabled setting
     */
    public static String pluginEnabledKey(ComponentName cn) {
        return PluginEnablerImpl.pluginEnabledKey(cn);
    }

    public static boolean hasPlugins(Context context) {
        return PluginPrefs.hasPlugins(context);
    }
}
