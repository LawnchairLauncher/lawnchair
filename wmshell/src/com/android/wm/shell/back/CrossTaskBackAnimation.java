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

package com.android.wm.shell.back;

import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.window.BackEvent.EDGE_RIGHT;

import static com.android.internal.jank.InteractionJankMonitor.CUJ_PREDICTIVE_BACK_CROSS_TASK;
import static com.android.wm.shell.back.BackAnimationConstants.UPDATE_SYSUI_FLAGS_THRESHOLD;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BACK_PREVIEW;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.RemoteException;
import android.view.Choreographer;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.window.BackEvent;
import android.window.BackMotionEvent;
import android.window.BackProgressAnimator;
import android.window.IOnBackInvokedCallback;

import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.internal.policy.SystemBarUtils;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.animation.Interpolators;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import javax.inject.Inject;

/**
 * Controls the animation of swiping back and returning to another task.
 *
 * <p>This is a two part animation. The first part is an animation that tracks gesture location to
 * scale and move the closing and entering app windows. Once the gesture is committed, the second
 * part remains the closing window in place. The entering window plays the rest of app opening
 * transition to enter full screen.
 *
 * <p>This animation is used only for apps that enable back dispatching via {@link
 * android.window.OnBackInvokedDispatcher}. The controller registers an {@link
 * IOnBackInvokedCallback} with WM Shell and receives back dispatches when a back navigation to
 * launcher starts.
 */
@ShellMainThread
public class CrossTaskBackAnimation extends ShellBackAnimation {
    private static final int BACKGROUNDCOLOR = 0x43433A;

    /**
     * Minimum scale of the entering and closing window.
     */
    private static final float MIN_WINDOW_SCALE = 0.8f;

    /** Duration of post animation after gesture committed. */
    private static final int POST_ANIMATION_DURATION_MS = 500;

    private final Rect mStartTaskRect = new Rect();
    private float mCornerRadius;
    private int mStatusbarHeight;

    // The closing window properties.
    private final Rect mClosingStartRect = new Rect();
    private final RectF mClosingCurrentRect = new RectF();

    // The entering window properties.
    private final Rect mEnteringStartRect = new Rect();
    private final RectF mEnteringCurrentRect = new RectF();

    private final PointF mInitialTouchPos = new PointF();
    private final Interpolator mPostAnimationInterpolator = Interpolators.EMPHASIZED;
    private final Interpolator mProgressInterpolator = Interpolators.BACK_GESTURE;
    private final Interpolator mVerticalMoveInterpolator = new DecelerateInterpolator();
    private final Matrix mTransformMatrix = new Matrix();

    private final float[] mTmpFloat9 = new float[9];
    private final BackAnimationRunner mBackAnimationRunner;
    private final BackAnimationBackground mBackground;
    private final Context mContext;
    private RemoteAnimationTarget mEnteringTarget;
    private RemoteAnimationTarget mClosingTarget;
    private final SurfaceControl.Transaction mTransaction = new SurfaceControl.Transaction();
    private boolean mBackInProgress = false;
    private boolean mIsRightEdge;
    private final PointF mTouchPos = new PointF();
    private IRemoteAnimationFinishedCallback mFinishCallback;
    private final BackProgressAnimator mProgressAnimator = new BackProgressAnimator();
    private float mInterWindowMargin;
    private float mVerticalMargin;

    @Inject
    public CrossTaskBackAnimation(Context context, BackAnimationBackground background) {
        mBackAnimationRunner = new BackAnimationRunner(
                new Callback(), new Runner(), context, CUJ_PREDICTIVE_BACK_CROSS_TASK);
        mBackground = background;
        mContext = context;
        loadResources();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        loadResources();
    }

    private void loadResources() {
        mCornerRadius = ScreenDecorationsUtils.getWindowCornerRadius(mContext);
        mStatusbarHeight = SystemBarUtils.getStatusBarHeight(mContext);
    }

    private static float mapRange(float value, float min, float max) {
        return min + (value * (max - min));
    }

    private float getInterpolatedProgress(float backProgress) {
        return mProgressInterpolator.getInterpolation(backProgress);
    }

    private void startBackAnimation() {
        if (mEnteringTarget == null || mClosingTarget == null) {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW, "Entering target or closing target is null.");
            return;
        }

        // Offset start rectangle to align task bounds.
        mStartTaskRect.set(mClosingTarget.windowConfiguration.getBounds());
        mStartTaskRect.offsetTo(0, 0);

        // inset bottom in case of pinned taskbar being present
        mStartTaskRect.inset(0, 0, 0, mClosingTarget.contentInsets.bottom);

        // Draw background.
        mBackground.ensureBackground(mClosingTarget.windowConfiguration.getBounds(),
                BACKGROUNDCOLOR, mTransaction, mStatusbarHeight);
        mInterWindowMargin = mContext.getResources()
                .getDimension(R.dimen.cross_task_back_inter_window_margin);
        mVerticalMargin = mContext.getResources()
                .getDimension(R.dimen.cross_task_back_vertical_margin);
    }

    private void updateGestureBackProgress(float progress, BackEvent event) {
        if (mEnteringTarget == null || mClosingTarget == null) {
            return;
        }

        float touchY = event.getTouchY();

        // The 'follow width' is the width of the window if it completely matches
        // the gesture displacement.
        final int width = mStartTaskRect.width();
        final int height = mStartTaskRect.height();

        float scale = mapRange(progress, 1, MIN_WINDOW_SCALE);
        float scaledWidth = scale * width;
        float scaledHeight = scale * height;

        // Base the window movement in the Y axis on the touch movement in the Y axis.
        float rawYDelta = touchY - mInitialTouchPos.y;
        float yDirection = rawYDelta < 0 ? -1 : 1;
        // limit yDelta interpretation to 1/2 of screen height in either direction
        float deltaYRatio = Math.min(height / 2f, Math.abs(rawYDelta)) / (height / 2f);
        float interpolatedYRatio = mVerticalMoveInterpolator.getInterpolation(deltaYRatio);
        // limit y-shift so surface never passes 8dp screen margin
        float deltaY = yDirection * interpolatedYRatio * Math.max(0f,
                (height - scaledHeight) / 2f - mVerticalMargin);

        // Move the window along the Y axis.
        float scaledTop = (height - scaledHeight) * 0.5f + deltaY;
        // Move the window along the X axis.
        float right;
        if (mIsRightEdge) {
            right = (width - scaledWidth) * 0.5f + scaledWidth;
        } else {
            right = width - (progress * mVerticalMargin);
        }
        float left = right - scaledWidth;

        mClosingCurrentRect.set(left, scaledTop, right, scaledTop + scaledHeight);
        mEnteringCurrentRect.set(left - scaledWidth - mInterWindowMargin, scaledTop,
                left - mInterWindowMargin, scaledTop + scaledHeight);

        applyTransform(mClosingTarget.leash, mClosingCurrentRect, mCornerRadius);
        applyTransform(mEnteringTarget.leash, mEnteringCurrentRect, mCornerRadius);
        applyTransaction();

        mBackground.customizeStatusBarAppearance((int) scaledTop);
    }

    private void updatePostCommitClosingAnimation(float progress) {
        float targetLeft =
                mStartTaskRect.left + (1 - MIN_WINDOW_SCALE) * mStartTaskRect.width() / 2;
        float targetTop =
                mStartTaskRect.top + (1 - MIN_WINDOW_SCALE) * mStartTaskRect.height() / 2;
        float targetWidth = mStartTaskRect.width() * MIN_WINDOW_SCALE;
        float targetHeight = mStartTaskRect.height() * MIN_WINDOW_SCALE;

        float left = mapRange(progress, mClosingStartRect.left, targetLeft);
        float top = mapRange(progress, mClosingStartRect.top, targetTop);
        float width = mapRange(progress, mClosingStartRect.width(), targetWidth);
        float height = mapRange(progress, mClosingStartRect.height(), targetHeight);
        if (mClosingTarget.leash != null && mClosingTarget.leash.isValid()) {
            mTransaction.setLayer(mClosingTarget.leash, 0);
        }

        mClosingCurrentRect.set(left, top, left + width, top + height);
        applyTransform(mClosingTarget.leash, mClosingCurrentRect, mCornerRadius);
    }

    private void updatePostCommitEnteringAnimation(float progress) {
        float left = mapRange(progress, mEnteringStartRect.left, mStartTaskRect.left);
        float top = mapRange(progress, mEnteringStartRect.top, mStartTaskRect.top);
        float width = mapRange(progress, mEnteringStartRect.width(), mStartTaskRect.width());
        float height = mapRange(progress, mEnteringStartRect.height(), mStartTaskRect.height());

        mEnteringCurrentRect.set(left, top, left + width, top + height);
        applyTransform(mEnteringTarget.leash, mEnteringCurrentRect, mCornerRadius);
    }

    /** Transform the target window to match the target rect. */
    private void applyTransform(SurfaceControl leash, RectF targetRect, float cornerRadius) {
        if (leash == null || !leash.isValid()) {
            return;
        }

        final float scale = targetRect.width() / mStartTaskRect.width();
        mTransformMatrix.reset();
        mTransformMatrix.setScale(scale, scale);
        mTransformMatrix.postTranslate(targetRect.left, targetRect.top);
        mTransaction.setMatrix(leash, mTransformMatrix, mTmpFloat9)
                .setWindowCrop(leash, mStartTaskRect)
                .setCornerRadius(leash, cornerRadius);
    }

    private void applyTransaction() {
        mTransaction.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        mTransaction.apply();
    }

    private void finishAnimation() {
        if (mEnteringTarget != null) {
            mEnteringTarget.leash.release();
            mEnteringTarget = null;
        }
        if (mClosingTarget != null) {
            mClosingTarget.leash.release();
            mClosingTarget = null;
        }

        if (mBackground != null) {
            mBackground.removeBackground(mTransaction);
        }
        applyTransaction();
        mBackInProgress = false;
        mTransformMatrix.reset();
        mClosingCurrentRect.setEmpty();
        mInitialTouchPos.set(0, 0);

        if (mFinishCallback != null) {
            try {
                mFinishCallback.onAnimationFinished();
            } catch (RemoteException e) {
                ProtoLog.e(WM_SHELL_BACK_PREVIEW,
                        "RemoteException when invoking onAnimationFinished callback");
            }
            mFinishCallback = null;
        }
    }

    private void onGestureProgress(@NonNull BackEvent backEvent) {
        if (!mBackInProgress) {
            mBackInProgress = true;
        }
        float progress = backEvent.getProgress();
        mTouchPos.set(backEvent.getTouchX(), backEvent.getTouchY());
        updateGestureBackProgress(getInterpolatedProgress(progress), backEvent);
    }

    private void onGestureCommitted() {
        if (mEnteringTarget == null || mClosingTarget == null) {
            finishAnimation();
            return;
        }

        // We enter phase 2 of the animation, the starting coordinates for phase 2 are the current
        // coordinate of the gesture driven phase.
        mEnteringCurrentRect.round(mEnteringStartRect);
        mClosingCurrentRect.round(mClosingStartRect);

        ValueAnimator valueAnimator =
                ValueAnimator.ofFloat(1f, 0f).setDuration(POST_ANIMATION_DURATION_MS);
        valueAnimator.setInterpolator(mPostAnimationInterpolator);
        valueAnimator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            updatePostCommitEnteringAnimation(progress);
            updatePostCommitClosingAnimation(progress);
            if (progress > 1 - UPDATE_SYSUI_FLAGS_THRESHOLD) {
                mBackground.resetStatusBarCustomization();
            }
            applyTransaction();
        });

        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mBackground.resetStatusBarCustomization();
                finishAnimation();
            }
        });
        valueAnimator.start();
    }

    @Override
    public BackAnimationRunner getRunner() {
        return mBackAnimationRunner;
    }

    private final class Callback extends IOnBackInvokedCallback.Default {
        @Override
        public void onBackStarted(BackMotionEvent backEvent) {
            // in case we're still animating an onBackCancelled event, let's remove the finish-
            // callback from the progress animator to prevent calling finishAnimation() before
            // restarting a new animation
            mProgressAnimator.removeOnBackCancelledFinishCallback();

            mIsRightEdge = backEvent.getSwipeEdge() == EDGE_RIGHT;
            mInitialTouchPos.set(backEvent.getTouchX(), backEvent.getTouchY());
            mProgressAnimator.onBackStarted(backEvent,
                    CrossTaskBackAnimation.this::onGestureProgress);
        }

        @Override
        public void onBackProgressed(@NonNull BackMotionEvent backEvent) {
            mProgressAnimator.onBackProgressed(backEvent);
        }

        @Override
        public void onBackCancelled() {
            mProgressAnimator.onBackCancelled(CrossTaskBackAnimation.this::finishAnimation);
        }

        @Override
        public void onBackInvoked() {
            mProgressAnimator.reset();
            onGestureCommitted();
        }
    }

    private final class Runner extends IRemoteAnimationRunner.Default {
        @Override
        public void onAnimationStart(int transit, RemoteAnimationTarget[] apps,
                RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
                IRemoteAnimationFinishedCallback finishedCallback) {
            ProtoLog.d(WM_SHELL_BACK_PREVIEW, "Start back to task animation.");
            for (RemoteAnimationTarget a : apps) {
                if (a.mode == MODE_CLOSING) {
                    mClosingTarget = a;
                }
                if (a.mode == MODE_OPENING) {
                    mEnteringTarget = a;
                }
            }

            startBackAnimation();
            mFinishCallback = finishedCallback;
        }
    }
}
