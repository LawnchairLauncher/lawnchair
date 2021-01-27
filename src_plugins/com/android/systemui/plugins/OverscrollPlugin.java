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

import android.view.MotionEvent;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this interface to receive a callback when the user swipes right
 * to left on the gesture area. It won't fire if the user has quick switched to a previous app
 * (swiped right) and the current app isn't yet the active one (i.e., if swiping left would take
 * the user to a more recent app).
 */
@ProvidesInterface(action = com.android.systemui.plugins.OverscrollPlugin.ACTION,
        version = com.android.systemui.plugins.OverscrollPlugin.VERSION)
public interface OverscrollPlugin extends Plugin {

    String ACTION = "com.android.systemui.action.PLUGIN_LAUNCHER_OVERSCROLL";
    int VERSION = 4;

    String DEVICE_STATE_LOCKED = "Locked";
    String DEVICE_STATE_LAUNCHER = "Launcher";
    String DEVICE_STATE_APP = "App";
    String DEVICE_STATE_UNKNOWN = "Unknown";

    /**
     * @return true if the plugin is active and will accept overscroll gestures
     */
    boolean isActive();

    /**
     * Called when a touch has been recognized as an overscroll gesture.
     * @param horizontalDistancePx Horizontal distance from the last finger location to the finger
     *                               location when it first touched the screen.
     * @param verticalDistancePx Horizontal distance from the last finger location to the finger
     *                             location when it first touched the screen.
     * @param thresholdPx Minimum distance for gesture.
     * @param flingDistanceThresholdPx Minimum distance for gesture by fling.
     * @param flingVelocityThresholdPx Minimum velocity for gesture by fling.
     * @param deviceState String representing the current device state
     * @param underlyingActivity String representing the currently active Activity
     */
    void onTouchEvent(MotionEvent event,
                      int horizontalDistancePx,
                      int verticalDistancePx,
                      int thresholdPx,
                      int flingDistanceThresholdPx,
                      int flingVelocityThresholdPx,
                      String deviceState,
                      String underlyingActivity);

    /**
     * @return `true` if overscroll gesture handling should override all other gestures.
     */
    boolean blockOtherGestures();

    /**
     * @return `true` if the overscroll gesture can pan the underlying app.
     */
    boolean allowsUnderlyingActivityOverscroll();
}
