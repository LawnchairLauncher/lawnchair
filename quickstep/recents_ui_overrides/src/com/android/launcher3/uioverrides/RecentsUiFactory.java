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
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.uioverrides.touchcontrollers.FlingAndHoldTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.LandscapeEdgeSwipeController;
import com.android.launcher3.uioverrides.touchcontrollers.NavBarToHomeTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.OverviewToAllAppsTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.StatusBarTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.QuickSwitchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchController;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.UiThreadHelper;
import com.android.launcher3.util.UiThreadHelper.AsyncCommand;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.ArrayList;

/**
 * Provides recents-related {@link UiFactory} logic and classes.
 */
public abstract class RecentsUiFactory {

    public static final boolean GO_LOW_RAM_RECENTS_ENABLED = false;
    private static final AsyncCommand SET_SHELF_HEIGHT_CMD = (visible, height) ->
            WindowManagerWrapper.getInstance().setShelfHeight(visible != 0, height);

    // Scale recents takes before animating in
    private static final float RECENTS_PREPARE_SCALE = 1.33f;

    public static TouchController[] createTouchControllers(Launcher launcher) {
        Mode mode = SysUINavigationMode.getMode(launcher);

        ArrayList<TouchController> list = new ArrayList<>();
        list.add(launcher.getDragController());
        if (mode == Mode.NO_BUTTON) {
            list.add(new QuickSwitchTouchController(launcher));
            list.add(new NavBarToHomeTouchController(launcher));
            list.add(new FlingAndHoldTouchController(launcher));
        } else {
            if (launcher.getDeviceProfile().isVerticalBarLayout()) {
                list.add(new OverviewToAllAppsTouchController(launcher));
                list.add(new LandscapeEdgeSwipeController(launcher));
            } else {
                list.add(new PortraitStatesTouchController(launcher,
                        mode.hasGestures /* allowDragToOverview */));
                if (mode.hasGestures) {
                    list.add(new QuickSwitchTouchController(launcher));
                }
            }
        }

        if (FeatureFlags.PULL_DOWN_STATUS_BAR && Utilities.IS_DEBUG_DEVICE
                && !launcher.getDeviceProfile().isMultiWindowMode
                && !launcher.getDeviceProfile().isVerticalBarLayout()) {
            list.add(new StatusBarTouchController(launcher));
        }

        list.add(new LauncherTaskViewController(launcher));
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
        if (SysUINavigationMode.getMode(launcher) == Mode.NO_BUTTON) {
            // Overview lives on the side, so doesn't scale in from above.
            return;
        }
        RecentsView overview = launcher.getOverviewPanel();
        if (overview.getVisibility() != VISIBLE || overview.getContentAlpha() == 0) {
            SCALE_PROPERTY.set(overview, RECENTS_PREPARE_SCALE);
        }
    }

    /**
     * Clean-up logic that occurs when recents is no longer in use/visible.
     *
     * @param launcher the launcher activity
     */
    public static void resetOverview(Launcher launcher) {
        launcher.<RecentsView>getOverviewPanel().reset();
    }

    /**
     * Recents logic that triggers when launcher state changes or launcher activity stops/resumes.
     *
     * @param launcher the launcher activity
     */
    public static void onLauncherStateOrResumeChanged(Launcher launcher) {
        LauncherState state = launcher.getStateManager().getState();
        DeviceProfile profile = launcher.getDeviceProfile();
        boolean visible = (state == NORMAL || state == OVERVIEW) && launcher.isUserActive()
                && !profile.isVerticalBarLayout();
        UiThreadHelper.runAsyncCommand(launcher, SET_SHELF_HEIGHT_CMD,
                visible ? 1 : 0, profile.hotseatBarSizePx);

        if (state == NORMAL) {
            launcher.<RecentsView>getOverviewPanel().setSwipeDownShouldLaunchApp(false);
        }
    }

    private static final class LauncherTaskViewController extends
            TaskViewTouchController<Launcher> {

        LauncherTaskViewController(Launcher activity) {
            super(activity);
        }

        @Override
        protected boolean isRecentsInteractive() {
            return mActivity.isInState(OVERVIEW);
        }

        @Override
        protected void onUserControlledAnimationCreated(AnimatorPlaybackController animController) {
            mActivity.getStateManager().setCurrentUserControlledAnimation(animController);
        }
    }
}
