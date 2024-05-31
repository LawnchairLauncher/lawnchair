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

import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.launcher3.LauncherAnimUtils.VIEW_BACKGROUND_COLOR;
import static com.android.launcher3.LauncherAnimUtils.newSingleUseCancelListener;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.HINT_STATE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMotionEvent;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;
import static com.android.launcher3.util.NavigationMode.THREE_BUTTONS;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_ONE_HANDED_ACTIVE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.internal.jank.Cuj;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.taskbar.LauncherTaskbarUIController;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.util.OverviewToHomeAnim;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

import java.util.function.BiConsumer;

/**
 * Touch controller which handles swipe and hold from the nav bar to go to Overview. Swiping above
 * the nav bar falls back to go to All Apps. Swiping from the nav bar without holding goes to the
 * first home screen instead of to Overview.
 */
public class NoButtonNavbarToOverviewTouchController extends PortraitStatesTouchController {
    private static final float ONE_HANDED_ACTIVATED_SLOP_MULTIPLIER = 2.5f;

    // How much of the movement to use for translating overview after swipe and hold.
    private static final float OVERVIEW_MOVEMENT_FACTOR = 0.25f;
    private static final long TRANSLATION_ANIM_MIN_DURATION_MS = 80;
    private static final float TRANSLATION_ANIM_VELOCITY_DP_PER_MS = 0.8f;

    private final VibratorWrapper mVibratorWrapper;
    private final BiConsumer<AnimatorSet, Long> mCancelSplitRunnable;
    private final RecentsView mRecentsView;
    private final MotionPauseDetector mMotionPauseDetector;
    private final float mMotionPauseMinDisplacement;

    private boolean mDidTouchStartInNavBar;
    private boolean mStartedOverview;
    private boolean mReachedOverview;
    // The last recorded displacement before we reached overview.
    private PointF mStartDisplacement = new PointF();
    private float mStartY;
    private AnimatorPlaybackController mOverviewResistYAnim;

    // Normal to Hint animation has flag SKIP_OVERVIEW, so we update this scrim with this animator.
    private ObjectAnimator mNormalToHintOverviewScrimAnimator;

    /**
     * @param cancelSplitRunnable Called when split placeholder view needs to be cancelled.
     *                            Animation should be added to the provided AnimatorSet
     */
    public NoButtonNavbarToOverviewTouchController(Launcher l,
            BiConsumer<AnimatorSet, Long> cancelSplitRunnable) {
        super(l);
        mRecentsView = l.getOverviewPanel();
        mMotionPauseDetector = new MotionPauseDetector(l);
        mMotionPauseMinDisplacement = ViewConfiguration.get(l).getScaledTouchSlop();
        mVibratorWrapper = VibratorWrapper.INSTANCE.get(l.getApplicationContext());
        mCancelSplitRunnable = cancelSplitRunnable;
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (!isTrackpadMotionEvent(ev) && DisplayController.getNavigationMode(mLauncher)
                == THREE_BUTTONS) {
            return false;
        }
        mDidTouchStartInNavBar = (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
        boolean isOneHandedModeActive = (SystemUiProxy.INSTANCE.get(mLauncher)
                .getLastSystemUiStateFlags() & SYSUI_STATE_ONE_HANDED_ACTIVE) != 0;
        // Reset touch slop multiplier to default 1.0f if one-handed-mode is not active
        mDetector.setTouchSlopMultiplier(
                isOneHandedModeActive ? ONE_HANDED_ACTIVATED_SLOP_MULTIPLIER : 1f /* default */);
        return super.canInterceptTouch(ev) && !mLauncher.isInState(HINT_STATE);
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (fromState == NORMAL && mDidTouchStartInNavBar) {
            return HINT_STATE;
        }
        return super.getTargetState(fromState, isDragTowardPositive);
    }

    @Override
    protected float initCurrentAnimation() {
        float progressMultiplier = super.initCurrentAnimation();
        if (mToState == HINT_STATE) {
            // Track the drag across the entire height of the screen.
            progressMultiplier = -1f / mLauncher.getDeviceProfile().heightPx;
        }
        return progressMultiplier;
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        if (mLauncher.isInState(ALL_APPS)) {
            LauncherTaskbarUIController controller =
                    ((QuickstepLauncher) mLauncher).getTaskbarUIController();
            if (controller != null) {
                controller.setShouldDelayLauncherStateAnim(true);
            }
        }

        super.onDragStart(start, startDisplacement);

        mMotionPauseDetector.clear();

        if (handlingOverviewAnim()) {
            InteractionJankMonitorWrapper.begin(mRecentsView, Cuj.CUJ_LAUNCHER_APP_SWIPE_TO_RECENTS,
                    "Home");
            mMotionPauseDetector.setOnMotionPauseListener(this::onMotionPauseDetected);
        }

        if (mFromState == NORMAL && mToState == HINT_STATE) {
            mNormalToHintOverviewScrimAnimator = ObjectAnimator.ofArgb(
                    mLauncher.getScrimView(),
                    VIEW_BACKGROUND_COLOR,
                    mFromState.getWorkspaceScrimColor(mLauncher),
                    mToState.getWorkspaceScrimColor(mLauncher));
        }
        mStartedOverview = false;
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
        LauncherTaskbarUIController controller =
                ((QuickstepLauncher) mLauncher).getTaskbarUIController();
        if (controller != null) {
            controller.setShouldDelayLauncherStateAnim(false);
        }

        if (mStartedOverview) {
            goToOverviewOrHomeOnDragEnd(velocity);
        } else {
            InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_APP_SWIPE_TO_RECENTS);
            super.onDragEnd(velocity);
        }

        mMotionPauseDetector.clear();
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
            long duration = HINT_STATE.getTransitionDuration(mLauncher, true /* isToState */);
            animator.setDuration(duration);
            AnimatorSet animatorSet = new AnimatorSet();
            mCancelSplitRunnable.accept(animatorSet, duration);
            animatorSet.start();
        }
        if (FeatureFlags.ENABLE_PREMIUM_HAPTICS_ALL_APPS.get() &&
                ((mFromState == NORMAL && mToState == ALL_APPS)
                        || (mFromState == ALL_APPS && mToState == NORMAL)) && isFling) {
            mVibratorWrapper.vibrateForDragBump();
        }
    }

    private void onMotionPauseDetected() {
        if (mCurrentAnimation == null) {
            return;
        }
        mNormalToHintOverviewScrimAnimator = null;
        mCurrentAnimation.getTarget().addListener(newSingleUseCancelListener(() ->
                mLauncher.getStateManager().goToState(OVERVIEW, true, forSuccessCallback(() -> {
                    mOverviewResistYAnim = AnimatorControllerWithResistance
                            .createRecentsResistanceFromOverviewAnim(mLauncher, null)
                            .createPlaybackController();
                    mReachedOverview = true;
                    maybeSwipeInteractionToOverviewComplete();
                }))));

        mCurrentAnimation.getTarget().removeListener(mClearStateOnCancelListener);
        mCurrentAnimation.dispatchOnCancel();
        mStartedOverview = true;
        VibratorWrapper.INSTANCE.get(mLauncher).vibrate(OVERVIEW_HAPTIC);
    }

    private void maybeSwipeInteractionToOverviewComplete() {
        if (mReachedOverview && !mDetector.isDraggingState()) {
            InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_APP_SWIPE_TO_RECENTS);
            onSwipeInteractionCompleted(OVERVIEW);
        }
    }

    private boolean handlingOverviewAnim() {
        int stateFlags = SystemUiProxy.INSTANCE.get(mLauncher).getLastSystemUiStateFlags();
        return mDidTouchStartInNavBar && mStartState == NORMAL
                && (stateFlags & SYSUI_STATE_OVERVIEW_DISABLED) == 0;
    }

    @Override
    public boolean onDrag(float yDisplacement, float xDisplacement, MotionEvent event) {
        if (mStartedOverview) {
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
        }

        float upDisplacement = -yDisplacement;
        mMotionPauseDetector.setDisallowPause(!handlingOverviewAnim()
                || upDisplacement < mMotionPauseMinDisplacement);
        mMotionPauseDetector.addPosition(event);

        // Stay in Overview.
        return mStartedOverview || super.onDrag(yDisplacement, xDisplacement, event);
    }

    private void goToOverviewOrHomeOnDragEnd(float velocity) {
        boolean goToHomeInsteadOfOverview = !mMotionPauseDetector.isPaused();
        if (goToHomeInsteadOfOverview) {
            new OverviewToHomeAnim(mLauncher, () -> onSwipeInteractionCompleted(NORMAL), null)
                    .animateWithVelocity(velocity);
            InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_APP_SWIPE_TO_RECENTS);
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
                    .setInterpolator(ACCELERATE_DECELERATE)
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
        return Utilities.dpiFromPx(pixels, mLauncher.getResources().getDisplayMetrics().densityDpi);
    }
}
