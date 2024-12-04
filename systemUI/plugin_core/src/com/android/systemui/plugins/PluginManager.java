/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.plugins;

import android.text.TextUtils;

import com.android.systemui.plugins.annotations.ProvidesInterface;

public interface PluginManager {

    String PLUGIN_CHANGED = "com.android.systemui.action.PLUGIN_CHANGED";

    // must be one of the channels created in NotificationChannels.java
    String NOTIFICATION_CHANNEL_ID = "ALR";

    /** Returns plugins that don't get disabled when an exceptoin occurs. */
    String[] getPrivilegedPlugins();

    /** */
    <T extends Plugin> void addPluginListener(PluginListener<T> listener, Class<T> cls);
    /** */
    <T extends Plugin> void addPluginListener(PluginListener<T> listener, Class<T> cls,
            boolean allowMultiple);
    <T extends Plugin> void addPluginListener(String action, PluginListener<T> listener,
            Class<T> cls);
    <T extends Plugin> void addPluginListener(String action, PluginListener<T> listener,
            Class<T> cls, boolean allowMultiple);

    void removePluginListener(PluginListener<?> listener);

    <T> boolean dependsOn(Plugin p, Class<T> cls);

    class Helper {
        public static <P> String getAction(Class<P> cls) {
            ProvidesInterface info = cls.getDeclaredAnnotation(ProvidesInterface.class);
            if (info == null) {
                throw new RuntimeException(cls + " doesn't provide an interface");
            }
            if (TextUtils.isEmpty(info.action())) {
                throw new RuntimeException(cls + " doesn't provide an action");
            }
            return info.action();
        }
    }

}
