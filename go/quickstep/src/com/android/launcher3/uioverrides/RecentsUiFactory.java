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

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.uioverrides.touchcontrollers.LandscapeEdgeSwipeController;
import com.android.launcher3.uioverrides.touchcontrollers.LandscapeStatesTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.StatusBarTouchController;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.views.IconRecentsView;

import java.util.ArrayList;

/**
 * Provides recents-related {@link UiFactory} logic and classes.
 */
public abstract class RecentsUiFactory {

    public static final boolean GO_LOW_RAM_RECENTS_ENABLED = true;
    // Scale recents takes before animating in
    private static final float RECENTS_PREPARE_SCALE = 1.33f;

    public static TouchController[] createTouchControllers(Launcher launcher) {
        ArrayList<TouchController> list = new ArrayList<>();
        list.add(launcher.getDragController());

        if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            list.add(new LandscapeStatesTouchController(launcher));
            list.add(new LandscapeEdgeSwipeController(launcher));
        } else {
            boolean allowDragToOverview = SysUINavigationMode.INSTANCE.get(launcher)
                    .getMode().hasGestures;
            list.add(new PortraitStatesTouchController(launcher, allowDragToOverview));
        }
        if (FeatureFlags.PULL_DOWN_STATUS_BAR && Utilities.IS_DEBUG_DEVICE
                && !launcher.getDeviceProfile().isMultiWindowMode
                && !launcher.getDeviceProfile().isVerticalBarLayout()) {
            list.add(new StatusBarTouchController(launcher));
        }
        return list.toArray(new TouchController[list.size()]);
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
    public static void prepareToShowOverview(Launcher launcher) {
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
    public static void resetOverview(Launcher launcher) {
        IconRecentsView recentsView = launcher.getOverviewPanel();
        recentsView.setTransitionedFromApp(false);
    }

    /**
     * Recents logic that triggers when launcher state changes or launcher activity stops/resumes.
     *
     * @param launcher the launcher activity
     */
    public static void onLauncherStateOrResumeChanged(Launcher launcher) {}
}
