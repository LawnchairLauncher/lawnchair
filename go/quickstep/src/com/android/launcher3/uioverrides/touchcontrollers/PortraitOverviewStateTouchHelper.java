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

package com.android.launcher3.uioverrides.touchcontrollers;

import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.util.PendingAnimation;

/**
 * Helper class for {@link PortraitStatesTouchController} that determines swipeable regions and
 * animations on the overview state that depend on the recents implementation.
 */
public final class PortraitOverviewStateTouchHelper {

    public PortraitOverviewStateTouchHelper(Launcher launcher) {}

    /**
     * Whether or not {@link PortraitStatesTouchController} should intercept the touch when on the
     * overview state.
     *
     * @param ev the motion event
     * @return true if we should intercept the motion event
     */
    boolean canInterceptTouch(MotionEvent ev) {
        // Go does not support swiping to all-apps from recents.
        return false;
    }

    /**
     * Whether or not swiping down to leave overview state should return to the currently running
     * task app.
     *
     * @return true if going back should take the user to the currently running task
     */
    boolean shouldSwipeDownReturnToApp() {
        // Go does not support swiping tasks down to launch tasks from recents.
        return false;
    }

    /**
     * Create the animation for going from overview to the task app via swiping.
     *
     * @param duration how long the animation should be
     * @return the animation
     */
    PendingAnimation createSwipeDownToTaskAppAnimation(long duration) {
        // Go does not support swiping tasks down to launch tasks from recents.
        return null;
    }
}
