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

import android.content.Context;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.shared.plugins.PluginEnabler;
import com.android.systemui.shared.plugins.PluginInitializer;
import com.android.systemui.shared.plugins.PluginManager;
import com.android.systemui.shared.plugins.PluginManagerImpl;

public class PluginManagerWrapper {

    public static final MainThreadInitializedObject<PluginManagerWrapper> INSTANCE =
            new MainThreadInitializedObject<>(PluginManagerWrapper::new);

    private final PluginManager mPluginManager;
    private final PluginEnabler mPluginEnabler;

    private PluginManagerWrapper(Context c) {
        PluginInitializer pluginInitializer  = new PluginInitializerImpl();
        mPluginManager = new PluginManagerImpl(c, pluginInitializer);
        mPluginEnabler = pluginInitializer.getPluginEnabler(c);
    }

    PluginEnabler getPluginEnabler() {
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
}
