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

import static com.android.launcher3.util.NavigationMode.NO_BUTTON;
import static com.android.quickstep.fallback.RecentsState.BACKGROUND_APP;
import static com.android.quickstep.fallback.RecentsState.DEFAULT;
import static com.android.quickstep.fallback.RecentsState.HOME;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.taskbar.FallbackTaskbarUIController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.GestureState.GestureEndTarget;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.views.RecentsView;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link BaseActivityInterface} for recents when the default launcher is different than the
 * currently running one and apps should interact with the {@link RecentsActivity} as opposed
 * to the in-launcher one.
 */
public final class FallbackActivityInterface extends
        BaseActivityInterface<RecentsState, RecentsActivity> {

    public static final FallbackActivityInterface INSTANCE = new FallbackActivityInterface();

    private FallbackActivityInterface() {
        super(false, DEFAULT, BACKGROUND_APP);
    }

    /** 2 */
    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect,
            PagedOrientationHandler orientationHandler) {
        calculateTaskSize(context, dp, outRect);
        if (dp.isVerticalBarLayout() && DisplayController.getNavigationMode(context) != NO_BUTTON) {
            return dp.isSeascape() ? outRect.left : (dp.widthPx - outRect.right);
        } else {
            return dp.heightPx - outRect.bottom;
        }
    }

    /** 5 */
    @Override
    public void onAssistantVisibilityChanged(float visibility) {
        // This class becomes active when the screen is locked.
        // Rather than having it handle assistant visibility changes, the assistant visibility is
        // set to zero prior to this class becoming active.
    }

    /** 6 */
    @Override
    public AnimationFactory prepareRecentsUI(RecentsAnimationDeviceState deviceState,
            boolean activityVisible, Consumer<AnimatorControllerWithResistance> callback) {
        notifyRecentsOfOrientation(deviceState.getRotationTouchHelper());
        DefaultAnimationFactory factory = new DefaultAnimationFactory(callback);
        factory.initBackgroundStateUI();
        return factory;
    }

    @Override
    public ActivityInitListener createActivityInitListener(
            Predicate<Boolean> onInitListener) {
        return new ActivityInitListener<>((activity, alreadyOnHome) ->
                onInitListener.test(alreadyOnHome), RecentsActivity.ACTIVITY_TRACKER);
    }

    @Nullable
    @Override
    public RecentsActivity getCreatedActivity() {
        return RecentsActivity.ACTIVITY_TRACKER.getCreatedActivity();
    }

    @Override
    public FallbackTaskbarUIController getTaskbarController() {
        RecentsActivity activity = getCreatedActivity();
        if (activity == null) {
            return null;
        }
        return activity.getTaskbarUIController();
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        RecentsActivity activity = getCreatedActivity();
        if (activity != null) {
            if (activity.hasBeenResumed() || isInLiveTileMode()) {
                return activity.getOverviewPanel();
            }
        }
        return null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Runnable onCompleteCallback) {
        return false;
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTarget target) {
        // TODO: Remove this once b/77875376 is fixed
        return target.screenSpaceBounds;
    }

    @Override
    public boolean allowMinimizeSplitScreen() {
        // TODO: Remove this once b/77875376 is fixed
        return false;
    }

    @Override
    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        // In non-gesture mode, user might be clicking on the home button which would directly
        // start the home activity instead of going through recents. In that case, defer starting
        // recents until we are sure it is a gesture.
        return !deviceState.isFullyGesturalNavMode()
                || super.deferStartingActivity(deviceState, ev);
    }

    @Override
    public void onExitOverview(RotationTouchHelper deviceState, Runnable exitRunnable) {
        final StateManager<RecentsState> stateManager = getCreatedActivity().getStateManager();
        if (stateManager.getState() == HOME) {
            exitRunnable.run();
            notifyRecentsOfOrientation(deviceState);
            return;
        }

        stateManager.addStateListener(
                new StateManager.StateListener<RecentsState>() {
                    @Override
                    public void onStateTransitionComplete(RecentsState toState) {
                        // Are we going from Recents to Workspace?
                        if (toState == HOME) {
                            exitRunnable.run();
                            notifyRecentsOfOrientation(deviceState);
                            stateManager.removeStateListener(this);
                        }
                    }
                });
    }

    @Override
    public boolean isInLiveTileMode() {
        RecentsActivity activity = getCreatedActivity();
        return activity != null && activity.getStateManager().getState() == DEFAULT &&
                activity.isStarted();
    }

    @Override
    public void onLaunchTaskFailed() {
        // TODO: probably go back to overview instead.
        RecentsActivity activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        activity.<RecentsView>getOverviewPanel().startHome();
    }

    @Override
    public RecentsState stateFromGestureEndTarget(GestureEndTarget endTarget) {
        switch (endTarget) {
            case RECENTS:
                return DEFAULT;
            case NEW_TASK:
            case LAST_TASK:
                return BACKGROUND_APP;
            case HOME:
            default:
                return HOME;
        }
    }

    private void notifyRecentsOfOrientation(RotationTouchHelper rotationTouchHelper) {
        // reset layout on swipe to home
        RecentsView recentsView = getCreatedActivity().getOverviewPanel();
        recentsView.setLayoutRotation(rotationTouchHelper.getCurrentActiveRotation(),
                rotationTouchHelper.getDisplayRotation());
    }

    @Override
    public @Nullable Animator getParallelAnimationToLauncher(GestureEndTarget endTarget,
            long duration, RecentsAnimationCallbacks callbacks) {
        FallbackTaskbarUIController uiController = getTaskbarController();
        Animator superAnimator = super.getParallelAnimationToLauncher(
                endTarget, duration, callbacks);
        if (uiController == null) {
            return superAnimator;
        }
        RecentsState toState = stateFromGestureEndTarget(endTarget);
        Animator taskbarAnimator = uiController.createAnimToRecentsState(toState, duration);
        if (taskbarAnimator == null) {
            return superAnimator;
        }
        if (superAnimator == null) {
            return taskbarAnimator;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(superAnimator, taskbarAnimator);
        return animatorSet;
    }

    @Override
    protected int getOverviewScrimColorForState(RecentsActivity activity, RecentsState state) {
        return state.getScrimColor(activity);
    }
}
