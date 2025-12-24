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

import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.R;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.phone.PipAppIconOverlay;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.animation.Interpolators;
import com.android.wm.shell.shared.pip.PipContentOverlay;

/**
 * Animator that handles bounds animations for entering PIP.
 */
public class PipEnterAnimator extends ValueAnimator {
    private static final String TAG = PipEnterAnimator.class.getSimpleName();
    @NonNull private final SurfaceControl mLeash;
    private final SurfaceControl.Transaction mStartTransaction;
    private final SurfaceControl.Transaction mFinishTransaction;

    // Bounds updated by the evaluator as animator is running.
    private final Rect mAnimatedRect = new Rect();

    private final RectEvaluator mRectEvaluator;
    private final Rect mEndBounds = new Rect();
    private final @Surface.Rotation int mRotation;
    @Nullable private Runnable mAnimationStartCallback;
    @Nullable private Runnable mAnimationEndCallback;

    private PipSurfaceTransactionHelper.SurfaceControlTransactionFactory
            mSurfaceControlTransactionFactory;
    Matrix mTransformTensor = new Matrix();
    final float[] mMatrixTmp = new float[9];
    @Nullable private PipContentOverlay mContentOverlay;

    private PipAppIconOverlaySupplier mPipAppIconOverlaySupplier;

    // Internal state representing initial transform - cached to avoid recalculation.
    private final PointF mInitScale = new PointF();
    private final PointF mInitPos = new PointF();
    private final Rect mInitCrop = new Rect();

    private final Animator.AnimatorListener mAnimatorListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            super.onAnimationStart(animation);
            if (mAnimationStartCallback != null) {
                mAnimationStartCallback.run();
            }
            if (mStartTransaction != null) {
                onEnterAnimationUpdate(0f /* fraction */, mStartTransaction);
                mStartTransaction.apply();
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            if (mFinishTransaction != null) {
                onEnterAnimationUpdate(1f /* fraction */, mFinishTransaction);
            }
            if (mAnimationEndCallback != null) {
                mAnimationEndCallback.run();
            }
        }
    };

    private final AnimatorUpdateListener mAnimatorUpdateListener = new AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(@NonNull ValueAnimator animation) {
            final SurfaceControl.Transaction tx =
                    mSurfaceControlTransactionFactory.getTransaction();
            final float fraction = getAnimatedFraction();
            onEnterAnimationUpdate(fraction, tx);
            tx.apply();
        }
    };

    private final @NonNull PipSurfaceTransactionHelper mSurfaceTransactionHelper;

    public PipEnterAnimator(Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            @NonNull SurfaceControl leash,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction,
            @NonNull Rect endBounds,
            @Surface.Rotation int rotation) {
        mLeash = leash;
        mStartTransaction = startTransaction;
        mFinishTransaction = finishTransaction;
        mRectEvaluator = new RectEvaluator(mAnimatedRect);
        mEndBounds.set(endBounds);
        mRotation = rotation;
        mSurfaceControlTransactionFactory =
                new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();
        mPipAppIconOverlaySupplier = this::getAppIconOverlay;
        mSurfaceTransactionHelper = pipSurfaceTransactionHelper;

        final int enterAnimationDuration = context.getResources()
                .getInteger(R.integer.config_pipEnterAnimationDuration);
        setDuration(enterAnimationDuration);
        setFloatValues(0f, 1f);
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

    /**
     * Updates the transaction to reflect the state of PiP leash at a certain fraction during enter.
     *
     * @param fraction the fraction of the animator going from 0f to 1f.
     * @param tx the transaction to modify the transform of.
     */
    public void onEnterAnimationUpdate(float fraction, SurfaceControl.Transaction tx) {
        onEnterAnimationUpdate(mInitScale, mInitPos, mInitCrop, fraction, tx);
    }

    private void onEnterAnimationUpdate(PointF initScale, PointF initPos, Rect initCrop,
            float fraction, SurfaceControl.Transaction tx) {
        float scaleX = 1 + (initScale.x - 1) * (1 - fraction);
        float scaleY = 1 + (initScale.y - 1) * (1 - fraction);
        float posX = initPos.x + (mEndBounds.left - initPos.x) * fraction;
        float posY = initPos.y + (mEndBounds.top - initPos.y) * fraction;

        int normalizedRotation = mRotation;
        if (normalizedRotation == ROTATION_270) {
            normalizedRotation = -ROTATION_90;
        }
        float degrees = -normalizedRotation * 90f * fraction;

        Rect endCrop = new Rect(mEndBounds);
        endCrop.offsetTo(0, 0);
        mRectEvaluator.evaluate(fraction, initCrop, endCrop);
        tx.setCrop(mLeash, mAnimatedRect);

        mTransformTensor.reset();
        mTransformTensor.setScale(scaleX, scaleY);
        mTransformTensor.postTranslate(posX, posY);
        mTransformTensor.postRotate(degrees);
        tx.setMatrix(mLeash, mTransformTensor, mMatrixTmp);

        mSurfaceTransactionHelper.round(tx, mLeash, true /* applyCornerRadius */);
        mSurfaceTransactionHelper.shadow(tx, mLeash, true /* applyShadow */);

        if (mContentOverlay != null) {
            mContentOverlay.onAnimationUpdate(tx, 1f / scaleX, fraction, mEndBounds);
        }
    }

    /**
     * Caches the initial transform relevant values for the bounds enter animation.
     *
     * Since enter PiP makes use of a config-at-end transition, initial transform needs to be
     * calculated differently from generic transitions.
     * @param pipChange PiP change received as a transition target.
     */
    public void setEnterStartState(@NonNull TransitionInfo.Change pipChange) {
        PipUtils.calcStartTransform(pipChange, mInitScale, mInitPos, mInitCrop);
    }

    /**
     * Initializes and attaches an app icon overlay on top of the PiP layer.
     */
    public void setAppIconContentOverlay(Context context, Rect appBounds, Rect destinationBounds,
            ActivityInfo activityInfo, int appIconSizePx) {
        final SurfaceControl.Transaction tx =
                mSurfaceControlTransactionFactory.getTransaction();
        if (mContentOverlay != null) {
            mContentOverlay.detach(tx);
        }
        mContentOverlay = mPipAppIconOverlaySupplier.get(context, appBounds, destinationBounds,
                activityInfo, appIconSizePx);
        if (mContentOverlay != null) {
            mContentOverlay.attach(tx, mLeash);
        }
    }

    /**
     * Clears the {@link #mContentOverlay}, this should be done after the content overlay is
     * faded out.
     */
    public void clearAppIconOverlay() {
        if (mContentOverlay == null) {
            return;
        }
        SurfaceControl.Transaction tx = mSurfaceControlTransactionFactory.getTransaction();
        mContentOverlay.detach(tx);
        mContentOverlay = null;
    }

    @Nullable
    private PipAppIconOverlay getAppIconOverlay(
            Context context, Rect appBounds, Rect destinationBounds,
            ActivityInfo activityInfo, int iconSize) {
        if (activityInfo == null) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: activityInfo=null when trying to add app icon overlay", TAG);
            return null;
        }
        return new PipAppIconOverlay(context, appBounds, destinationBounds,
                new IconProvider(context).getIcon(activityInfo), iconSize);
    }

    /**
     * @return the app icon overlay leash; null if no overlay is attached.
     */
    @Nullable
    public SurfaceControl getContentOverlayLeash() {
        if (mContentOverlay == null) {
            return null;
        }
        return mContentOverlay.getLeash();
    }

    @VisibleForTesting
    void setSurfaceControlTransactionFactory(
            @NonNull PipSurfaceTransactionHelper.SurfaceControlTransactionFactory factory) {
        mSurfaceControlTransactionFactory = factory;
    }

    @VisibleForTesting
    interface PipAppIconOverlaySupplier {
        PipAppIconOverlay get(Context context, Rect appBounds, Rect destinationBounds,
                ActivityInfo activityInfo, int iconSize);
    }

    @VisibleForTesting
    void setPipAppIconOverlaySupplier(@NonNull PipAppIconOverlaySupplier supplier) {
        mPipAppIconOverlaySupplier = supplier;
    }
}
