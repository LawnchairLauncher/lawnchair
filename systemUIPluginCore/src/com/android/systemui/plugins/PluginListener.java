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
 * Interface for listening to plugins being connected and disconnected.
 *
 * The call order for a plugin is
 *  1) {@link #onPluginAttached}
 *          Called when a new plugin is added to the device, or an existing plugin was replaced by
 *          the package manager. Will only be called once per package manager event. If multiple
 *          non-conflicting packages which have the same plugin interface are installed on the
 *          device, then this method can be called multiple times with different instances of
 *          {@link PluginLifecycleManager} (as long as `allowMultiple` was set to true when the
 *          listener was registered with {@link PluginManager#addPluginListener}).
 *  2) {@link #onPluginLoaded}
 *          Called whenever a new instance of the plugin object is created and ready for use. Can be
 *          called multiple times per {@link PluginLifecycleManager}, but will always pass a newly
 *          created plugin object. {@link #onPluginUnloaded} with the previous plugin object will
 *          be called before another call to {@link #onPluginLoaded} is made. This method will be
 *          called once automatically after {@link #onPluginAttached}. Besides the initial call,
 *          {@link #onPluginLoaded} will occur due to {@link PluginLifecycleManager#loadPlugin}.
 *  3) {@link #onPluginUnloaded}
 *          Called when a request to unload the plugin has been received. This can be triggered from
 *          a related call to {@link PluginLifecycleManager#unloadPlugin} or for any reason that
 *          {@link #onPluginDetached} would be triggered.
 *  4) {@link #onPluginDetached}
 *          Called when the package is removed from the device, disabled, or replaced due to an
 *          external trigger. These are events from the android package manager.
 *
 * @param <T> is the target plugin type
 */
public interface PluginListener<T extends Plugin> {
    /**
     * Called when the plugin has been loaded and is ready to be used.
     * This may be called multiple times if multiple plugins are allowed.
     * It may also be called in the future if the plugin package changes
     * and needs to be reloaded.
     *
     * @deprecated Migrate to {@link #onPluginLoaded} or {@link #onPluginAttached}
     */
    @Deprecated
    default void onPluginConnected(T plugin, Context pluginContext) {
        // Optional
    }

    /**
     * Called when the plugin is first attached to the host application. {@link #onPluginLoaded}
     * will be automatically called as well when first attached if true is returned. This may be
     * called multiple times if multiple plugins are allowed. It may also be called in the future
     * if the plugin package changes and needs to be reloaded. Each call to
     * {@link #onPluginAttached} will provide a new or different {@link PluginLifecycleManager}.
     *
     * @return returning true will immediately load the plugin and call onPluginLoaded with the
     *   created object. false will skip loading, but the listener can load it at any time using the
     *   provided PluginLifecycleManager. Loading plugins immediately is the default behavior.
     */
    default boolean onPluginAttached(PluginLifecycleManager<T> manager) {
        // Optional
        return true;
    }

    /**
     * Called when a plugin has been uninstalled/updated and should be removed
     * from use.
     *
     * @deprecated Migrate to {@link #onPluginDetached} or {@link #onPluginUnloaded}
     */
    @Deprecated
    default void onPluginDisconnected(T plugin) {
        // Optional.
    }

    /**
     * Called when the plugin has been detached from the host application. Implementers should no
     * longer attempt to reload it via this {@link PluginLifecycleManager}. If the package was
     * updated and not removed, then {@link #onPluginAttached} will be called again when the updated
     * package is available.
     */
    default void onPluginDetached(PluginLifecycleManager<T> manager) {
        // Optional.
    }

    /**
     * Called when the plugin is loaded into the host's process and is available for use. This can
     * happen several times if clients are using {@link PluginLifecycleManager} to manipulate a
     * plugin's load state. Each call to {@link #onPluginLoaded} will have a matched call to
     * {@link #onPluginUnloaded} when that plugin object should no longer be used.
     */
    default void onPluginLoaded(
            T plugin,
            Context pluginContext,
            PluginLifecycleManager<T> manager
    ) {
        // Optional, default to deprecated version
        onPluginConnected(plugin, pluginContext);
    }

    /**
     * Called when the plugin should no longer be used. Listeners should clean up all references to
     * the relevant plugin so that it can be garbage collected. If the plugin object is required in
     * the future a call can be made to {@link PluginLifecycleManager#loadPlugin} to create a new
     * plugin object and trigger {@link #onPluginLoaded}.
     */
    default void onPluginUnloaded(T plugin, PluginLifecycleManager<T> manager) {
        // Optional, default to deprecated version
        onPluginDisconnected(plugin);
    }
}