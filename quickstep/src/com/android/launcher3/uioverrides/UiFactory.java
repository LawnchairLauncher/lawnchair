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

package com.android.launcher3.uioverrides;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_HIDE_BACK_BUTTON;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.allapps.DiscoveryBounce.HOME_BOUNCE_SEEN;
import static com.android.launcher3.allapps.DiscoveryBounce.SHELF_BOUNCE_SEEN;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.util.Base64;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.QuickstepAppTransitionManagerImpl;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.UiThreadHelper;
import com.android.launcher3.util.UiThreadHelper.AsyncCommand;
import com.android.quickstep.OverviewInteractionState;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.util.RemoteFadeOutAnimationListener;
import com.android.systemui.shared.system.ActivityCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.zip.Deflater;

public class UiFactory {

    private static final AsyncCommand SET_SHELF_HEIGHT_CMD = (visible, height) ->
            WindowManagerWrapper.getInstance().setShelfHeight(visible != 0, height);

    public static TouchController[] createTouchControllers(Launcher launcher) {
        ArrayList<TouchController> list = new ArrayList<>();
        list.add(launcher.getDragController());

        TouchController overviewToAllAppsController =
                RecentsUiFactory.createOverviewToAllAppsTouchController(launcher);
        if (overviewToAllAppsController != null) {
            list.add(overviewToAllAppsController);
        }
        if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            list.add(new LandscapeEdgeSwipeController(launcher));
        } else {
            list.add(new PortraitStatesTouchController(launcher));
        }
        if (FeatureFlags.PULL_DOWN_STATUS_BAR && Utilities.IS_DEBUG_DEVICE
                && !launcher.getDeviceProfile().isMultiWindowMode
                && !launcher.getDeviceProfile().isVerticalBarLayout()) {
            list.add(new StatusBarTouchController(launcher));
        }
        TouchController taskSwipeController =
                RecentsUiFactory.createTaskSwipeController(launcher);
        if (taskSwipeController != null) {
            list.add(taskSwipeController);
        }
        return list.toArray(new TouchController[list.size()]);
    }

    public static void setOnTouchControllersChangedListener(Context context, Runnable listener) {
        OverviewInteractionState.INSTANCE.get(context).setOnSwipeUpSettingChangedListener(listener);
    }

    public static StateHandler[] getStateHandler(Launcher launcher) {
        return new StateHandler[] {launcher.getAllAppsController(), launcher.getWorkspace(),
                RecentsUiFactory.createRecentsViewStateController(launcher),
                new BackButtonAlphaHandler(launcher)};
    }

    /**
     * Sets the back button visibility based on the current state/window focus.
     */
    public static void onLauncherStateOrFocusChanged(Launcher launcher) {
        boolean shouldBackButtonBeHidden = launcher != null
                && launcher.getStateManager().getState().hideBackButton
                && launcher.hasWindowFocus();
        if (shouldBackButtonBeHidden) {
            // Show the back button if there is a floating view visible.
            shouldBackButtonBeHidden = AbstractFloatingView.getTopOpenViewWithType(launcher,
                    TYPE_ALL & ~TYPE_HIDE_BACK_BUTTON) == null;
        }
        OverviewInteractionState.INSTANCE.get(launcher)
                .setBackButtonAlpha(shouldBackButtonBeHidden ? 0 : 1, true /* animate */);
    }

    public static void resetOverview(Launcher launcher) {
        RecentsUiFactory.resetRecents(launcher);
    }

    public static void onCreate(Launcher launcher) {
        if (!launcher.getSharedPrefs().getBoolean(HOME_BOUNCE_SEEN, false)) {
            launcher.getStateManager().addStateListener(new LauncherStateManager.StateListener() {
                @Override
                public void onStateSetImmediately(LauncherState state) {
                    onStateTransitionComplete(state);
                }

                @Override
                public void onStateTransitionStart(LauncherState toState) {
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    boolean swipeUpEnabled = OverviewInteractionState.INSTANCE.get(launcher)
                            .isSwipeUpGestureEnabled();
                    LauncherState prevState = launcher.getStateManager().getLastState();

                    if (((swipeUpEnabled && finalState == OVERVIEW) || (!swipeUpEnabled
                            && finalState == ALL_APPS && prevState == NORMAL))) {
                        launcher.getSharedPrefs().edit().putBoolean(HOME_BOUNCE_SEEN, true).apply();
                        launcher.getStateManager().removeStateListener(this);
                    }
                }
            });
        }

        if (!launcher.getSharedPrefs().getBoolean(SHELF_BOUNCE_SEEN, false)) {
            launcher.getStateManager().addStateListener(new LauncherStateManager.StateListener() {
                @Override
                public void onStateSetImmediately(LauncherState state) {
                    onStateTransitionComplete(state);
                }

                @Override
                public void onStateTransitionStart(LauncherState toState) {
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    LauncherState prevState = launcher.getStateManager().getLastState();

                    if (finalState == ALL_APPS && prevState == OVERVIEW) {
                        launcher.getSharedPrefs().edit().putBoolean(SHELF_BOUNCE_SEEN, true).apply();
                        launcher.getStateManager().removeStateListener(this);
                    }
                }
            });
        }
    }

    public static void onEnterAnimationComplete(Context context) {
        // After the transition to home, enable the high-res thumbnail loader if it wasn't enabled
        // as a part of quickstep/scrub, so that high-res thumbnails can load the next time we
        // enter overview
        RecentsModel.INSTANCE.get(context).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    public static void onLauncherStateOrResumeChanged(Launcher launcher) {
        LauncherState state = launcher.getStateManager().getState();
        if (!OverviewInteractionState.INSTANCE.get(launcher).swipeGestureInitializing()) {
            DeviceProfile profile = launcher.getDeviceProfile();
            boolean visible = (state == NORMAL || state == OVERVIEW) && launcher.isUserActive()
                    && !profile.isVerticalBarLayout();
            UiThreadHelper.runAsyncCommand(launcher, SET_SHELF_HEIGHT_CMD,
                    visible ? 1 : 0, profile.hotseatBarSizePx);
        }

        RecentsUiFactory.onLauncherStateOrResumeChanged(launcher);
    }

    public static void onTrimMemory(Context context, int level) {
        RecentsModel model = RecentsModel.INSTANCE.get(context);
        if (model != null) {
            model.onTrimMemory(level);
        }
    }

    public static void useFadeOutAnimationForLauncherStart(Launcher launcher,
            CancellationSignal cancellationSignal) {
        QuickstepAppTransitionManagerImpl appTransitionManager =
                (QuickstepAppTransitionManagerImpl) launcher.getAppTransitionManager();
        appTransitionManager.setRemoteAnimationProvider((targets) -> {

            // On the first call clear the reference.
            cancellationSignal.cancel();

            ValueAnimator fadeAnimation = ValueAnimator.ofFloat(1, 0);
            fadeAnimation.addUpdateListener(new RemoteFadeOutAnimationListener(targets));
            AnimatorSet anim = new AnimatorSet();
            anim.play(fadeAnimation);
            return anim;
        }, cancellationSignal);
    }

    public static boolean dumpActivity(Activity activity, PrintWriter writer) {
        if (!Utilities.IS_DEBUG_DEVICE) {
            return false;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!(new ActivityCompat(activity).encodeViewHierarchy(out))) {
            return false;
        }

        Deflater deflater = new Deflater();
        deflater.setInput(out.toByteArray());
        deflater.finish();

        out.reset();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer); // returns the generated code... index
            out.write(buffer, 0, count);
        }

        writer.println("--encoded-view-dump-v0--");
        writer.println(Base64.encodeToString(
                out.toByteArray(), Base64.NO_WRAP | Base64.NO_PADDING));
        return true;
    }

    public static void prepareToShowOverview(Launcher launcher) {
        RecentsUiFactory.prepareToShowRecents(launcher);
    }
}
