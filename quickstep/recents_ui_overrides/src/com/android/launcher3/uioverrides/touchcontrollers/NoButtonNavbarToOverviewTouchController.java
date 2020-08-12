/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.launcher3.LauncherState.HINT_STATE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.graphics.OverviewScrim;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.OverviewToHomeAnim;
import com.android.quickstep.views.RecentsView;

/**
 * Touch controller which handles swipe and hold from the nav bar to go to Overview. Swiping above
 * the nav bar falls back to go to All Apps. Swiping from the nav bar without holding goes to the
 * first home screen instead of to Overview.
 */
public class NoButtonNavbarToOverviewTouchController extends FlingAndHoldTouchController {


    // How much of the movement to use for translating overview after swipe and hold.
    private static final float OVERVIEW_MOVEMENT_FACTOR = 0.25f;
    private static final long TRANSLATION_ANIM_MIN_DURATION_MS = 80;
    private static final float TRANSLATION_ANIM_VELOCITY_DP_PER_MS = 0.8f;

    private final RecentsView mRecentsView;

    private boolean mDidTouchStartInNavBar;
    private boolean mReachedOverview;
    // The last recorded displacement before we reached overview.
    private PointF mStartDisplacement = new PointF();
    private float mStartY;
    private AnimatorPlaybackController mOverviewResistYAnim;

    // Normal to Hint animation has flag SKIP_OVERVIEW, so we update this scrim with this animator.
    private ObjectAnimator mNormalToHintOverviewScrimAnimator;

    public NoButtonNavbarToOverviewTouchController(Launcher l) {
        super(l);
        mRecentsView = l.getOverviewPanel();
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "NoButtonNavbarToOverviewTouchController.ctor");
        }
    }

    @Override
    protected float getMotionPauseMaxDisplacement() {
        // No need to disallow pause when swiping up all the way up the screen (unlike
        // FlingAndHoldTouchController where user is probably intending to go to all apps).
        return Float.MAX_VALUE;
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        mDidTouchStartInNavBar = (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
        return super.canInterceptTouch(ev);
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (fromState == NORMAL && mDidTouchStartInNavBar) {
            return HINT_STATE;
        } else if (fromState == OVERVIEW && isDragTowardPositive) {
            // Don't allow swiping up to all apps.
            return OVERVIEW;
        }
        return super.getTargetState(fromState, isDragTowardPositive);
    }

    @Override
    protected float initCurrentAnimation(int animComponents) {
        float progressMultiplier = super.initCurrentAnimation(animComponents);
        if (mToState == HINT_STATE) {
            // Track the drag across the entire height of the screen.
            progressMultiplier = -1 / getShiftRange();
        }
        return progressMultiplier;
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        super.onDragStart(start, startDisplacement);
        if (mFromState == NORMAL && mToState == HINT_STATE) {
            mNormalToHintOverviewScrimAnimator = ObjectAnimator.ofFloat(
                    mLauncher.getDragLayer().getOverviewScrim(),
                    OverviewScrim.SCRIM_PROGRESS,
                    mFromState.getOverviewScrimAlpha(mLauncher),
                    mToState.getOverviewScrimAlpha(mLauncher));
        }
        mReachedOverview = false;
        mOverviewResistYAnim = null;
    }

    @Override
    protected void updateProgress(float fraction) {
        super.updateProgress(fraction);
        if (mNormalToHintOverviewScrimAnimator != null) {
            mNormalToHintOverviewScrimAnimator.setCurrentFraction(fraction);
        }
    }

    @Override
    public void onDragEnd(float velocity) {
        super.onDragEnd(velocity);
        mNormalToHintOverviewScrimAnimator = null;
        if (mLauncher.isInState(OVERVIEW)) {
            // Normally we would cleanup the state based on mCurrentAnimation, but since we stop
            // using that when we pause to go to Overview, we need to clean up ourselves.
            clearState();
        }
    }

    @Override
    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        super.updateSwipeCompleteAnimation(animator, expectedDuration, targetState, velocity,
                isFling);
        if (targetState == HINT_STATE) {
            // Normally we compute the duration based on the velocity and distance to the given
            // state, but since the hint state tracks the entire screen without a clear endpoint, we
            // need to manually set the duration to a reasonable value.
            animator.setDuration(HINT_STATE.getTransitionDuration(mLauncher));
        }
    }

    @Override
    protected void onMotionPauseChanged(boolean isPaused) {
        if (mCurrentAnimation == null) {
            return;
        }
        mNormalToHintOverviewScrimAnimator = null;
        mCurrentAnimation.dispatchOnCancelWithoutCancelRunnable(() -> {
            mLauncher.getStateManager().goToState(OVERVIEW, true, () -> {
                mOverviewResistYAnim = AnimatorControllerWithResistance
                        .createRecentsResistanceFromOverviewAnim(mLauncher, null)
                        .createPlaybackController();
                mReachedOverview = true;
                maybeSwipeInteractionToOverviewComplete();
            });
        });
        VibratorWrapper.INSTANCE.get(mLauncher).vibrate(OVERVIEW_HAPTIC);
    }

    private void maybeSwipeInteractionToOverviewComplete() {
        if (mReachedOverview && mDetector.isSettlingState()) {
            onSwipeInteractionCompleted(OVERVIEW, Touch.SWIPE);
        }
    }

    @Override
    protected boolean handlingOverviewAnim() {
        return mDidTouchStartInNavBar && super.handlingOverviewAnim();
    }

    @Override
    public boolean onDrag(float yDisplacement, float xDisplacement, MotionEvent event) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "NoButtonNavbarToOverviewTouchController");
        }
        if (mMotionPauseDetector.isPaused()) {
            if (!mReachedOverview) {
                mStartDisplacement.set(xDisplacement, yDisplacement);
                mStartY = event.getY();
            } else {
                mRecentsView.setTranslationX((xDisplacement - mStartDisplacement.x)
                        * OVERVIEW_MOVEMENT_FACTOR);
                float yProgress = (mStartDisplacement.y - yDisplacement) / mStartY;
                if (yProgress > 0 && mOverviewResistYAnim != null) {
                    mOverviewResistYAnim.setPlayFraction(yProgress);
                } else {
                    mRecentsView.setTranslationY((yDisplacement - mStartDisplacement.y)
                            * OVERVIEW_MOVEMENT_FACTOR);
                }
            }
            // Stay in Overview.
            return true;
        }
        return super.onDrag(yDisplacement, xDisplacement, event);
    }

    @Override
    protected void goToOverviewOnDragEnd(float velocity) {
        float velocityDp = dpiFromPx(velocity);
        boolean isFling = Math.abs(velocityDp) > 1;
        StateManager<LauncherState> stateManager = mLauncher.getStateManager();
        boolean goToHomeInsteadOfOverview = isFling;
        if (goToHomeInsteadOfOverview) {
            new OverviewToHomeAnim(mLauncher, ()-> onSwipeInteractionCompleted(NORMAL, Touch.FLING))
                    .animateWithVelocity(velocity);
        }
        if (mReachedOverview) {
            float distanceDp = dpiFromPx(Math.max(
                    Math.abs(mRecentsView.getTranslationX()),
                    Math.abs(mRecentsView.getTranslationY())));
            long duration = (long) Math.max(TRANSLATION_ANIM_MIN_DURATION_MS,
                    distanceDp / TRANSLATION_ANIM_VELOCITY_DP_PER_MS);
            mRecentsView.animate()
                    .translationX(0)
                    .translationY(0)
                    .setInterpolator(ACCEL_DEACCEL)
                    .setDuration(duration)
                    .withEndAction(goToHomeInsteadOfOverview
                            ? null
                            : this::maybeSwipeInteractionToOverviewComplete);
            if (!goToHomeInsteadOfOverview) {
                // Return to normal properties for the overview state.
                StateAnimationConfig config = new StateAnimationConfig();
                config.duration = duration;
                LauncherState state = mLauncher.getStateManager().getState();
                mLauncher.getStateManager().createAtomicAnimation(state, state, config).start();
            }
        }
    }

    private float dpiFromPx(float pixels) {
        return Utilities.dpiFromPx(pixels, mLauncher.getResources().getDisplayMetrics());
    }
}
