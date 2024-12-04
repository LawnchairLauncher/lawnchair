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
import android.graphics.Region;
import android.os.Bundle;
import android.view.MotionEvent;
import com.android.systemui.shared.recents.ISystemUiProxy;

// Next ID: 29
oneway interface IOverviewProxy {

    void onActiveNavBarRegionChanges(in Region activeRegion) = 11;

    void onInitialize(in Bundle params) = 12;

    /**
     * Sent when overview button is pressed to toggle show/hide of overview.
     */
    void onOverviewToggle() = 6;

    /**
     * Sent when overview is to be shown.
     */
    void onOverviewShown(boolean triggeredFromAltTab) = 7;

    /**
     * Sent when overview is to be hidden.
     */
    void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) = 8;

    /**
     * Sent when device assistant changes its default assistant whether it is available or not.
     * @param longPressHomeEnabled if 3-button nav assistant can be invoked or not
     */
    void onAssistantAvailable(boolean available, boolean longPressHomeEnabled) = 13;

    /**
     * Sent when the assistant changes how visible it is to the user.
     */
    void onAssistantVisibilityChanged(float visibility) = 14;

    /**
     * Sent when the assistant has been invoked with the given type (defined in AssistManager) and
     * should be shown. This method should be used if SystemUiProxy#setAssistantOverridesRequested
     * was previously called including this invocation type.
     */
    void onAssistantOverrideInvoked(int invocationType) = 28;

    /**
     * Sent when some system ui state changes.
     */
    void onSystemUiStateChanged(long stateFlags) = 16;

    /**
     * Sent when suggested rotation button could be shown
     */
    void onRotationProposal(int rotation, boolean isValid) = 18;

    /**
     * Sent when disable flags change
     */
    void disable(int displayId, int state1, int state2, boolean animate) = 19;

    /**
     * Sent when behavior changes. See WindowInsetsController#@Behavior
     */
    void onSystemBarAttributesChanged(int displayId, int behavior) = 20;

    /**
     * Sent when the desired dark intensity of the nav buttons has changed
     */
    void onNavButtonsDarkIntensityChanged(float darkIntensity) = 22;

    /**
     * Sent when when navigation bar luma sampling is enabled or disabled.
     */
    void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) = 23;

    /**
     * Sent when split keyboard shortcut is triggered to enter stage split.
     */
    void enterStageSplitFromRunningApp(boolean leftOrTop) = 25;

    /**
     * Sent when the task bar stash state is toggled.
     */
    void onTaskbarToggled() = 27;
}
