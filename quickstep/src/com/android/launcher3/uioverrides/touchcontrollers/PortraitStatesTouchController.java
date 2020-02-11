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
package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.launcher3.AbstractFloatingView.TYPE_ACCESSIBLE;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.QUICKSTEP_SPRINGS;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationComponents;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.uioverrides.states.OverviewState;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.OverviewInteractionState;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.util.LayoutUtils;

/**
 * Touch controller for handling various state transitions in portrait UI.
 */
public class PortraitStatesTouchController extends AbstractStateChangeTouchController {

    private static final String TAG = "PortraitStatesTouchCtrl";

    /**
     * The progress at which all apps content will be fully visible when swiping up from overview.
     */
    protected static final float ALL_APPS_CONTENT_FADE_THRESHOLD = 0.08f;

    /**
     * The progress at which recents will begin fading out when swiping up from overview.
     */
    private static final float RECENTS_FADE_THRESHOLD = 0.88f;

    private final PortraitOverviewStateTouchHelper mOverviewPortraitStateTouchHelper;

    private final InterpolatorWrapper mAllAppsInterpolatorWrapper = new InterpolatorWrapper();

    private final boolean mAllowDragToOverview;

    // If true, we will finish the current animation instantly on second touch.
    private boolean mFinishFastOnSecondTouch;

    public PortraitStatesTouchController(Launcher l, boolean allowDragToOverview) {
        super(l, SwipeDetector.VERTICAL);
        mOverviewPortraitStateTouchHelper = new PortraitOverviewStateTouchHelper(l);
        mAllowDragToOverview = allowDragToOverview;
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            if (mFinishFastOnSecondTouch) {
                // TODO: Animate to finish instead.
                mCurrentAnimation.skipToEnd();
            }

            AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
            if (ev.getY() >= allAppsController.getShiftRange() * allAppsController.getProgress()) {
                // If we are already animating from a previous state, we can intercept as long as
                // the touch is below the current all apps progress (to allow for double swipe).
                return true;
            }
            // Otherwise, make sure everything is settled and don't intercept so they can scroll
            // recents, dismiss a task, etc.
            if (mAtomicAnim != null) {
                mAtomicAnim.end();
            }
            return false;
        }
        if (mLauncher.isInState(ALL_APPS)) {
            // In all-apps only listen if the container cannot scroll itself
            if (!mLauncher.getAppsView().shouldContainerScroll(ev)) {
                return false;
            }
        } else if (mLauncher.isInState(OVERVIEW)) {
            if (!mOverviewPortraitStateTouchHelper.canInterceptTouch(ev)) {
                return false;
            }
        } else {
            // If we are swiping to all apps instead of overview, allow it from anywhere.
            boolean interceptAnywhere = mLauncher.isInState(NORMAL) && !mAllowDragToOverview;
            // For all other states, only listen if the event originated below the hotseat height
            if (!interceptAnywhere && !isTouchOverHotseat(mLauncher, ev)) {
                return false;
            }
        }
        if (AbstractFloatingView.getTopOpenViewWithType(mLauncher, TYPE_ACCESSIBLE) != null) {
            return false;
        }
        return true;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (fromState == ALL_APPS && !isDragTowardPositive) {
            // Should swipe down go to OVERVIEW instead?
            return TouchInteractionService.isConnected() ?
                    mLauncher.getStateManager().getLastState() : NORMAL;
        } else if (fromState == OVERVIEW) {
            return isDragTowardPositive ? ALL_APPS : NORMAL;
        } else if (fromState == NORMAL && isDragTowardPositive) {
            int stateFlags = OverviewInteractionState.INSTANCE.get(mLauncher)
                    .getSystemUiStateFlags();
            return mAllowDragToOverview && TouchInteractionService.isConnected()
                    && (stateFlags & SYSUI_STATE_OVERVIEW_DISABLED) == 0
                    ? OVERVIEW : ALL_APPS;
        }
        return fromState;
    }

    @Override
    protected int getLogContainerTypeForNormalState(MotionEvent ev) {
        return isTouchOverHotseat(mLauncher, ev) ? ContainerType.HOTSEAT : ContainerType.WORKSPACE;
    }

    private AnimatorSetBuilder getNormalToOverviewAnimation() {
        mAllAppsInterpolatorWrapper.baseInterpolator = LINEAR;

        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        builder.setInterpolator(ANIM_VERTICAL_PROGRESS, mAllAppsInterpolatorWrapper);
        return builder;
    }

    public static AnimatorSetBuilder getOverviewToAllAppsAnimation() {
        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        builder.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.clampToProgress(ACCEL,
                0, ALL_APPS_CONTENT_FADE_THRESHOLD));
        builder.setInterpolator(ANIM_OVERVIEW_FADE, Interpolators.clampToProgress(DEACCEL,
                RECENTS_FADE_THRESHOLD, 1));
        return builder;
    }

    private AnimatorSetBuilder getAllAppsToOverviewAnimation() {
        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        builder.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.clampToProgress(DEACCEL,
                1 - ALL_APPS_CONTENT_FADE_THRESHOLD, 1));
        builder.setInterpolator(ANIM_OVERVIEW_FADE, Interpolators.clampToProgress(ACCEL,
                0f, 1 - RECENTS_FADE_THRESHOLD));
        return builder;
    }

    @Override
    protected AnimatorSetBuilder getAnimatorSetBuilderForStates(LauncherState fromState,
            LauncherState toState) {
        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        if (fromState == NORMAL && toState == OVERVIEW) {
            builder = getNormalToOverviewAnimation();
        } else if (fromState == OVERVIEW && toState == ALL_APPS) {
            builder = getOverviewToAllAppsAnimation();
        } else if (fromState == ALL_APPS && toState == OVERVIEW) {
            builder = getAllAppsToOverviewAnimation();
        }
        return builder;
    }

    @Override
    protected float initCurrentAnimation(@AnimationComponents int animComponents) {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);

        float startVerticalShift = mFromState.getVerticalProgress(mLauncher) * range;
        float endVerticalShift = mToState.getVerticalProgress(mLauncher) * range;

        float totalShift = endVerticalShift - startVerticalShift;

        final AnimatorSetBuilder builder = totalShift == 0 ? new AnimatorSetBuilder()
                : getAnimatorSetBuilderForStates(mFromState, mToState);
        updateAnimatorBuilderOnReinit(builder);

        cancelPendingAnim();

        if (mFromState == OVERVIEW && mToState == NORMAL
                && mOverviewPortraitStateTouchHelper.shouldSwipeDownReturnToApp()) {
            // Reset the state manager, when changing the interaction mode
            mLauncher.getStateManager().goToState(OVERVIEW, false /* animate */);
            mPendingAnimation = mOverviewPortraitStateTouchHelper
                    .createSwipeDownToTaskAppAnimation(maxAccuracy);
            mPendingAnimation.anim.setInterpolator(Interpolators.LINEAR);

            Runnable onCancelRunnable = () -> {
                cancelPendingAnim();
                clearState();
            };
            mCurrentAnimation = AnimatorPlaybackController.wrap(mPendingAnimation.anim, maxAccuracy,
                    onCancelRunnable);
            mLauncher.getStateManager().setCurrentUserControlledAnimation(mCurrentAnimation);
            totalShift = LayoutUtils.getShelfTrackingDistance(mLauncher,
                    mLauncher.getDeviceProfile());
        } else {
            mCurrentAnimation = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(mToState, builder, maxAccuracy, this::clearState,
                            animComponents);
        }

        if (totalShift == 0) {
            totalShift = Math.signum(mFromState.ordinal - mToState.ordinal)
                    * OverviewState.getDefaultSwipeHeight(mLauncher);
        }
        return 1 / totalShift;
    }

    /**
     * Give subclasses the chance to update the animation when we re-initialize towards a new state.
     */
    protected void updateAnimatorBuilderOnReinit(AnimatorSetBuilder builder) {
    }

    private void cancelPendingAnim() {
        if (mPendingAnimation != null) {
            mPendingAnimation.finish(false, Touch.SWIPE);
            mPendingAnimation = null;
        }
    }

    @Override
    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        super.updateSwipeCompleteAnimation(animator, expectedDuration, targetState,
                velocity, isFling);
        handleFirstSwipeToOverview(animator, expectedDuration, targetState, velocity, isFling);
    }

    private void handleFirstSwipeToOverview(final ValueAnimator animator,
            final long expectedDuration, final LauncherState targetState, final float velocity,
            final boolean isFling) {
        if (QUICKSTEP_SPRINGS.get() && mFromState == OVERVIEW && mToState == ALL_APPS
                && targetState == OVERVIEW) {
            mFinishFastOnSecondTouch = true;
        } else  if (mFromState == NORMAL && mToState == OVERVIEW && targetState == OVERVIEW) {
            mFinishFastOnSecondTouch = true;
            if (isFling && expectedDuration != 0) {
                // Update all apps interpolator to add a bit of overshoot starting from currFraction
                final float currFraction = mCurrentAnimation.getProgressFraction();
                mAllAppsInterpolatorWrapper.baseInterpolator = Interpolators.clampToProgress(
                        Interpolators.overshootInterpolatorForVelocity(velocity), currFraction, 1);
                animator.setDuration(Math.min(expectedDuration, ATOMIC_DURATION))
                        .setInterpolator(LINEAR);
            }
        } else {
            mFinishFastOnSecondTouch = false;
        }
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        super.onSwipeInteractionCompleted(targetState, logAction);
        if (mStartState == NORMAL && targetState == OVERVIEW) {
            RecentsModel.INSTANCE.get(mLauncher).onOverviewShown(true, TAG);
        }
    }

    /**
     * Whether the motion event is over the hotseat.
     *
     * @param launcher the launcher activity
     * @param ev the event to check
     * @return true if the event is over the hotseat
     */
    static boolean isTouchOverHotseat(Launcher launcher, MotionEvent ev) {
        return (ev.getY() >= getHotseatTop(launcher));
    }

    public static int getHotseatTop(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        int hotseatHeight = dp.hotseatBarSizePx + dp.getInsets().bottom;
        return launcher.getDragLayer().getHeight() - hotseatHeight;
    }

    private static class InterpolatorWrapper implements Interpolator {

        public TimeInterpolator baseInterpolator = LINEAR;

        @Override
        public float getInterpolation(float v) {
            return baseInterpolator.getInterpolation(v);
        }
    }
}
