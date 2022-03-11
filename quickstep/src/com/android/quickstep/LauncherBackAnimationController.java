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

import static com.android.launcher3.BaseActivity.INVISIBLE_ALL;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_PENDING_FLAGS;
import static com.android.launcher3.BaseActivity.PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;
import android.util.MathUtils;
import android.util.Pair;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.BackEvent;
import android.window.IOnBackInvokedCallback;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
/**
 * Controls the animation of swiping back and returning to launcher.
 *
 * This is a two part animation. The first part is an animation that tracks gesture location to
 * scale and move the leaving app window. Once the gesture is committed, the second part takes over
 * the app window and plays the rest of app close transitions in one go.
 *
 * This animation is used only for apps that enable back dispatching via
 * {@link android.view.OnBackInvokedDispatcher}. The controller registers
 * an {@link IOnBackInvokedCallback} with WM Shell and receives back dispatches when a back
 * navigation to launcher starts.
 *
 * Apps using the legacy back dispatching will keep triggering the WALLPAPER_OPEN remote
 * transition registered in {@link QuickstepTransitionManager}.
 *
 */
public class LauncherBackAnimationController {
    private static final int CANCEL_TRANSITION_DURATION = 150;
    private static final String TAG = "LauncherBackAnimationController";
    private final DeviceProfile mDeviceProfile;
    private final QuickstepTransitionManager mQuickstepTransitionManager;
    private final Matrix mTransformMatrix = new Matrix();
    private final RectF mTargetRectF = new RectF();
    private final RectF mStartRectF = new RectF();
    private final RectF mCurrentRect = new RectF();
    private final BaseQuickstepLauncher mLauncher;
    private final int mWindowScaleMarginX;
    private final int mWindowScaleMarginY;
    private final float mWindowScaleEndCornerRadius;
    private final float mWindowScaleStartCornerRadius;

    private RemoteAnimationTargetCompat mBackTarget;
    private SurfaceControl.Transaction mTransaction = new SurfaceControl.Transaction();
    private boolean mSpringAnimationInProgress = false;
    private boolean mAnimatorSetInProgress = false;
    @BackEvent.SwipeEdge
    private int mSwipeEdge;
    private float mBackProgress = 0;
    private boolean mBackInProgress = false;

    public LauncherBackAnimationController(
            DeviceProfile deviceProfile,
            BaseQuickstepLauncher launcher,
            QuickstepTransitionManager quickstepTransitionManager) {
        mDeviceProfile = deviceProfile;
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
        mWindowScaleMarginY = mLauncher.getResources().getDimensionPixelSize(
                R.dimen.swipe_back_window_scale_y_margin);
    }

    /**
     * Registers {@link IOnBackInvokedCallback} to receive back dispatches from shell.
     * @param handler Handler to the thread to run the animations on.
     */
    public void registerBackCallbacks(Handler handler) {
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.getNoCreate();
        if (systemUiProxy == null) {
            Log.e(TAG, "SystemUiProxy is null. Skip registering back invocation callbacks");
            return;
        }
        systemUiProxy.setBackToLauncherCallback(
                new IOnBackInvokedCallback.Stub() {
                    @Override
                    public void onBackCancelled() {
                        handler.post(() -> resetPositionAnimated());
                    }

                    @Override
                    public void onBackInvoked() {
                        handler.post(() -> startTransition());
                    }

                    @Override
                    public void onBackProgressed(BackEvent backEvent) {
                        mBackProgress = backEvent.getProgress();
                        // TODO: Update once the interpolation curve spec is finalized.
                        mBackProgress =
                                1 - (1 - mBackProgress) * (1 - mBackProgress) * (1
                                        - mBackProgress);
                        if (!mBackInProgress) {
                            startBack(backEvent);
                        } else {
                            updateBackProgress(mBackProgress);
                        }
                    }

                    public void onBackStarted() { }
                });
    }

    private void resetPositionAnimated() {
        ValueAnimator cancelAnimator = ValueAnimator.ofFloat(mBackProgress, 0);
        cancelAnimator.setDuration(CANCEL_TRANSITION_DURATION);
        cancelAnimator.addUpdateListener(
                animation -> {
                    updateBackProgress((float) animation.getAnimatedValue());
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
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.getNoCreate();
        if (systemUiProxy != null) {
            systemUiProxy.clearBackToLauncherCallback();
        }
    }

    private void startBack(BackEvent backEvent) {
        mBackInProgress = true;
        RemoteAnimationTarget appTarget = backEvent.getDepartingAnimationTarget();

        if (appTarget == null) {
            return;
        }

        mTransaction.show(appTarget.leash).apply();
        mTransaction.setAnimationTransaction();
        mBackTarget = new RemoteAnimationTargetCompat(appTarget);
        mSwipeEdge = backEvent.getSwipeEdge();
        float screenWidth = mDeviceProfile.widthPx;
        float screenHeight = mDeviceProfile.heightPx;
        float targetHeight = screenHeight - 2 * mWindowScaleMarginY;
        float targetWidth = targetHeight * screenWidth / screenHeight;
        float left;
        if (mSwipeEdge == BackEvent.EDGE_LEFT) {
            left = screenWidth - targetWidth - mWindowScaleMarginX;
        } else {
            left = mWindowScaleMarginX;
        }
        float top = mWindowScaleMarginY;
        // TODO(b/218916755): Offset start rectangle in multiwindow mode.
        mStartRectF.set(0, 0, screenWidth, screenHeight);
        mTargetRectF.set(left, top, targetWidth + left, targetHeight + top);
    }

    private void updateBackProgress(float progress) {
        if (mBackTarget == null) {
            return;
        }

        mCurrentRect.set(
                MathUtils.lerp(mStartRectF.left, mTargetRectF.left, progress),
                MathUtils.lerp(mStartRectF.top, mTargetRectF.top, progress),
                MathUtils.lerp(mStartRectF.right, mTargetRectF.right, progress),
                MathUtils.lerp(mStartRectF.bottom, mTargetRectF.bottom, progress));
        SyncRtSurfaceTransactionApplierCompat.SurfaceParams.Builder builder =
                new SyncRtSurfaceTransactionApplierCompat.SurfaceParams.Builder(mBackTarget.leash);

        Rect currentRect = new Rect();
        mCurrentRect.round(currentRect);

        // Scale the target window to match the currentRectF.
        final float scale = mCurrentRect.width() / mStartRectF.width();
        mTransformMatrix.reset();
        mTransformMatrix.setScale(scale, scale);
        mTransformMatrix.postTranslate(mCurrentRect.left, mCurrentRect.top);
        Rect startRect = new Rect();
        mStartRectF.round(startRect);
        float cornerRadius = Utilities.mapRange(
                progress, mWindowScaleStartCornerRadius, mWindowScaleEndCornerRadius);
        builder.withMatrix(mTransformMatrix)
                .withWindowCrop(startRect)
                .withCornerRadius(cornerRadius);
        SyncRtSurfaceTransactionApplierCompat.SurfaceParams surfaceParams = builder.build();

        if (surfaceParams.surface.isValid()) {
            surfaceParams.applyTo(mTransaction);
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

        Pair<RectFSpringAnim, AnimatorSet> pair =
                mQuickstepTransitionManager.createWallpaperOpenAnimations(
                    new RemoteAnimationTargetCompat[]{mBackTarget},
                    new RemoteAnimationTargetCompat[]{},
                    false /* fromUnlock */,
                    mCurrentRect);
        startTransitionAnimations(pair.first, pair.second);
        mLauncher.clearForceInvisibleFlag(INVISIBLE_ALL);
    }

    private void finishAnimation() {
        mBackTarget = null;
        mBackInProgress = false;
        mBackProgress = 0;
        mSwipeEdge = BackEvent.EDGE_LEFT;
        mTransformMatrix.reset();
        mTargetRectF.setEmpty();
        mCurrentRect.setEmpty();
        mStartRectF.setEmpty();
        mAnimatorSetInProgress = false;
        mSpringAnimationInProgress = false;
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.getNoCreate();
        if (systemUiProxy != null) {
            SystemUiProxy.INSTANCE.getNoCreate().onBackToLauncherAnimationFinished();
        }
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
