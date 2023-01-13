/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context;

/**
 * Interface for listening to plugins being connected.
 */
public interface PluginListener<T extends Plugin> {
    /**
     * Called when the plugin has been loaded and is ready to be used.
     * This may be called multiple times if multiple plugins are allowed.
     * It may also be called in the future if the plugin package changes
     * and needs to be reloaded.
     */
    void onPluginConnected(T plugin, Context pluginContext);

    /**
     * Called when a plugin has been uninstalled/updated and should be removed
     * from use.
     */
    default void onPluginDisconnected(T plugin) {
        // Optional.
    }
}
