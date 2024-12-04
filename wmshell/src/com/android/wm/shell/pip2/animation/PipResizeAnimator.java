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

import android.animation.Animator;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;

/**
 * Animator that handles any resize related animation for PIP.
 */
public class PipResizeAnimator extends ValueAnimator
        implements ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener{
    @NonNull
    private final Context mContext;
    @NonNull
    private final SurfaceControl mLeash;
    @Nullable
    private SurfaceControl.Transaction mStartTx;
    @Nullable
    private SurfaceControl.Transaction mFinishTx;
    @Nullable
    private Runnable mAnimationStartCallback;
    @Nullable
    private Runnable mAnimationEndCallback;
    private RectEvaluator mRectEvaluator;
    private final Rect mBaseBounds = new Rect();
    private final Rect mStartBounds = new Rect();
    private final Rect mEndBounds = new Rect();
    private final Rect mAnimatedRect = new Rect();
    private final float mDelta;

    private final PipSurfaceTransactionHelper.SurfaceControlTransactionFactory
            mSurfaceControlTransactionFactory;

    public PipResizeAnimator(@NonNull Context context,
            @NonNull SurfaceControl leash,
            @Nullable SurfaceControl.Transaction startTransaction,
            @Nullable SurfaceControl.Transaction finishTransaction,
            @NonNull Rect baseBounds,
            @NonNull Rect startBounds,
            @NonNull Rect endBounds,
            int duration,
            float delta) {
        mContext = context;
        mLeash = leash;
        mStartTx = startTransaction;
        mFinishTx = finishTransaction;
        mSurfaceControlTransactionFactory =
                new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();

        mBaseBounds.set(baseBounds);
        mStartBounds.set(startBounds);
        mAnimatedRect.set(startBounds);
        mEndBounds.set(endBounds);
        mDelta = delta;

        mRectEvaluator = new RectEvaluator(mAnimatedRect);

        setObjectValues(startBounds, endBounds);
        addListener(this);
        addUpdateListener(this);
        setEvaluator(mRectEvaluator);
        // TODO: change this
        setDuration(duration);
    }

    public void setAnimationStartCallback(@NonNull Runnable runnable) {
        mAnimationStartCallback = runnable;
    }

    public void setAnimationEndCallback(@NonNull Runnable runnable) {
        mAnimationEndCallback = runnable;
    }

    @Override
    public void onAnimationStart(@NonNull Animator animation) {
        if (mAnimationStartCallback != null) {
            mAnimationStartCallback.run();
        }
        if (mStartTx != null) {
            setBoundsAndRotation(mStartTx, mLeash, mBaseBounds, mStartBounds, mDelta);
            mStartTx.apply();
        }
    }

    @Override
    public void onAnimationUpdate(@NonNull ValueAnimator animation) {
        final SurfaceControl.Transaction tx = mSurfaceControlTransactionFactory.getTransaction();
        final float fraction = getAnimatedFraction();
        final float degrees = (1.0f - fraction) * mDelta;
        setBoundsAndRotation(tx, mLeash, mBaseBounds, mAnimatedRect, degrees);
        tx.apply();
    }

    /**
     * Set a proper transform matrix for a leash to move it to given bounds with a certain rotation.
     *
     * @param baseBounds crop/buffer size relative to which we are scaling the leash.
     * @param targetBounds bounds to which we are scaling the leash.
     * @param degrees degrees of rotation - counter-clockwise is positive by convention.
     */
    public static void setBoundsAndRotation(SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect baseBounds, Rect targetBounds, float degrees) {
        Matrix transformTensor = new Matrix();
        final float[] mMatrixTmp = new float[9];
        final float scale = (float) targetBounds.width() / baseBounds.width();

        transformTensor.setScale(scale, scale);
        transformTensor.postTranslate(targetBounds.left, targetBounds.top);
        transformTensor.postRotate(degrees, targetBounds.centerX(), targetBounds.centerY());

        tx.setMatrix(leash, transformTensor, mMatrixTmp);
    }

    @Override
    public void onAnimationEnd(@NonNull Animator animation) {
        if (mFinishTx != null) {
            setBoundsAndRotation(mFinishTx, mLeash, mBaseBounds, mEndBounds, 0f);
        }
        if (mAnimationEndCallback != null) {
            mAnimationEndCallback.run();
        }
    }

    @Override
    public void onAnimationCancel(@NonNull Animator animation) {}

    @Override
    public void onAnimationRepeat(@NonNull Animator animation) {}
}
