/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.pip2.animation;

import static android.view.Surface.ROTATION_90;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.Surface;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.R;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.shared.animation.Interpolators;

/**
 * Animator that handles bounds animations for exit-via-expanding PIP.
 */
public class PipExpandAnimator extends ValueAnimator {
    @NonNull
    private final SurfaceControl mLeash;
    private final SurfaceControl.Transaction mStartTransaction;
    private final SurfaceControl.Transaction mFinishTransaction;
    private final @Surface.Rotation int mRotation;
    private final Boolean mIsPipInDesktopMode;

    // optional callbacks for tracking animation start and end
    @Nullable
    private Runnable mAnimationStartCallback;
    @Nullable private Runnable mAnimationEndCallback;

    private final Rect mBaseBounds = new Rect();
    private final Rect mStartBounds = new Rect();
    private final Rect mEndBounds = new Rect();

    private final Rect mSourceRectHint = new Rect();
    private final Rect mSourceRectHintInsets = new Rect();
    private final Rect mZeroInsets = new Rect(0, 0, 0, 0);

    // Bounds updated by the evaluator as animator is running.
    private final Rect mAnimatedRect = new Rect();

    private PipSurfaceTransactionHelper.SurfaceControlTransactionFactory
            mSurfaceControlTransactionFactory;
    private final RectEvaluator mRectEvaluator;
    private final RectEvaluator mInsetEvaluator;
    private @NonNull final PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;

    private final Animator.AnimatorListener mAnimatorListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            super.onAnimationStart(animation);
            if (mAnimationStartCallback != null) {
                mAnimationStartCallback.run();
            }
            if (mStartTransaction != null) {
                onExpandAnimationUpdate(mStartTransaction, 0f);
                mStartTransaction.apply();
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            if (mFinishTransaction != null) {
                onExpandAnimationUpdate(mFinishTransaction, 1f);
                // Reset the corner radius at the end, if not in desktop windowing mode.
                if (!mIsPipInDesktopMode) {
                    mPipSurfaceTransactionHelper.round(mFinishTransaction, mLeash,
                            false /* applyCornerRadius */);
                }
            }
            if (mAnimationEndCallback != null) {
                mAnimationEndCallback.run();
            }
        }
    };

    private final ValueAnimator.AnimatorUpdateListener mAnimatorUpdateListener =
            new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                    final SurfaceControl.Transaction tx =
                            mSurfaceControlTransactionFactory.getTransaction();
                    final float fraction = getAnimatedFraction();
                    onExpandAnimationUpdate(tx, fraction);
                    tx.apply();
                }
            };

    public PipExpandAnimator(Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            @NonNull SurfaceControl leash,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction,
            @NonNull Rect baseBounds,
            @NonNull Rect startBounds,
            @NonNull Rect endBounds,
            @Nullable Rect sourceRectHint,
            @Surface.Rotation int rotation,
            boolean isPipInDesktopMode) {
        mLeash = leash;
        mStartTransaction = startTransaction;
        mFinishTransaction = finishTransaction;
        mBaseBounds.set(baseBounds);
        mStartBounds.set(startBounds);
        mAnimatedRect.set(startBounds);
        mEndBounds.set(endBounds);
        mRectEvaluator = new RectEvaluator(mAnimatedRect);
        mInsetEvaluator = new RectEvaluator(new Rect());
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mRotation = rotation;
        mIsPipInDesktopMode = isPipInDesktopMode;

        if (sourceRectHint == null || sourceRectHint.isEmpty()) {
            // Similar to enter animation, use a pseudo source rect hint on exit if app does not
            // provide one to get a unified exit animation experience.
            final float aspectRatio = mStartBounds.width() / (float) mStartBounds.height();
            mSourceRectHint.set(
                    PipUtils.getPseudoSourceRectHint(mBaseBounds, aspectRatio));
            mSourceRectHint.offsetTo(mBaseBounds.left, mBaseBounds.top);
        } else {
            mSourceRectHint.set(sourceRectHint);
        }
        mSourceRectHintInsets.set(
                mSourceRectHint.left - mBaseBounds.left,
                mSourceRectHint.top - mBaseBounds.top,
                mBaseBounds.right - mSourceRectHint.right,
                mBaseBounds.bottom - mSourceRectHint.bottom
        );

        mSurfaceControlTransactionFactory =
                new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();

        final int enterAnimationDuration = context.getResources()
                .getInteger(R.integer.config_pipEnterAnimationDuration);
        setDuration(enterAnimationDuration);

        setObjectValues(startBounds, endBounds);
        setEvaluator(mRectEvaluator);
        setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        addListener(mAnimatorListener);
        addUpdateListener(mAnimatorUpdateListener);
    }

    public void setAnimationStartCallback(@NonNull Runnable runnable) {
        mAnimationStartCallback = runnable;
    }

    public void setAnimationEndCallback(@NonNull Runnable runnable) {
        mAnimationEndCallback = runnable;
    }

    private void onExpandAnimationUpdate(SurfaceControl.Transaction tx, float fraction) {
        Rect insets = getInsets(fraction);
        if (mRotation == Surface.ROTATION_0) {
            mPipSurfaceTransactionHelper.scaleAndCrop(tx, mLeash, mSourceRectHint, mBaseBounds,
                    mAnimatedRect, insets, false /* isInPipDirection */, fraction);
        } else {
            // Fixed rotation case.
            Rect start = mStartBounds;
            Rect end = mEndBounds;
            float degrees, x, y;
            x = fraction * (end.left - start.left) + start.left;
            y = fraction * (end.top - start.top) + start.top;

            if (mRotation == ROTATION_90) {
                degrees = 90 * fraction;
            } else {
                degrees = -90 * fraction;
            }
            mPipSurfaceTransactionHelper.rotateAndScaleWithCrop(tx, mLeash, mBaseBounds,
                    mAnimatedRect, insets, degrees, x, y,
                    true /* isExpanding */, mRotation == ROTATION_90);
        }
        // Apply round corner during the animation, it will be reset at the end if not in desktop
        // windowing mode.
        mPipSurfaceTransactionHelper.round(tx, mLeash, true /* applyCornerRadius */)
                .shadow(tx, mLeash, false /* applyShadowRadius */);
    }

    private Rect getInsets(float fraction) {
        final Rect startInsets = mSourceRectHintInsets;
        final Rect endInsets = mZeroInsets;
        return mInsetEvaluator.evaluate(fraction, startInsets, endInsets);
    }

    @VisibleForTesting
    void setSurfaceControlTransactionFactory(
            @NonNull PipSurfaceTransactionHelper.SurfaceControlTransactionFactory factory) {
        mSurfaceControlTransactionFactory = factory;
    }
}
