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

import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.MotionEvent;

import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.RemoteTransitionCompat;

/**
 * Temporary callbacks into SystemUI.
 */
interface ISystemUiProxy {

    /**
     * Begins screen pinning on the provided {@param taskId}.
     */
    void startScreenPinning(int taskId) = 1;

    /**
     * Notifies SystemUI that Overview is shown.
     */
    void onOverviewShown(boolean fromHome) = 6;

    /**
     * Get the secondary split screen app's rectangle when not minimized.
     */
    Rect getNonMinimizedSplitScreenSecondaryBounds() = 7;

    /**
     * Control the {@param alpha} of the option nav bar button (back-button in 2 button mode
     * and home handle & background in gestural mode).  The {@param animate} is currently only
     * supported for 2 button mode.
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

    /**
     * Handle the provided image as if it was a screenshot.
     *
     * Deprecated, use handleImageBundleAsScreenshot with image bundle and UserTask
     * @deprecated
     */
    void handleImageAsScreenshot(in Bitmap screenImage, in Rect locationInScreen,
              in Insets visibleInsets, int taskId) = 21;

    /**
     * Sets the split-screen divider minimized state
     */
    void setSplitScreenMinimized(boolean minimized) = 22;

    /*
     * Notifies that the swipe-to-home (recents animation) is finished.
     */
    void notifySwipeToHomeFinished() = 23;

    /**
     * Notifies that quickstep will switch to a new task
     * @param rotation indicates which Surface.Rotation the gesture was started in
     */
    void notifyPrioritizedRotation(int rotation) = 25;

    /**
     * Handle the provided image as if it was a screenshot.
     */
    void handleImageBundleAsScreenshot(in Bundle screenImageBundle, in Rect locationInScreen,
              in Insets visibleInsets, in Task.TaskKey task) = 28;

    /**
     * Notifies to expand notification panel.
     */
    void expandNotificationPanel() = 29;

    /**
     * Notifies SystemUI to invoke Back.
     */
    void onBackPressed() = 44;

    /** Sets home rotation enabled. */
    void setHomeRotationEnabled(boolean enabled) = 45;

    /** Notifies that a swipe-up gesture has started */
    oneway void notifySwipeUpGestureStarted() = 46;

    // Next id = 47
}
