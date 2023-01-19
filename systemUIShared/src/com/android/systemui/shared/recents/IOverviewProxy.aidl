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
     * Sent when there was an action on one of the onboarding tips view.
     * TODO: Move this implementation to SystemUI completely
     */
    void onTip(int actionType, int viewType) = 10;

    /**
     * Sent when device assistant changes its default assistant whether it is available or not.
     */
    void onAssistantAvailable(boolean available) = 13;

    /**
     * Sent when the assistant changes how visible it is to the user.
     */
    void onAssistantVisibilityChanged(float visibility) = 14;

    /**
     * Sent when back is triggered.
     * TODO: Move this implementation to SystemUI completely
     */
    void onBackAction(boolean completed, int downX, int downY, boolean isButton,
            boolean gestureSwipeLeft) = 15;

    /**
     * Sent when some system ui state changes.
     */
    void onSystemUiStateChanged(int stateFlags) = 16;

    /**
     * Sent when the split screen is resized
     */
    void onSplitScreenSecondaryBoundsChanged(in Rect bounds, in Rect insets) = 17;

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
     * Sent when screen turned on and ready to use (blocker scrim is hidden)
     */
    void onScreenTurnedOn() = 21;

    /**
     * Sent when the desired dark intensity of the nav buttons has changed
     */
    void onNavButtonsDarkIntensityChanged(float darkIntensity) = 22;
}
