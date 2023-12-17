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

import android.view.View;

import com.android.systemui.plugins.annotations.ProvidesInterface;
import com.android.systemui.plugins.statusbar.DozeParameters;

@ProvidesInterface(action = OverlayPlugin.ACTION, version = OverlayPlugin.VERSION)
public interface OverlayPlugin extends Plugin {

    String ACTION = "com.android.systemui.action.PLUGIN_OVERLAY";
    int VERSION = 4;

    /**
     * Setup overlay plugin
     */
    void setup(View statusBar, View navBar);

    /**
     * Setup overlay plugin with callback and DozeParameters
     */
    default void setup(View statusBar, View navBar, Callback callback,
            DozeParameters dozeParameters) {
        setup(statusBar, navBar);
    }

    default boolean holdStatusBarOpen() {
        return false;
    }

    /**
     * Only called if the plugin has returned true to holdStatusBarOpen().
     */
    default void setCollapseDesired(boolean collapseDesired) {
    }

    /**
     * Used to update system ui whether to hold status bar open
     */
    interface Callback {
        void onHoldStatusBarOpenChange();
    }
}
