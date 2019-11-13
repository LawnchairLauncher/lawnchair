/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this interface to receive a callback when the user swipes right
 * to left on the gesture area. It won't fire if the user has quick switched to a previous app
 * (swiped right) and the current app isn't yet the active one (i.e., if swiping left would take
 * the user to a more recent app).
 */
@ProvidesInterface(action = com.android.systemui.plugins.OverscrollPlugin.ACTION,
        version = com.android.systemui.plugins.OverlayPlugin.VERSION)
public interface OverscrollPlugin extends Plugin {

    String ACTION = "com.android.systemui.action.PLUGIN_LAUNCHER_OVERSCROLL";
    int VERSION = 1;

    String DEVICE_STATE_LOCKED = "Locked";
    String DEVICE_STATE_LAUNCHER = "Launcher";
    String DEVICE_STATE_APP = "App";
    String DEVICE_STATE_UNKNOWN = "Unknown";

    /**
     * Called when the user completed a right to left swipe in the gesture area.
     *
     * @param deviceState One of the DEVICE_STATE_* constants.
     */
    void onOverscroll(String deviceState);
}
