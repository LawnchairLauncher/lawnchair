/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.LauncherState.FAST_OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.systemui.shared.system.NavigationBarCompat.HIT_TARGET_BACK;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.view.View;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherInitListener;
import com.android.launcher3.LauncherState;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.views.LauncherLayoutListener;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.BiPredicate;

/**
 * Utility class which abstracts out the logical differences between Launcher and RecentsActivity.
 */
public interface ActivityControlHelper<T extends BaseDraggingActivity> {

    LayoutListener createLayoutListener(T activity);

    void onQuickstepGestureStarted(T activity, boolean activityVisible);

    /**
     * Updates the UI to indicate quick interaction.
     * @return true if there any any UI change as a result of this
     */
    boolean onQuickInteractionStart(T activity, boolean activityVisible);

    void executeOnWindowAvailable(T activity, Runnable action);

    void executeOnNextDraw(T activity, TaskView targetView, Runnable action);

    void onTransitionCancelled(T activity, boolean activityVisible);

    int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect);

    void onSwipeUpComplete(T activity);

    void prepareRecentsUI(T activity, boolean activityVisible);

    AnimatorPlaybackController createControllerForVisibleActivity(T activity);

    AnimatorPlaybackController createControllerForHiddenActivity(T activity, int transitionLength);

    ActivityInitListener createActivityInitListener(BiPredicate<T, Boolean> onInitListener);

    @Nullable
    T getCreatedActivity();

    @UiThread
    @Nullable
    RecentsView getVisibleRecentsView();

    @UiThread
    boolean switchToRecentsIfVisible();

    Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target);

    boolean shouldMinimizeSplitScreen();

    /**
     * @return {@code true} if recents activity should be started immediately on touchDown,
     *         {@code false} if it should deferred until some threshold is crossed.
     */
    boolean deferStartingActivity(int downHitTarget);

    class LauncherActivityControllerHelper implements ActivityControlHelper<Launcher> {

        @Override
        public LayoutListener createLayoutListener(Launcher activity) {
            return new LauncherLayoutListener(activity);
        }

        @Override
        public void onQuickstepGestureStarted(Launcher activity, boolean activityVisible) {
            activity.onQuickstepGestureStarted(activityVisible);
        }

        @Override
        public boolean onQuickInteractionStart(Launcher activity, boolean activityVisible) {
            LauncherState fromState = activity.getStateManager().getState();
            activity.getStateManager().goToState(FAST_OVERVIEW, activityVisible);
            return !fromState.overviewUi;
        }

        @Override
        public void executeOnWindowAvailable(Launcher activity, Runnable action) {
            if (activity.getWorkspace().runOnOverlayHidden(action)) {
                // Notify the activity that qiuckscrub has started
                onQuickstepGestureStarted(activity, true);
            }
        }

        @Override
        public void executeOnNextDraw(Launcher activity, TaskView targetView, Runnable action) {
            ViewOnDrawExecutor executor = new ViewOnDrawExecutor() {
                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (!isCompleted()) {
                        runAllTasks();
                    }
                }
            };
            executor.attachTo(activity, targetView, false /* waitForLoadAnimation */);
            executor.execute(action);
        }

        @Override
        public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect) {
            LayoutUtils.calculateLauncherTaskSize(context, dp, outRect);
            if (dp.isVerticalBarLayout()) {
                Rect targetInsets = dp.getInsets();
                int hotseatInset = dp.isSeascape() ? targetInsets.left : targetInsets.right;
                return dp.hotseatBarSizePx + dp.hotseatBarSidePaddingPx + hotseatInset;
            } else {
                return dp.heightPx - outRect.bottom;
            }
        }

        @Override
        public void onTransitionCancelled(Launcher activity, boolean activityVisible) {
            LauncherState startState = activity.getStateManager().getRestState();
            activity.getStateManager().goToState(startState, activityVisible);
        }

        @Override
        public void onSwipeUpComplete(Launcher activity) {
            // Re apply state in case we did something funky during the transition.
            activity.getStateManager().reapplyState();
        }

        @Override
        public void prepareRecentsUI(Launcher activity, boolean activityVisible) {
            LauncherState startState = activity.getStateManager().getState();
            if (startState.disableRestore) {
                startState = activity.getStateManager().getRestState();
            }
            activity.getStateManager().setRestState(startState);

            if (!activityVisible) {
                // Since the launcher is not visible, we can safely reset the scroll position.
                // This ensures then the next swipe up to all-apps starts from scroll 0.
                activity.getAppsView().reset(false /* animate */);
                activity.getStateManager().goToState(OVERVIEW, false);

                // Optimization, hide the all apps view to prevent layout while initializing
                activity.getAppsView().getContentView().setVisibility(View.GONE);
            }
        }

        @Override
        public AnimatorPlaybackController createControllerForVisibleActivity(Launcher activity) {
            DeviceProfile dp = activity.getDeviceProfile();
            long accuracy = 2 * Math.max(dp.widthPx, dp.heightPx);
            return activity.getStateManager().createAnimationToNewWorkspace(OVERVIEW, accuracy);
        }

        @Override
        public AnimatorPlaybackController createControllerForHiddenActivity(
                Launcher activity, int transitionLength) {
            AllAppsTransitionController controller = activity.getAllAppsController();
            AnimatorSet anim = new AnimatorSet();
            if (activity.getDeviceProfile().isVerticalBarLayout()) {
                // TODO:
            } else {
                float scrollRange = Math.max(controller.getShiftRange(), 1);
                float progressDelta = (transitionLength / scrollRange);

                float endProgress = OVERVIEW.getVerticalProgress(activity);
                float startProgress = endProgress + progressDelta;
                ObjectAnimator shiftAnim = ObjectAnimator.ofFloat(
                        controller, ALL_APPS_PROGRESS, startProgress, endProgress);
                shiftAnim.setInterpolator(LINEAR);
                anim.play(shiftAnim);
            }

            anim.setDuration(transitionLength * 2);
            activity.getStateManager().setCurrentAnimation(anim);
            return AnimatorPlaybackController.wrap(anim, transitionLength * 2);
        }

        @Override
        public ActivityInitListener createActivityInitListener(
                BiPredicate<Launcher, Boolean> onInitListener) {
            return new LauncherInitListener(onInitListener);
        }

        @Nullable
        @Override
        public Launcher getCreatedActivity() {
            LauncherAppState app = LauncherAppState.getInstanceNoCreate();
            if (app == null) {
                return null;
            }
            return (Launcher) app.getModel().getCallback();
        }

        @Nullable
        @UiThread
        private Launcher getVisibleLaucher() {
            Launcher launcher = getCreatedActivity();
            return (launcher != null) && launcher.isStarted() && launcher.hasWindowFocus() ?
                    launcher : null;
        }

        @Nullable
        @Override
        public RecentsView getVisibleRecentsView() {
            Launcher launcher = getVisibleLaucher();
            return launcher != null && launcher.isInState(OVERVIEW)
                    ? launcher.getOverviewPanel() : null;
        }

        @Override
        public boolean switchToRecentsIfVisible() {
            Launcher launcher = getVisibleLaucher();
            if (launcher != null) {
                launcher.getStateManager().goToState(OVERVIEW);
                return true;
            }
            return false;
        }

        @Override
        public boolean deferStartingActivity(int downHitTarget) {
            return downHitTarget == HIT_TARGET_BACK;
        }

        @Override
        public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target) {
            return homeBounds;
        }

        @Override
        public boolean shouldMinimizeSplitScreen() {
            return true;
        }
    }

    class FallbackActivityControllerHelper implements ActivityControlHelper<RecentsActivity> {

        @Override
        public void onQuickstepGestureStarted(RecentsActivity activity, boolean activityVisible) {
            // TODO:
        }

        @Override
        public boolean onQuickInteractionStart(RecentsActivity activity, boolean activityVisible) {
            // Activity does not need any UI change for quickscrub.
            return false;
        }

        @Override
        public void executeOnWindowAvailable(RecentsActivity activity, Runnable action) {
            action.run();
        }

        @Override
        public void executeOnNextDraw(RecentsActivity activity, TaskView targetView,
                Runnable action) {
            // TODO:
            new Handler(Looper.getMainLooper()).post(action);
        }

        @Override
        public void onTransitionCancelled(RecentsActivity activity, boolean activityVisible) {
            // TODO:
        }

        @Override
        public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect) {
            LayoutUtils.calculateFallbackTaskSize(context, dp, outRect);
            if (dp.isVerticalBarLayout()) {
                Rect targetInsets = dp.getInsets();
                int hotseatInset = dp.isSeascape() ? targetInsets.left : targetInsets.right;
                return dp.hotseatBarSizePx + dp.hotseatBarSidePaddingPx + hotseatInset;
            } else {
                return dp.heightPx - outRect.bottom;
            }
        }

        @Override
        public void onSwipeUpComplete(RecentsActivity activity) {
            // TODO:
        }

        @Override
        public void prepareRecentsUI(RecentsActivity activity, boolean activityVisible) {
            // TODO:
        }

        @Override
        public AnimatorPlaybackController createControllerForVisibleActivity(
                RecentsActivity activity) {
            DeviceProfile dp = activity.getDeviceProfile();
            return createControllerForHiddenActivity(activity, Math.max(dp.widthPx, dp.heightPx));
        }

        @Override
        public AnimatorPlaybackController createControllerForHiddenActivity(
                RecentsActivity activity, int transitionLength) {
            // We do not animate anything. Create a empty controller
            AnimatorSet anim = new AnimatorSet();
            return AnimatorPlaybackController.wrap(anim, transitionLength * 2);
        }

        @Override
        public LayoutListener createLayoutListener(RecentsActivity activity) {
            // We do not change anything as part of layout changes in fallback activity. Return a
            // default layout listener.
            return new LayoutListener() {
                @Override
                public void open() { }

                @Override
                public void setHandler(WindowTransformSwipeHandler handler) { }

                @Override
                public void finish() { }
            };
        }

        @Override
        public ActivityInitListener createActivityInitListener(
                BiPredicate<RecentsActivity, Boolean> onInitListener) {
            return new RecentsActivityTracker(onInitListener);
        }

        @Nullable
        @Override
        public RecentsActivity getCreatedActivity() {
            return RecentsActivityTracker.getCurrentActivity();
        }

        @Nullable
        @Override
        public RecentsView getVisibleRecentsView() {
            RecentsActivity activity = getCreatedActivity();
            if (activity != null && activity.hasWindowFocus()) {
                return activity.getOverviewPanel();
            }
            return null;
        }

        @Override
        public boolean switchToRecentsIfVisible() {
            return false;
        }

        @Override
        public boolean deferStartingActivity(int downHitTarget) {
            // Always defer starting the activity when using fallback
            return true;
        }

        @Override
        public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target) {
            // TODO: Remove this once b/77875376 is fixed
            return target.sourceContainerBounds;
        }

        @Override
        public boolean shouldMinimizeSplitScreen() {
            // TODO: Remove this once b/77875376 is fixed
            return false;
        }
    }

    interface LayoutListener {

        void open();

        void setHandler(WindowTransformSwipeHandler handler);

        void finish();
    }

    interface ActivityInitListener {

        void register();

        void unregister();

        void registerAndStartActivity(Intent intent, RemoteAnimationProvider animProvider,
                Context context, Handler handler, long duration);
    }
}
