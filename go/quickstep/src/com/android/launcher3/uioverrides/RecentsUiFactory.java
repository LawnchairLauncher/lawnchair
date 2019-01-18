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

package com.android.launcher3.uioverrides;

import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;

import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.util.TouchController;

/**
 * Provides recents-related {@link UiFactory} logic and classes.
 */
public final class RecentsUiFactory {

    // Scale recents takes before animating in
    private static final float RECENTS_PREPARE_SCALE = 1.33f;

    private RecentsUiFactory() {}

    /**
     * Creates and returns a touch controller for swiping recents tasks.
     *
     * @param launcher the launcher activity
     * @return the touch controller for recents tasks
     */
    public static @Nullable TouchController createTaskSwipeController(Launcher launcher) {
        // We leave all input handling to the view itself.
        return null;
    }

    /**
     * Creates and returns a touch controller for swiping from overview state to the all apps state
     * if such an action is supported.
     *
     * @param launcher the launcher activity
     * @return the touch controller for swiping from overview to all apps
     */
    public static @Nullable TouchController createOverviewToAllAppsTouchController(
            Launcher launcher) {
        // Go does not support overview to all apps transition.
        return null;
    }

    /**
     * Creates and returns the controller responsible for recents view state transitions.
     *
     * @param launcher the launcher activity
     * @return state handler for recents
     */
    public static StateHandler createRecentsViewStateController(Launcher launcher) {
        return new RecentsViewStateController(launcher);
    }

    /**
     * Prepare the recents view to animate in.
     *
     * @param launcher the launcher activity
     */
    public static void prepareToShowRecents(Launcher launcher) {
        View overview = launcher.getOverviewPanel();
        if (overview.getVisibility() != VISIBLE) {
            SCALE_PROPERTY.set(overview, RECENTS_PREPARE_SCALE);
        }
    }

    /**
     * Clean-up logic that occurs when recents is no longer in use/visible.
     *
     * @param launcher the launcher activity
     */
    public static void resetRecents(Launcher launcher) {}

    /**
     * Recents logic that triggers when launcher state changes or launcher activity stops/resumes.
     *
     * @param launcher the launcher activity
     */
    public static void onLauncherStateOrResumeChanged(Launcher launcher) {}
}
