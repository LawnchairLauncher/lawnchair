/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.shared.recents;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;

/**
 * Temporary callbacks into SystemUI.
 */
interface ISystemUiProxy {

    /**
     * Proxies SurfaceControl.screenshotToBuffer().
     * @Removed
     * GraphicBufferCompat screenshot(in Rect sourceCrop, int width, int height, int minLayer,
     *             int maxLayer, boolean useIdentityTransform, int rotation) = 0;
     */

    /**
     * Begins screen pinning on the provided {@param taskId}.
     */
    void startScreenPinning(int taskId) = 1;

    /**
     * Enables/disables launcher/overview interaction features {@link InteractionType}.
     * @deprecated
     */
    void setInteractionState(int flags) = 4;

    /**
     * Notifies SystemUI that split screen has been invoked.
     */
    void onSplitScreenInvoked() = 5;

    /**
     * Notifies SystemUI that Overview is shown.
     */
    void onOverviewShown(boolean fromHome) = 6;

    /**
     * Get the secondary split screen app's rectangle when not minimized.
     */
    Rect getNonMinimizedSplitScreenSecondaryBounds() = 7;

    /**
     * Control the {@param alpha} of the back button in the navigation bar and {@param animate} if
     * needed from current value
     * @deprecated
     */
    void setBackButtonAlpha(float alpha, boolean animate) = 8;

    /**
     * Control the {@param alpha} of the option nav bar button (back-button in 2 button mode
     * and home bar in no-button mode) and {@param animate} if needed from current value
     */
    void setNavBarButtonAlpha(float alpha, boolean animate) = 19;

    /**
     * Proxies motion events from the homescreen UI to the status bar. Only called when
     * swipe down is detected on WORKSPACE. The sender guarantees the following order of events on
     * the tracking pointer.
     *
     * Normal gesture: DOWN, MOVE/POINTER_DOWN/POINTER_UP)*, UP or CANCLE
     */
    void onStatusBarMotionEvent(in MotionEvent event) = 9;

    /**
     * Proxies the assistant gesture's progress started from navigation bar.
     */
    void onAssistantProgress(float progress) = 12;

    /**
    * Proxies the assistant gesture fling velocity (in pixels per millisecond) upon completion.
    * Velocity is 0 for drag gestures.
    */
    void onAssistantGestureCompletion(float velocity) = 18;

    /**
     * Start the assistant.
     */
    void startAssistant(in Bundle bundle) = 13;

    /**
     * Creates a new gesture monitor
     */
    Bundle monitorGestureInput(String name, int displayId) = 14;

    /**
     * Notifies that the accessibility button in the system's navigation area has been clicked
     */
    void notifyAccessibilityButtonClicked(int displayId) = 15;

    /**
     * Notifies that the accessibility button in the system's navigation area has been long clicked
     */
    void notifyAccessibilityButtonLongClicked() = 16;

    /**
     * Ends the system screen pinning.
     */
    void stopScreenPinning() = 17;
}
