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
package com.android.quickstep;

import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.QUICK_SWITCH;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherInitListener;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statehandlers.DepthController.ClampedDepthProperty;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.taskbar.LauncherTaskbarUIController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.quickstep.GestureState.GestureEndTarget;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link BaseActivityInterface} for the in-launcher recents.
 */
public final class LauncherActivityInterface extends
        BaseActivityInterface<LauncherState, BaseQuickstepLauncher> {

    public static final LauncherActivityInterface INSTANCE = new LauncherActivityInterface();

    private LauncherActivityInterface() {
        super(true, OVERVIEW, BACKGROUND_APP);
    }

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect,
            PagedOrientationHandler orientationHandler) {
        calculateTaskSize(context, dp, outRect, orientationHandler);
        if (dp.isVerticalBarLayout() && SysUINavigationMode.getMode(context) != Mode.NO_BUTTON) {
            return dp.isSeascape() ? outRect.left : (dp.widthPx - outRect.right);
        } else {
            return LayoutUtils.getShelfTrackingDistance(context, dp, orientationHandler);
        }
    }

    @Override
    public void onSwipeUpToHomeComplete(RecentsAnimationDeviceState deviceState) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        // When going to home, the state animator we use has SKIP_OVERVIEW because we assume that
        // setRecentsAttachedToAppWindow() will handle animating Overview instead. Thus, at the end
        // of the animation, we should ensure recents is at the correct position for NORMAL state.
        // For example, when doing a long swipe to home, RecentsView may be scaled down. This is
        // relatively expensive, so do it on the next frame instead of critical path.
        MAIN_EXECUTOR.getHandler().post(launcher.getStateManager()::reapplyState);

        launcher.getRootView().setForceHideBackArrow(false);
        notifyRecentsOfOrientation(deviceState.getRotationTouchHelper());
    }

    @Override
    public void onAssistantVisibilityChanged(float visibility) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.onAssistantVisibilityChanged(visibility);
    }

    @Override
    public void onOneHandedModeStateChanged(boolean activated) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.onOneHandedStateChanged(activated);
    }

    @Override
    public AnimationFactory prepareRecentsUI(RecentsAnimationDeviceState deviceState,
            boolean activityVisible, Consumer<AnimatorControllerWithResistance> callback) {
        notifyRecentsOfOrientation(deviceState.getRotationTouchHelper());
        DefaultAnimationFactory factory = new DefaultAnimationFactory(callback) {
            @Override
            protected void createBackgroundToOverviewAnim(BaseQuickstepLauncher activity,
                    PendingAnimation pa) {
                super.createBackgroundToOverviewAnim(activity, pa);

                // Animate the blur and wallpaper zoom
                float fromDepthRatio = BACKGROUND_APP.getDepth(activity);
                float toDepthRatio = OVERVIEW.getDepth(activity);
                pa.addFloat(getDepthController(),
                        new ClampedDepthProperty(fromDepthRatio, toDepthRatio),
                        fromDepthRatio, toDepthRatio, LINEAR);

            }
        };

        BaseQuickstepLauncher launcher = factory.initUI();
        // Since all apps is not visible, we can safely reset the scroll position.
        // This ensures then the next swipe up to all-apps starts from scroll 0.
        launcher.getAppsView().reset(false /* animate */);
        return factory;
    }

    @Override
    public ActivityInitListener createActivityInitListener(Predicate<Boolean> onInitListener) {
        return new LauncherInitListener((activity, alreadyOnHome) ->
                onInitListener.test(alreadyOnHome));
    }

    @Override
    public void setOnDeferredActivityLaunchCallback(Runnable r) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.setOnDeferredActivityLaunchCallback(r);
    }

    @Nullable
    @Override
    public BaseQuickstepLauncher getCreatedActivity() {
        return BaseQuickstepLauncher.ACTIVITY_TRACKER.getCreatedActivity();
    }

    @Nullable
    @Override
    public DepthController getDepthController() {
        BaseQuickstepLauncher launcher = getCreatedActivity();
        if (launcher == null) {
            return null;
        }
        return launcher.getDepthController();
    }

    @Nullable
    private LauncherTaskbarUIController getTaskbarController() {
        BaseQuickstepLauncher launcher = getCreatedActivity();
        if (launcher == null) {
            return null;
        }
        return launcher.getTaskbarUIController();
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        Launcher launcher = getVisibleLauncher();
        RecentsView recentsView =
                launcher != null && launcher.getStateManager().getState().overviewUi
                        ? launcher.getOverviewPanel() : null;
        if (recentsView == null || (!launcher.hasBeenResumed()
                && recentsView.getRunningTaskId() == -1)) {
            // If live tile has ended, return null.
            return null;
        }
        return recentsView;
    }

    @Nullable
    @UiThread
    private Launcher getVisibleLauncher() {
        Launcher launcher = getCreatedActivity();
        return (launcher != null) && launcher.isStarted()
                && ((ENABLE_QUICKSTEP_LIVE_TILE.get() && isInLiveTileMode())
                || launcher.hasBeenResumed()) ? launcher : null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Runnable onCompleteCallback) {
        Launcher launcher = getVisibleLauncher();
        if (launcher == null) {
            return false;
        }
        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && isInLiveTileMode()) {
            RecentsView recentsView = getVisibleRecentsView();
            if (recentsView == null) {
                return false;
            }
        }

        closeOverlay();
        launcher.getStateManager().goToState(OVERVIEW,
                launcher.getStateManager().shouldAnimateStateChange(),
                onCompleteCallback == null ? null : forEndCallback(onCompleteCallback));
        return true;
    }


    @Override
    public void onExitOverview(RotationTouchHelper deviceState, Runnable exitRunnable) {
        final StateManager<LauncherState> stateManager = getCreatedActivity().getStateManager();
        stateManager.addStateListener(
                new StateManager.StateListener<LauncherState>() {
                    @Override
                    public void onStateTransitionComplete(LauncherState toState) {
                        // Are we going from Recents to Workspace?
                        if (toState == LauncherState.NORMAL) {
                            exitRunnable.run();
                            notifyRecentsOfOrientation(deviceState);
                            stateManager.removeStateListener(this);
                        }
                    }
                });
    }

    private void notifyRecentsOfOrientation(RotationTouchHelper rotationTouchHelper) {
        // reset layout on swipe to home
        RecentsView recentsView = getCreatedActivity().getOverviewPanel();
        recentsView.setLayoutRotation(rotationTouchHelper.getCurrentActiveRotation(),
                rotationTouchHelper.getDisplayRotation());
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target) {
        return homeBounds;
    }

    @Override
    public boolean allowMinimizeSplitScreen() {
        return true;
    }

    @Override
    public boolean isInLiveTileMode() {
        Launcher launcher = getCreatedActivity();
        return launcher != null && launcher.getStateManager().getState() == OVERVIEW &&
                launcher.isStarted();
    }

    @Override
    public void onLaunchTaskFailed() {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.getStateManager().goToState(OVERVIEW);
    }

    @Override
    public void closeOverlay() {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        LauncherOverlayManager om = launcher.getOverlayManager();
        if (!launcher.isStarted() || launcher.isForceInvisible()) {
            om.hideOverlay(false /* animate */);
        } else {
            om.hideOverlay(150);
        }
    }

    @Override
    void onOverviewServiceBound() {
        final BaseQuickstepLauncher activity = getCreatedActivity();
        if (activity == null) return;
        activity.getAppTransitionManager().registerRemoteTransitions();
    }

    @Override
    public @Nullable Animator getParallelAnimationToLauncher(GestureEndTarget endTarget,
            long duration) {
        LauncherTaskbarUIController uiController = getTaskbarController();
        Animator superAnimator = super.getParallelAnimationToLauncher(endTarget, duration);
        if (uiController == null) {
            return superAnimator;
        }
        LauncherState toState = stateFromGestureEndTarget(endTarget);
        Animator taskbarAnimator = uiController.createAnimToLauncher(toState, duration);
        if (superAnimator == null) {
            return taskbarAnimator;
        } else {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(superAnimator, taskbarAnimator);
            return animatorSet;
        }
    }

    @Override
    protected int getOverviewScrimColorForState(BaseQuickstepLauncher launcher,
            LauncherState state) {
        return state.getWorkspaceScrimColor(launcher);
    }

    @Override
    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        LauncherTaskbarUIController uiController = getTaskbarController();
        if (uiController == null) {
            return super.deferStartingActivity(deviceState, ev);
        }
        return uiController.isEventOverAnyTaskbarItem(ev);
    }

    @Override
    public boolean shouldCancelCurrentGesture() {
        LauncherTaskbarUIController uiController = getTaskbarController();
        if (uiController == null) {
            return super.shouldCancelCurrentGesture();
        }
        return uiController.isDraggingItem();
    }

    @Override
    public LauncherState stateFromGestureEndTarget(GestureEndTarget endTarget) {
        switch (endTarget) {
            case RECENTS:
                return OVERVIEW;
            case NEW_TASK:
            case LAST_TASK:
                return QUICK_SWITCH;
            case HOME:
            default:
                return NORMAL;
        }
    }
}
