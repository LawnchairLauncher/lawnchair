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

import static android.view.View.TRANSLATION_X;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OPTIONS;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.anim.Interpolators.DEACCEL_3;
import static com.android.launcher3.touch.AbstractStateChangeTouchController.SUCCESS_TRANSITION_PROGRESS;
import static com.android.quickstep.TouchInteractionService.EDGE_NAV_BAR;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.views.RecentsView;

/**
 * Handles swiping up on the nav bar to go home from launcher, e.g. overview or all apps.
 */
public class NavBarToHomeTouchController implements TouchController, SwipeDetector.Listener {

    private static final Interpolator PULLBACK_INTERPOLATOR = DEACCEL_3;

    private final Launcher mLauncher;
    private final SwipeDetector mSwipeDetector;
    private final float mPullbackDistance;

    private boolean mNoIntercept;
    private LauncherState mStartState;
    private LauncherState mEndState = NORMAL;
    private AnimatorPlaybackController mCurrentAnimation;

    public NavBarToHomeTouchController(Launcher launcher) {
        mLauncher = launcher;
        mSwipeDetector = new SwipeDetector(mLauncher, this, SwipeDetector.VERTICAL);
        mPullbackDistance = mLauncher.getResources().getDimension(R.dimen.home_pullback_distance);
    }

    @Override
    public final boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mStartState = mLauncher.getStateManager().getState();
            mNoIntercept = !canInterceptTouch(ev);
            if (mNoIntercept) {
                return false;
            }
            mSwipeDetector.setDetectableScrollConditions(SwipeDetector.DIRECTION_POSITIVE, false);
        }

        if (mNoIntercept) {
            return false;
        }

        onControllerTouchEvent(ev);
        return mSwipeDetector.isDraggingOrSettling();
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        boolean cameFromNavBar = (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
        if (!cameFromNavBar) {
            return false;
        }
        if (mStartState == OVERVIEW || mStartState == ALL_APPS || mStartState == OPTIONS) {
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return true;
        }
        return false;
    }

    @Override
    public final boolean onControllerTouchEvent(MotionEvent ev) {
        return mSwipeDetector.onTouchEvent(ev);
    }

    private float getShiftRange() {
        return mLauncher.getDeviceProfile().heightPx;
    }

    @Override
    public void onDragStart(boolean start) {
        initCurrentAnimation();
    }

    private void initCurrentAnimation() {
        long accuracy = (long) (getShiftRange() * 2);
        final AnimatorSet anim = new AnimatorSet();
        if (mStartState == OVERVIEW) {
            RecentsView recentsView = mLauncher.getOverviewPanel();
            float pullbackDist = mPullbackDistance;
            if (!recentsView.isRtl()) {
                pullbackDist = -pullbackDist;
            }
            Animator pullback = ObjectAnimator.ofFloat(recentsView, TRANSLATION_X, pullbackDist);
            pullback.setInterpolator(PULLBACK_INTERPOLATOR);
            anim.play(pullback);
        } else if (mStartState == ALL_APPS) {
            AnimatorSetBuilder builder = new AnimatorSetBuilder();
            AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
            Animator allAppsProgress = ObjectAnimator.ofFloat(allAppsController, ALL_APPS_PROGRESS,
                    -mPullbackDistance / allAppsController.getShiftRange());
            allAppsProgress.setInterpolator(PULLBACK_INTERPOLATOR);
            builder.play(allAppsProgress);
            // Slightly fade out all apps content to further distinguish from scrolling.
            builder.setInterpolator(AnimatorSetBuilder.ANIM_ALL_APPS_FADE, Interpolators
                    .mapToProgress(PULLBACK_INTERPOLATOR, 0, 0.5f));
            AnimationConfig config = new AnimationConfig();
            config.duration = accuracy;
            allAppsController.setAlphas(mEndState.getVisibleElements(mLauncher), config, builder);
            anim.play(builder.build());
        } else if (mStartState == OPTIONS) {
            float scale = OPTIONS.getWorkspaceScaleAndTranslation(mLauncher)[0];
            float height = mLauncher.getWorkspace().getHeight();
            float newHeight = height * scale - mPullbackDistance * 2;
            float newScale = newHeight / height;
            Animator pullback = ObjectAnimator.ofFloat(mLauncher.getWorkspace(), SCALE_PROPERTY, scale, newScale);
            pullback.setInterpolator(PULLBACK_INTERPOLATOR);
            anim.play(pullback);
        }
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mLauncher);
        if (topView != null) {
            Animator hintCloseAnim = topView.createHintCloseAnim(mPullbackDistance);
            if (hintCloseAnim != null) {
                hintCloseAnim.setInterpolator(PULLBACK_INTERPOLATOR);
                anim.play(hintCloseAnim);
            }
        }
        anim.setDuration(accuracy);
        mCurrentAnimation = AnimatorPlaybackController.wrap(anim, accuracy, this::clearState);
    }

    private void clearState() {
        mCurrentAnimation = null;
        mSwipeDetector.finishedScrolling();
        mSwipeDetector.setDetectableScrollConditions(0, false);
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        // Only allow swipe up.
        displacement = Math.min(0, displacement);
        float progress = getProgress(displacement, 0, getShiftRange());
        mCurrentAnimation.setPlayFraction(progress);
        return true;
    }

    public static float getProgress(float current, float min, float max) {
        return Math.abs(current - min) / Math.abs(max - min);
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        float progress = mCurrentAnimation.getProgressFraction();
        float interpolatedProgress = PULLBACK_INTERPOLATOR.getInterpolation(progress);
        boolean success = interpolatedProgress >= SUCCESS_TRANSITION_PROGRESS
                || (velocity < 0 && fling);
        if (success) {
            mLauncher.getStateManager().goToState(mEndState, true,
                    () -> onSwipeInteractionCompleted(mEndState));
            AbstractFloatingView topOpenView = AbstractFloatingView.getTopOpenView(mLauncher);
            if (topOpenView != null) {
                AbstractFloatingView.closeAllOpenViews(mLauncher);
            }
        } else {
            // Quickly return to the state we came from (we didn't move far).
            ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
            anim.setFloatValues(progress, 0);
            anim.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    onSwipeInteractionCompleted(mStartState);
                }
            });
            anim.setDuration(80).start();
        }
    }

    private void onSwipeInteractionCompleted(LauncherState targetState) {
        clearState();
        mLauncher.getStateManager().goToState(targetState, false /* animated */);
    }
}