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

package com.android.quickstep.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;

/**
 * An {@link Animator} that animates an Activity to PiP (picture-in-picture) window when
 * swiping up (in gesture navigation mode). Note that this class is derived from
 * {@link com.android.wm.shell.pip.PipAnimationController.PipTransitionAnimator}.
 *
 * TODO: consider sharing this class including the animator and leash operations between
 * Launcher and SysUI. Also, there should be one source of truth for the corner radius of the
 * PiP window, which would ideally be on SysUI side as well.
 */
public class SwipePipToHomeAnimator extends ValueAnimator implements
        ValueAnimator.AnimatorUpdateListener {
    private final int mTaskId;
    private final ComponentName mComponentName;
    private final SurfaceControl mLeash;
    private final Rect mStartBounds = new Rect();
    private final Rect mDestinationBounds = new Rect();
    private final SurfaceTransactionHelper mSurfaceTransactionHelper;

    /** for calculating the transform in {@link #onAnimationUpdate(ValueAnimator)} */
    private final RectEvaluator mRectEvaluator = new RectEvaluator(new Rect());
    private final RectEvaluator mInsetsEvaluator = new RectEvaluator(new Rect());
    private final Rect mSourceHintRectInsets = new Rect();
    private final Rect mSourceInsets = new Rect();

    /**
     * Flag to avoid the double-end problem since the leash would have been released
     * after the first end call and any further operations upon it would lead to NPE.
     */
    private boolean mHasAnimationEnded;

    public SwipePipToHomeAnimator(int taskId,
            @NonNull ComponentName componentName,
            @NonNull SurfaceControl leash,
            @NonNull Rect sourceRectHint,
            @NonNull Rect startBounds,
            @NonNull Rect destinationBounds) {
        mTaskId = taskId;
        mComponentName = componentName;
        mLeash = leash;
        mStartBounds.set(startBounds);
        mDestinationBounds.set(destinationBounds);
        mSurfaceTransactionHelper = new SurfaceTransactionHelper();

        mSourceHintRectInsets.set(sourceRectHint.left - startBounds.left,
                sourceRectHint.top - startBounds.top,
                startBounds.right - sourceRectHint.right,
                startBounds.bottom - sourceRectHint.bottom);

        addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                SwipePipToHomeAnimator.this.onAnimationEnd();
            }
        });
        addUpdateListener(this);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animator) {
        if (mHasAnimationEnded) return;

        final float fraction = animator.getAnimatedFraction();
        final Rect bounds = mRectEvaluator.evaluate(fraction, mStartBounds, mDestinationBounds);
        final Rect insets = mInsetsEvaluator.evaluate(fraction, mSourceInsets,
                mSourceHintRectInsets);
        final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        mSurfaceTransactionHelper.scaleAndCrop(tx, mLeash, mStartBounds, bounds, insets);
        tx.setCornerRadius(mLeash, 0).apply();
    }

    public int getTaskId() {
        return mTaskId;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public Rect getDestinationBounds() {
        return mDestinationBounds;
    }

    private void onAnimationEnd() {
        if (mHasAnimationEnded) return;

        final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        mSurfaceTransactionHelper.resetScale(tx, mLeash, mDestinationBounds);
        mSurfaceTransactionHelper.crop(tx, mLeash, mDestinationBounds);
        tx.setCornerRadius(mLeash, 0).apply();
        mHasAnimationEnded = true;
    }

    /**
     * Slim version of {@link com.android.wm.shell.pip.PipSurfaceTransactionHelper}
     */
    private static final class SurfaceTransactionHelper {
        private final Matrix mTmpTransform = new Matrix();
        private final float[] mTmpFloat9 = new float[9];
        private final RectF mTmpSourceRectF = new RectF();
        private final Rect mTmpDestinationRect = new Rect();

        private void scaleAndCrop(SurfaceControl.Transaction tx, SurfaceControl leash,
                Rect sourceBounds, Rect destinationBounds, Rect insets) {
            mTmpSourceRectF.set(sourceBounds);
            mTmpDestinationRect.set(sourceBounds);
            mTmpDestinationRect.inset(insets);
            // Scale by the shortest edge and offset such that the top/left of the scaled inset
            // source rect aligns with the top/left of the destination bounds
            final float scale = sourceBounds.width() <= sourceBounds.height()
                    ? (float) destinationBounds.width() / sourceBounds.width()
                    : (float) destinationBounds.height() / sourceBounds.height();
            final float left = destinationBounds.left - insets.left * scale;
            final float top = destinationBounds.top - insets.top * scale;
            mTmpTransform.setScale(scale, scale);
            tx.setMatrix(leash, mTmpTransform, mTmpFloat9)
                    .setWindowCrop(leash, mTmpDestinationRect)
                    .setPosition(leash, left, top);
        }

        private void resetScale(SurfaceControl.Transaction tx, SurfaceControl leash,
                Rect destinationBounds) {
            tx.setMatrix(leash, Matrix.IDENTITY_MATRIX, mTmpFloat9)
                    .setPosition(leash, destinationBounds.left, destinationBounds.top);
        }

        private void crop(SurfaceControl.Transaction tx, SurfaceControl leash,
                Rect destinationBounds) {
            tx.setWindowCrop(leash, destinationBounds.width(), destinationBounds.height())
                    .setPosition(leash, destinationBounds.left, destinationBounds.top);
        }
    }
}
