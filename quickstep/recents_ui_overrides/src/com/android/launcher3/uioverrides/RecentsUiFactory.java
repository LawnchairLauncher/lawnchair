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

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.uioverrides.touchcontrollers.FlingAndHoldTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.LandscapeEdgeSwipeController;
import com.android.launcher3.uioverrides.touchcontrollers.NavBarToHomeTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.NoButtonQuickSwitchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.OverviewToAllAppsTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.QuickSwitchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.StatusBarTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TransposedQuickSwitchTouchController;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.UiThreadHelper;
import com.android.launcher3.util.UiThreadHelper.AsyncCommand;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.TouchInteractionService;
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

    public static RotationMode ROTATION_LANDSCAPE = new RotationMode(-90) {
        @Override
        public void mapRect(int left, int top, int right, int bottom, Rect out) {
            out.left = top;
            out.top = right;
            out.right = bottom;
            out.bottom = left;
        }

        @Override
        public void mapInsets(Context context, Rect insets, Rect out) {
            // If there is a display cutout, the top insets in portrait would also include the
            // cutout, which we will get as the left inset in landscape. Using the max of left and
            // top allows us to cover both cases (with or without cutout).
            if (SysUINavigationMode.getMode(context) == NO_BUTTON) {
                out.top = Math.max(insets.top, insets.left);
                out.bottom = Math.max(insets.right, insets.bottom);
                out.left = out.right = 0;
            } else {
                out.top = Math.max(insets.top, insets.left);
                out.bottom = insets.right;
                out.left = insets.bottom;
                out.right = 0;
            }
        }
    };

    public static RotationMode ROTATION_SEASCAPE = new RotationMode(90) {
        @Override
        public void mapRect(int left, int top, int right, int bottom, Rect out) {
            out.left = bottom;
            out.top = left;
            out.right = top;
            out.bottom = right;
        }

        @Override
        public void mapInsets(Context context, Rect insets, Rect out) {
            if (SysUINavigationMode.getMode(context) == NO_BUTTON) {
                out.top = Math.max(insets.top, insets.right);
                out.bottom = Math.max(insets.left, insets.bottom);
                out.left = out.right = 0;
            } else {
                out.top = Math.max(insets.top, insets.right);
                out.bottom = insets.left;
                out.right = insets.bottom;
                out.left = 0;
            }
        }

        @Override
        public int toNaturalGravity(int absoluteGravity) {
            int horizontalGravity = absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            int verticalGravity = absoluteGravity & Gravity.VERTICAL_GRAVITY_MASK;

            if (horizontalGravity == Gravity.RIGHT) {
                horizontalGravity = Gravity.LEFT;
            } else if (horizontalGravity == Gravity.LEFT) {
                horizontalGravity = Gravity.RIGHT;
            }

            if (verticalGravity == Gravity.TOP) {
                verticalGravity = Gravity.BOTTOM;
            } else if (verticalGravity == Gravity.BOTTOM) {
                verticalGravity = Gravity.TOP;
            }

            return ((absoluteGravity & ~Gravity.HORIZONTAL_GRAVITY_MASK)
                    & ~Gravity.VERTICAL_GRAVITY_MASK)
                    | horizontalGravity | verticalGravity;
        }
    };

    public static RotationMode getRotationMode(DeviceProfile dp) {
        return !dp.isVerticalBarLayout() ? RotationMode.NORMAL
                : (dp.isSeascape() ? ROTATION_SEASCAPE : ROTATION_LANDSCAPE);
    }

    public static TouchController[] createTouchControllers(Launcher launcher) {
        Mode mode = SysUINavigationMode.getMode(launcher);

        ArrayList<TouchController> list = new ArrayList<>();
        list.add(launcher.getDragController());
        if (mode == NO_BUTTON) {
            list.add(new NoButtonQuickSwitchTouchController(launcher));
            list.add(new NavBarToHomeTouchController(launcher));
            list.add(new FlingAndHoldTouchController(launcher));
        } else {
            if (launcher.getDeviceProfile().isVerticalBarLayout()) {
                list.add(new OverviewToAllAppsTouchController(launcher));
                list.add(new LandscapeEdgeSwipeController(launcher));
                if (mode.hasGestures) {
                    list.add(new TransposedQuickSwitchTouchController(launcher));
                }
            } else {
                list.add(new PortraitStatesTouchController(launcher,
                        mode.hasGestures /* allowDragToOverview */));
                if (mode.hasGestures) {
                    list.add(new QuickSwitchTouchController(launcher));
                }
            }
        }

        if (FeatureFlags.PULL_DOWN_STATUS_BAR
                && !launcher.getDeviceProfile().isMultiWindowMode) {
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
     * Clears the swipe shared state for the current swipe gesture.
     */
    public static void clearSwipeSharedState(boolean finishAnimation) {
        TouchInteractionService.getSwipeSharedState().clearAllState(finishAnimation);
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
