/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.plugins;

import com.android.systemui.plugins.annotations.Requires;

import android.content.Context;

/**
 * Plugins are separate APKs that
 * are expected to implement interfaces provided by SystemUI.  Their
 * code is dynamically loaded into the SysUI process which can allow
 * for multiple prototypes to be created and run on a single android
 * build.
 *
 * PluginLifecycle:
 * <pre class="prettyprint">
 *
 * plugin.onCreate(Context sysuiContext, Context pluginContext);
 * --- This is always called before any other calls
 *
 * pluginListener.onPluginConnected(Plugin p);
 * --- This lets the plugin hook know that a plugin is now connected.
 *
 * ** Any other calls back and forth between sysui/plugin **
 *
 * pluginListener.onPluginDisconnected(Plugin p);
 * --- Lets the plugin hook know that it should stop interacting with
 *     this plugin and drop all references to it.
 *
 * plugin.onDestroy();
 * --- Finally the plugin can perform any cleanup to ensure that its not
 *     leaking into the SysUI process.
 *
 * Any time a plugin APK is updated the plugin is destroyed and recreated
 * to load the new code/resources.
 *
 * </pre>
 *
 * Creating plugin hooks:
 *
 * To create a plugin hook, first create an interface in
 * frameworks/base/packages/SystemUI/plugin that extends Plugin.
 * Include in it any hooks you want to be able to call into from
 * sysui and create callback interfaces for anything you need to
 * pass through into the plugin.
 *
 * Then to attach to any plugins simply add a plugin listener and
 * onPluginConnected will get called whenever new plugins are installed,
 * updated, or enabled.  Like this example from SystemUIApplication:
 *
 * <pre class="prettyprint">
 * {@literal
 * PluginManager.getInstance(this).addPluginListener(OverlayPlugin.COMPONENT,
 *        new PluginListener<OverlayPlugin>() {
 *        @Override
 *        public void onPluginConnected(OverlayPlugin plugin) {
 *            StatusBar phoneStatusBar = getComponent(StatusBar.class);
 *            if (phoneStatusBar != null) {
 *                plugin.setup(phoneStatusBar.getStatusBarWindow(),
 *                phoneStatusBar.getNavigationBarView());
 *            }
 *        }
 * }, OverlayPlugin.VERSION, true /* Allow multiple plugins *\/);
 * }
 * </pre>
 * Note the VERSION included here.  Any time incompatible changes in the
 * interface are made, this version should be changed to ensure old plugins
 * aren't accidentally loaded.  Since the plugin library is provided by
 * SystemUI, default implementations can be added for new methods to avoid
 * version changes when possible.
 *
 * Implementing a Plugin:
 *
 * See the ExamplePlugin for an example Android.mk on how to compile
 * a plugin.  Note that SystemUILib is not static for plugins, its classes
 * are provided by SystemUI.
 *
 * Plugin security is based around a signature permission, so plugins must
 * hold the following permission in their manifest.
 *
 * <pre class="prettyprint">
 * {@literal
 * <uses-permission android:name="com.android.systemui.permission.PLUGIN" />
 * }
 * </pre>
 *
 * A plugin is found through a querying for services, so to let SysUI know
 * about it, create a service with a name that points at your implementation
 * of the plugin interface with the action accompanying it:
 *
 * <pre class="prettyprint">
 * {@literal
 * <service android:name=".TestOverlayPlugin">
 *    <intent-filter>
 *        <action android:name="com.android.systemui.action.PLUGIN_COMPONENT" />
 *    </intent-filter>
 * </service>
 * }
 * </pre>
 */
public interface Plugin {

    /**
     * @deprecated
     * @see Requires
     */
    default int getVersion() {
        // Default of -1 indicates the plugin supports the new Requires model.
        return -1;
    }

    default void onCreate(Context sysuiContext, Context pluginContext) {
    }

    default void onDestroy() {
    }
}
