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

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.FLOATING_SEARCH_BAR;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherInitListener;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.taskbar.LauncherTaskbarUIController;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.quickstep.GestureState.GestureEndTarget;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.plugins.shared.LauncherOverlayManager;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link BaseActivityInterface} for the in-launcher recents.
 */
public final class LauncherActivityInterface extends
        BaseActivityInterface<LauncherState, QuickstepLauncher> {

    public static final LauncherActivityInterface INSTANCE = new LauncherActivityInterface();

    private LauncherActivityInterface() {
        super(true, OVERVIEW, BACKGROUND_APP);
    }

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect,
            RecentsPagedOrientationHandler orientationHandler) {
        calculateTaskSize(context, dp, outRect, orientationHandler);
        if (dp.isVerticalBarLayout()
                && DisplayController.getNavigationMode(context) != NavigationMode.NO_BUTTON) {
            return dp.isSeascape() ? outRect.left : (dp.widthPx - outRect.right);
        } else {
            return LayoutUtils.getShelfTrackingDistance(context, dp, orientationHandler);
        }
    }

    @Override
    public void onSwipeUpToHomeComplete(RecentsAnimationDeviceState deviceState) {
        QuickstepLauncher launcher = getCreatedContainer();
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
        QuickstepLauncher launcher = getCreatedContainer();
        if (launcher == null) {
            return;
        }
        launcher.onAssistantVisibilityChanged(visibility);
    }

    @Override
    public AnimationFactory prepareRecentsUI(RecentsAnimationDeviceState deviceState,
            boolean activityVisible, Consumer<AnimatorControllerWithResistance> callback) {
        notifyRecentsOfOrientation(deviceState.getRotationTouchHelper());
        DefaultAnimationFactory factory = new DefaultAnimationFactory(callback) {
            @Override
            protected void createBackgroundToOverviewAnim(QuickstepLauncher activity,
                    PendingAnimation pa) {
                super.createBackgroundToOverviewAnim(activity, pa);

                // Animate the blur and wallpaper zoom
                float fromDepthRatio = BACKGROUND_APP.getDepth(activity);
                float toDepthRatio = OVERVIEW.getDepth(activity);
                pa.addFloat(getDepthController().stateDepth,
                        new LauncherAnimUtils.ClampedProperty<>(
                                MULTI_PROPERTY_VALUE, fromDepthRatio, toDepthRatio),
                        fromDepthRatio, toDepthRatio, LINEAR);
            }
        };

        QuickstepLauncher launcher = factory.initBackgroundStateUI();
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
        QuickstepLauncher launcher = getCreatedContainer();
        if (launcher == null) {
            return;
        }
        launcher.setOnDeferredActivityLaunchCallback(r);
    }

    @Nullable
    @Override
    public QuickstepLauncher getCreatedContainer() {
        return QuickstepLauncher.ACTIVITY_TRACKER.getCreatedActivity();
    }

    @Nullable
    @Override
    public DepthController getDepthController() {
        QuickstepLauncher launcher = getCreatedContainer();
        if (launcher == null) {
            return null;
        }
        return launcher.getDepthController();
    }

    @Nullable
    @Override
    public DesktopVisibilityController getDesktopVisibilityController() {
        QuickstepLauncher launcher = getCreatedContainer();
        if (launcher == null) {
            return null;
        }
        return launcher.getDesktopVisibilityController();
    }

    @Nullable
    @Override
    public LauncherTaskbarUIController getTaskbarController() {
        QuickstepLauncher launcher = getCreatedContainer();
        if (launcher == null) {
            return null;
        }
        return launcher.getTaskbarUIController();
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        QuickstepLauncher launcher = getVisibleLauncher();
        RecentsView recentsView =
                launcher != null && launcher.getStateManager().getState().overviewUi
                        ? launcher.getOverviewPanel() : null;
        if (recentsView == null || (!launcher.hasBeenResumed()
                && recentsView.getRunningTaskViewId() == -1)) {
            // If live tile has ended, return null.
            return null;
        }
        return recentsView;
    }

    @Nullable
    @UiThread
    private QuickstepLauncher getVisibleLauncher() {
        QuickstepLauncher launcher = getCreatedContainer();
        if (launcher == null) {
            return null;
        }
        if (launcher.isStarted() && (isInLiveTileMode() || launcher.hasBeenResumed())) {
            return launcher;
        }
        if (Flags.useActivityOverlay()
                && SystemUiProxy.INSTANCE.get(launcher).getHomeVisibilityState().isHomeVisible()) {
            return launcher;
        }
        return null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Animator.AnimatorListener animatorListener) {
        QuickstepLauncher launcher = getVisibleLauncher();
        if (launcher == null) {
            return false;
        }
        if (isInLiveTileMode()) {
            RecentsView recentsView = getVisibleRecentsView();
            if (recentsView == null) {
                return false;
            }
        }

        closeOverlay();
        launcher.getStateManager().goToState(OVERVIEW,
                launcher.getStateManager().shouldAnimateStateChange(),
                animatorListener);
        return true;
    }


    @Override
    public void onExitOverview(RotationTouchHelper deviceState, Runnable exitRunnable) {
        final StateManager<LauncherState> stateManager = getCreatedContainer().getStateManager();
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
        RecentsView recentsView = getCreatedContainer().getOverviewPanel();
        recentsView.setLayoutRotation(rotationTouchHelper.getCurrentActiveRotation(),
                rotationTouchHelper.getDisplayRotation());
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTarget target) {
        return homeBounds;
    }

    @Override
    public boolean allowMinimizeSplitScreen() {
        return true;
    }

    @Override
    public boolean allowAllAppsFromOverview() {
        return FeatureFlags.ENABLE_ALL_APPS_FROM_OVERVIEW.get()
                // If floating search bar would not show in overview, don't allow all apps gesture.
                && OVERVIEW.areElementsVisible(getCreatedContainer(), FLOATING_SEARCH_BAR);
    }

    @Override
    public boolean isInLiveTileMode() {
        QuickstepLauncher launcher = getCreatedContainer();

        return launcher != null
                && launcher.getStateManager().getState() == OVERVIEW
                && launcher.isStarted()
                && TopTaskTracker.INSTANCE.get(launcher).getCachedTopTask(false).isHomeTask();
    }

    @Override
    public void onLaunchTaskFailed() {
        QuickstepLauncher launcher = getCreatedContainer();
        if (launcher == null) {
            return;
        }
        launcher.getStateManager().goToState(OVERVIEW);
    }

    @Override
    public void closeOverlay() {
        super.closeOverlay();
        QuickstepLauncher launcher = getCreatedContainer();
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
    public @Nullable Animator getParallelAnimationToLauncher(GestureEndTarget endTarget,
            long duration, RecentsAnimationCallbacks callbacks) {
        LauncherTaskbarUIController uiController = getTaskbarController();
        Animator superAnimator = super.getParallelAnimationToLauncher(
                endTarget, duration, callbacks);
        if (uiController == null || callbacks == null) {
            return superAnimator;
        }
        LauncherState toState = stateFromGestureEndTarget(endTarget);
        Animator taskbarAnimator = uiController.createAnimToLauncher(toState, callbacks, duration);
        if (superAnimator == null) {
            return taskbarAnimator;
        } else {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(superAnimator, taskbarAnimator);
            return animatorSet;
        }
    }

    @Override
    protected int getOverviewScrimColorForState(QuickstepLauncher activity, LauncherState state) {
        return state.getWorkspaceScrimColor(activity);
    }

    @Override
    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        LauncherTaskbarUIController uiController = getTaskbarController();
        if (uiController == null) {
            return super.deferStartingActivity(deviceState, ev);
        }
        return uiController.isEventOverAnyTaskbarItem(ev)
                || super.deferStartingActivity(deviceState, ev);
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
                return BACKGROUND_APP;
            case ALL_APPS:
                return ALL_APPS;
            case HOME:
            default:
                return NORMAL;
        }
    }
}
