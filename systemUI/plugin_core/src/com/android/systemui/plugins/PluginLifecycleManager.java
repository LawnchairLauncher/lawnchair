/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.plugins;

import android.content.ComponentName;

import java.util.function.BiConsumer;

/**
 * Provides the ability for consumers to control plugin lifecycle.
 *
 * @param <T> is the target plugin type
 */
public interface PluginLifecycleManager<T extends Plugin> {
    /** Returns the ComponentName of the target plugin. Maybe be called when not loaded. */
    ComponentName getComponentName();

    /** Returns the package name of the target plugin. May be called when not loaded. */
    String getPackage();

    /** Returns the currently loaded plugin instance (if plugin is loaded) */
    T getPlugin();

    /** Log tag and messages will be sent to the provided Consumer */
    void setLogFunc(BiConsumer<String, String> logConsumer);

    /** returns true if the plugin is currently loaded */
    default boolean isLoaded() {
        return getPlugin() != null;
    }

    /**
     * Loads and creates the plugin instance if it does not exist.
     *
     * This will trigger {@link PluginListener#onPluginLoaded} with the new instance if it did not
     * already exist.
     */
    void loadPlugin();

    /**
     * Unloads and destroys the plugin instance if it exists.
     *
     * This will trigger {@link PluginListener#onPluginUnloaded} if a concrete plugin instance
     * existed when this call was made.
     */
    void unloadPlugin();
}
