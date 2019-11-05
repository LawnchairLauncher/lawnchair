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
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.HotseatPredictionController;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.uioverrides.touchcontrollers.FlingAndHoldTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.LandscapeEdgeSwipeController;
import com.android.launcher3.uioverrides.touchcontrollers.NavBarToHomeTouchController;
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
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.RecentsView;

import java.util.ArrayList;
import java.util.stream.Stream;

public class QuickstepLauncher extends BaseQuickstepLauncher {

    public static final boolean GO_LOW_RAM_RECENTS_ENABLED = false;
    /**
     * Reusable command for applying the shelf height on the background thread.
     */
    public static final AsyncCommand SET_SHELF_HEIGHT = (context, arg1, arg2) ->
            SystemUiProxy.INSTANCE.get(context).setShelfHeight(arg1 != 0, arg2);
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
    private HotseatPredictionController mHotseatPredictionController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FeatureFlags.ENABLE_HYBRID_HOTSEAT.get()) {
            mHotseatPredictionController = new HotseatPredictionController(this);
        }
    }

    @Override
    protected RotationMode getFakeRotationMode(DeviceProfile dp) {
        return !dp.isVerticalBarLayout() ? RotationMode.NORMAL
                : (dp.isSeascape() ? ROTATION_SEASCAPE : ROTATION_LANDSCAPE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onStateOrResumeChanged();
    }

    @Override
    protected void onActivityFlagsChanged(int changeBits) {
        super.onActivityFlagsChanged(changeBits);

        if ((changeBits & (ACTIVITY_STATE_DEFERRED_RESUMED | ACTIVITY_STATE_STARTED
                | ACTIVITY_STATE_USER_ACTIVE | ACTIVITY_STATE_TRANSITION_ACTIVE)) != 0
                && (getActivityFlags() & ACTIVITY_STATE_TRANSITION_ACTIVE) == 0) {
            onStateOrResumeChanged();
        }
    }

    @Override
    public Stream<SystemShortcut.Factory> getSupportedShortcuts() {
        if (mHotseatPredictionController != null) {
            return Stream.concat(super.getSupportedShortcuts(),
                    Stream.of(mHotseatPredictionController));
        } else {
            return super.getSupportedShortcuts();
        }
    }

    /**
     * Recents logic that triggers when launcher state changes or launcher activity stops/resumes.
     */
    private void onStateOrResumeChanged() {
        LauncherState state = getStateManager().getState();
        DeviceProfile profile = getDeviceProfile();
        boolean visible = (state == NORMAL || state == OVERVIEW) && isUserActive()
                && !profile.isVerticalBarLayout();
        UiThreadHelper.runAsyncCommand(this, SET_SHELF_HEIGHT, visible ? 1 : 0,
                profile.hotseatBarSizePx);
        if (state == NORMAL) {
            ((RecentsView) getOverviewPanel()).setSwipeDownShouldLaunchApp(false);
        }
    }

    @Override
    public void finishBindingItems(int pageBoundFirst) {
        super.finishBindingItems(pageBoundFirst);
        if (mHotseatPredictionController != null) {
            mHotseatPredictionController.fillGapsWithPrediction(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHotseatPredictionController != null) {
            mHotseatPredictionController.destroy();
        }
    }

    @Override
    public TouchController[] createTouchControllers() {
        Mode mode = SysUINavigationMode.getMode(this);

        ArrayList<TouchController> list = new ArrayList<>();
        list.add(getDragController());
        if (mode == NO_BUTTON) {
            list.add(new QuickSwitchTouchController(this));
            list.add(new NavBarToHomeTouchController(this));
            list.add(new FlingAndHoldTouchController(this));
        } else {
            if (getDeviceProfile().isVerticalBarLayout()) {
                list.add(new OverviewToAllAppsTouchController(this));
                list.add(new LandscapeEdgeSwipeController(this));
                if (mode.hasGestures) {
                    list.add(new TransposedQuickSwitchTouchController(this));
                }
            } else {
                list.add(new PortraitStatesTouchController(this,
                        mode.hasGestures /* allowDragToOverview */));
                if (mode.hasGestures) {
                    list.add(new QuickSwitchTouchController(this));
                }
            }
        }

        if (!getDeviceProfile().isMultiWindowMode) {
            list.add(new StatusBarTouchController(this));
        }

        list.add(new LauncherTaskViewController(this));
        return list.toArray(new TouchController[list.size()]);
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
