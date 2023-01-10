/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.BaseActivity.INVISIBLE_ALL;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_PENDING_FLAGS;
import static com.android.launcher3.BaseActivity.PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Pair;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.window.BackEvent;
import android.window.BackProgressAnimator;
import android.window.IOnBackInvokedCallback;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.systemui.shared.system.QuickStepContract;

/**
 * Controls the animation of swiping back and returning to launcher.
 *
 * This is a two part animation. The first part is an animation that tracks gesture location to
 * scale and move the leaving app window. Once the gesture is committed, the second part takes over
 * the app window and plays the rest of app close transitions in one go.
 *
 * This animation is used only for apps that enable back dispatching via
 * {@link android.window.OnBackInvokedDispatcher}. The controller registers
 * an {@link IOnBackInvokedCallback} with WM Shell and receives back dispatches when a back
 * navigation to launcher starts.
 *
 * Apps using the legacy back dispatching will keep triggering the WALLPAPER_OPEN remote
 * transition registered in {@link QuickstepTransitionManager}.
 *
 */
public class LauncherBackAnimationController {
    private static final int CANCEL_TRANSITION_DURATION = 233;
    private static final float MIN_WINDOW_SCALE = 0.7f;
    private final QuickstepTransitionManager mQuickstepTransitionManager;
    private final Matrix mTransformMatrix = new Matrix();
    /** The window position at the beginning of the back animation. */
    private final Rect mStartRect = new Rect();
    /** The window position when the back gesture is cancelled. */
    private final RectF mCancelRect = new RectF();
    /** The current window position. */
    private final RectF mCurrentRect = new RectF();
    private final QuickstepLauncher mLauncher;
    private final int mWindowScaleMarginX;
    /** Max window translation in the Y axis. */
    private final int mWindowMaxDeltaY;
    private final float mWindowScaleEndCornerRadius;
    private final float mWindowScaleStartCornerRadius;
    private final Interpolator mCancelInterpolator;
    private final PointF mInitialTouchPos = new PointF();

    private RemoteAnimationTarget mBackTarget;
    private SurfaceControl.Transaction mTransaction = new SurfaceControl.Transaction();
    private boolean mSpringAnimationInProgress = false;
    private boolean mAnimatorSetInProgress = false;
    private float mBackProgress = 0;
    private boolean mBackInProgress = false;
    private IOnBackInvokedCallback mBackCallback;
    private BackProgressAnimator mProgressAnimator = new BackProgressAnimator();

    public LauncherBackAnimationController(
            QuickstepLauncher launcher,
            QuickstepTransitionManager quickstepTransitionManager) {
        mLauncher = launcher;
        mQuickstepTransitionManager = quickstepTransitionManager;
        mWindowScaleEndCornerRadius = QuickStepContract.supportsRoundedCornersOnWindows(
                mLauncher.getResources())
                ? mLauncher.getResources().getDimensionPixelSize(
                        R.dimen.swipe_back_window_corner_radius)
                : 0;
        mWindowScaleStartCornerRadius = QuickStepContract.getWindowCornerRadius(mLauncher);
        mWindowScaleMarginX = mLauncher.getResources().getDimensionPixelSize(
                R.dimen.swipe_back_window_scale_x_margin);
        mWindowMaxDeltaY = mLauncher.getResources().getDimensionPixelSize(
                R.dimen.swipe_back_window_max_delta_y);
        mCancelInterpolator =
                AnimationUtils.loadInterpolator(mLauncher, R.interpolator.back_cancel);
    }

    /**
     * Registers {@link IOnBackInvokedCallback} to receive back dispatches from shell.
     * @param handler Handler to the thread to run the animations on.
     */
    public void registerBackCallbacks(Handler handler) {
        mBackCallback = new IOnBackInvokedCallback.Stub() {
            @Override
            public void onBackCancelled() {
                handler.post(() -> {
                    resetPositionAnimated();
                    mProgressAnimator.reset();
                });
            }

            @Override
            public void onBackInvoked() {
                handler.post(() -> {
                    startTransition();
                    mProgressAnimator.reset();
                });
            }

            @Override
            public void onBackProgressed(BackEvent backEvent) {
                handler.post(() -> {
                    mProgressAnimator.onBackProgressed(backEvent);
                });
            }

            @Override
            public void onBackStarted(BackEvent backEvent) {
                handler.post(() -> {
                    startBack(backEvent);
                    mProgressAnimator.onBackStarted(backEvent, event -> {
                        mBackProgress = event.getProgress();
                        // TODO: Update once the interpolation curve spec is finalized.
                        mBackProgress =
                                1 - (1 - mBackProgress) * (1 - mBackProgress) * (1
                                        - mBackProgress);
                        updateBackProgress(mBackProgress, event);
                    });
                });
            }
        };
        SystemUiProxy.INSTANCE.get(mLauncher).setBackToLauncherCallback(mBackCallback);
    }

    private void resetPositionAnimated() {
        ValueAnimator cancelAnimator = ValueAnimator.ofFloat(0, 1);
        mCancelRect.set(mCurrentRect);
        cancelAnimator.setDuration(CANCEL_TRANSITION_DURATION);
        cancelAnimator.setInterpolator(mCancelInterpolator);
        cancelAnimator.addUpdateListener(
                animation -> {
                    updateCancelProgress((float) animation.getAnimatedValue());
                });
        cancelAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finishAnimation();
            }
        });
        cancelAnimator.start();
    }

    /** Unregisters the back to launcher callback in shell. */
    public void unregisterBackCallbacks() {
        if (mBackCallback != null) {
            SystemUiProxy.INSTANCE.get(mLauncher).clearBackToLauncherCallback(mBackCallback);
        }
        mProgressAnimator.reset();
        mBackCallback = null;
    }

    private void startBack(BackEvent backEvent) {
        mBackInProgress = true;
        RemoteAnimationTarget appTarget = backEvent.getDepartingAnimationTarget();

        if (appTarget == null) {
            return;
        }

        mTransaction.show(appTarget.leash).apply();
        mTransaction.setAnimationTransaction();
        mBackTarget = appTarget;
        mInitialTouchPos.set(backEvent.getTouchX(), backEvent.getTouchY());

        // TODO(b/218916755): Offset start rectangle in multiwindow mode.
        mStartRect.set(mBackTarget.windowConfiguration.getMaxBounds());
        mCurrentRect.set(mStartRect);
    }

    private void updateBackProgress(float progress, BackEvent event) {
        if (!mBackInProgress || mBackTarget == null) {
            return;
        }
        float screenWidth = mStartRect.width();
        float screenHeight = mStartRect.height();
        float width = Utilities.mapRange(progress, 1, MIN_WINDOW_SCALE) * screenWidth;
        float height = screenHeight / screenWidth * width;
        float deltaYRatio = (event.getTouchY() - mInitialTouchPos.y) / screenHeight;
        // Base the window movement in the Y axis on the touch movement in the Y axis.
        float deltaY = (float) Math.sin(deltaYRatio * Math.PI * 0.5f) * mWindowMaxDeltaY * progress;
        // Move the window along the Y axis.
        float top = (screenHeight - height) * 0.5f + deltaY;
        // Move the window along the X axis.
        float left = event.getSwipeEdge() == BackEvent.EDGE_RIGHT
                ? progress * mWindowScaleMarginX
                : screenWidth - progress * mWindowScaleMarginX - width;

        mCurrentRect.set(left, top, left + width, top + height);
        float cornerRadius = Utilities.mapRange(
                progress, mWindowScaleStartCornerRadius, mWindowScaleEndCornerRadius);
        applyTransform(mCurrentRect, cornerRadius);
    }

    private void updateCancelProgress(float progress) {
        if (mBackTarget == null) {
            return;
        }
        mCurrentRect.set(
                Utilities.mapRange(progress, mCancelRect.left, mStartRect.left),
                Utilities.mapRange(progress, mCancelRect.top, mStartRect.top),
                Utilities.mapRange(progress, mCancelRect.right, mStartRect.right),
                Utilities.mapRange(progress, mCancelRect.bottom, mStartRect.bottom));

        float endCornerRadius = Utilities.mapRange(
                mBackProgress, mWindowScaleStartCornerRadius, mWindowScaleEndCornerRadius);
        float cornerRadius = Utilities.mapRange(
                progress, endCornerRadius, mWindowScaleStartCornerRadius);
        applyTransform(mCurrentRect, cornerRadius);
    }

    /** Transform the target window to match the target rect. */
    private void applyTransform(RectF targetRect, float cornerRadius) {
        final float scale = targetRect.width() / mStartRect.width();
        mTransformMatrix.reset();
        mTransformMatrix.setScale(scale, scale);
        mTransformMatrix.postTranslate(targetRect.left, targetRect.top);

        if (mBackTarget.leash.isValid()) {
            mTransaction.setMatrix(mBackTarget.leash, mTransformMatrix, new float[9]);
            mTransaction.setWindowCrop(mBackTarget.leash, mStartRect);
            mTransaction.setCornerRadius(mBackTarget.leash, cornerRadius);
        }

        mTransaction.apply();
    }

    private void startTransition() {
        if (mBackTarget == null) {
            // Trigger transition system instead of custom transition animation.
            finishAnimation();
            return;
        }
        if (mLauncher.isDestroyed()) {
            return;
        }
        // TODO: Catch the moment when launcher becomes visible after the top app un-occludes
        //  launcher and start animating afterwards. Currently we occasionally get a flicker from
        //  animating when launcher is still invisible.
        if (mLauncher.hasSomeInvisibleFlag(PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION)) {
            mLauncher.addForceInvisibleFlag(INVISIBLE_BY_PENDING_FLAGS);
            mLauncher.getStateManager().moveToRestState();
        }

        // Explicitly close opened floating views (which is typically called from
        // Launcher#onResumed, but in the predictive back flow launcher is not resumed until
        // the transition is fully finished.)
        AbstractFloatingView.closeAllOpenViewsExcept(mLauncher, false, TYPE_REBIND_SAFE);
        float cornerRadius = Utilities.mapRange(
                mBackProgress, mWindowScaleStartCornerRadius, mWindowScaleEndCornerRadius);
        Pair<RectFSpringAnim, AnimatorSet> pair =
                mQuickstepTransitionManager.createWallpaperOpenAnimations(
                    new RemoteAnimationTarget[]{mBackTarget},
                    new RemoteAnimationTarget[0],
                    false /* fromUnlock */,
                    mCurrentRect,
                    cornerRadius);
        startTransitionAnimations(pair.first, pair.second);
        mLauncher.clearForceInvisibleFlag(INVISIBLE_ALL);
    }

    private void finishAnimation() {
        mBackTarget = null;
        mBackInProgress = false;
        mBackProgress = 0;
        mTransformMatrix.reset();
        mCancelRect.setEmpty();
        mCurrentRect.setEmpty();
        mStartRect.setEmpty();
        mInitialTouchPos.set(0, 0);
        mAnimatorSetInProgress = false;
        mSpringAnimationInProgress = false;
        SystemUiProxy.INSTANCE.get(mLauncher).onBackToLauncherAnimationFinished();
    }

    private void startTransitionAnimations(RectFSpringAnim springAnim, AnimatorSet anim) {
        mAnimatorSetInProgress = anim != null;
        mSpringAnimationInProgress = springAnim != null;
        if (springAnim != null) {
            springAnim.addAnimatorListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mSpringAnimationInProgress = false;
                            tryFinishBackAnimation();
                        }
                    }
            );
        }
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimatorSetInProgress = false;
                tryFinishBackAnimation();
            }
        });
        anim.start();
    }

    private void tryFinishBackAnimation() {
        if (!mSpringAnimationInProgress && !mAnimatorSetInProgress) {
            finishAnimation();
        }
    }
}
