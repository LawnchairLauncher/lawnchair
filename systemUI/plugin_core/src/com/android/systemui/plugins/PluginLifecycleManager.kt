/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.systemui.plugins

import android.content.ComponentName

/**
 * Provides the ability for consumers to control plugin lifecycle.
 *
 * @param [T] is the target plugin type
 */
interface PluginLifecycleManager<T : Plugin> {
    /** Returns the ComponentName of the target plugin. Maybe be called when not loaded. */
    val componentName: ComponentName

    /** Returns the package name of the target plugin. May be called when not loaded. */
    val packageName: String

    /** Returns the currently loaded plugin instance (if plugin is loaded) */
    val plugin: T?

    /** Returns true if the plugin is currently loaded */
    val isLoaded: Boolean
        get() = plugin != null

    /**
     * Loads and creates the plugin instance if it does not exist.
     *
     * This will trigger [PluginListener.onPluginLoaded] with the newly created plugin instance if
     * it did not already exist. Otherwise it will do nothing.
     */
    fun loadPlugin()

    /**
     * Unloads and destroys the plugin instance if it exists.
     *
     * This will trigger [PluginListener.onPluginUnloaded] if a concrete plugin instance existed
     * when this call was made. Otherwise it will do nothing.
     */
    fun unloadPlugin()
}
