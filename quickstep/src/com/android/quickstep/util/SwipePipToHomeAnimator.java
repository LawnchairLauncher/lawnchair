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

import static com.android.systemui.shared.system.InteractionJankMonitorWrapper.CUJ_APP_CLOSE_TO_PIP;

import android.animation.Animator;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceSession;
import android.view.View;
import android.window.PictureInPictureSurfaceTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.util.Themes;
import com.android.systemui.shared.pip.PipSurfaceTransactionHelper;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

/**
 * An {@link Animator} that animates an Activity to PiP (picture-in-picture) window when
 * swiping up (in gesture navigation mode). Note that this class is derived from
 * {@link com.android.wm.shell.pip.PipAnimationController.PipTransitionAnimator}.
 *
 * TODO: consider sharing this class including the animator and leash operations between
 * Launcher and SysUI. Also, there should be one source of truth for the corner radius of the
 * PiP window, which would ideally be on SysUI side as well.
 */
public class SwipePipToHomeAnimator extends ValueAnimator {
    private static final String TAG = SwipePipToHomeAnimator.class.getSimpleName();

    public static final float FRACTION_START = 0f;
    public static final float FRACTION_END = 1f;

    private final int mTaskId;
    private final ComponentName mComponentName;
    private final SurfaceControl mLeash;
    private final Rect mAppBounds = new Rect();
    private final Rect mStartBounds = new Rect();
    private final Rect mDestinationBounds = new Rect();
    private final PipSurfaceTransactionHelper mSurfaceTransactionHelper;

    /** for calculating the transform in {@link #onAnimationUpdate(ValueAnimator)} */
    private final RectEvaluator mRectEvaluator = new RectEvaluator(new Rect());
    private final RectEvaluator mInsetsEvaluator = new RectEvaluator(new Rect());
    private final Rect mSourceHintRectInsets;
    private final Rect mSourceInsets = new Rect();

    /** for rotation via {@link #setFromRotation(TaskViewSimulator, int)} */
    private @RecentsOrientedState.SurfaceRotation int mFromRotation = Surface.ROTATION_0;
    private final Rect mDestinationBoundsTransformed = new Rect();
    private final Rect mDestinationBoundsAnimation = new Rect();

    /**
     * Flag to avoid the double-end problem since the leash would have been released
     * after the first end call and any further operations upon it would lead to NPE.
     */
    private boolean mHasAnimationEnded;

    /**
     * An overlay used to mask changes in content when entering PiP for apps that aren't seamless.
     */
    @Nullable
    private SurfaceControl mContentOverlay;

    /**
     * @param taskId Task id associated with this animator, see also {@link #getTaskId()}
     * @param componentName Component associated with this animator,
     *                      see also {@link #getComponentName()}
     * @param leash {@link SurfaceControl} this animator operates on
     * @param sourceRectHint See the definition in {@link android.app.PictureInPictureParams}
     * @param appBounds Bounds of the application, sourceRectHint is based on this bounds
     * @param startBounds Bounds of the application when this animator starts. This can be
     *                    different from the appBounds if user has swiped a certain distance and
     *                    Launcher has performed transform on the leash.
     * @param destinationBounds Bounds of the destination this animator ends to
     * @param cornerRadius Corner radius in pixel value for PiP window
     */
    public SwipePipToHomeAnimator(int taskId,
            @NonNull ComponentName componentName,
            @NonNull SurfaceControl leash,
            @Nullable Rect sourceRectHint,
            @NonNull Rect appBounds,
            @NonNull Rect startBounds,
            @NonNull Rect destinationBounds,
            int cornerRadius,
            @NonNull View view) {
        mTaskId = taskId;
        mComponentName = componentName;
        mLeash = leash;
        mAppBounds.set(appBounds);
        mStartBounds.set(startBounds);
        mDestinationBounds.set(destinationBounds);
        mDestinationBoundsTransformed.set(mDestinationBounds);
        mDestinationBoundsAnimation.set(mDestinationBounds);
        mSurfaceTransactionHelper = new PipSurfaceTransactionHelper(cornerRadius);

        if (sourceRectHint == null) {
            mSourceHintRectInsets = null;

            // Create a new overlay layer
            SurfaceSession session = new SurfaceSession();
            mContentOverlay = new SurfaceControl.Builder(session)
                    .setCallsite("SwipePipToHomeAnimator")
                    .setName("PipContentOverlay")
                    .setColorLayer()
                    .build();
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.show(mContentOverlay);
            t.setLayer(mContentOverlay, Integer.MAX_VALUE);
            int color = Themes.getColorBackground(view.getContext());
            float[] bgColor = new float[] {Color.red(color) / 255f, Color.green(color) / 255f,
                    Color.blue(color) / 255f};
            t.setColor(mContentOverlay, bgColor);
            t.setAlpha(mContentOverlay, 0f);
            t.reparent(mContentOverlay, mLeash);
            t.apply();

            addUpdateListener(valueAnimator -> {
                float alpha = valueAnimator.getAnimatedFraction() < 0.5f
                        ? 0
                        : Utilities.mapToRange(valueAnimator.getAnimatedFraction(), 0.5f, 1f,
                                0f, 1f, Interpolators.FAST_OUT_SLOW_IN);
                t.setAlpha(mContentOverlay, alpha);
                t.apply();
            });
        } else {
            mSourceHintRectInsets = new Rect(sourceRectHint.left - appBounds.left,
                    sourceRectHint.top - appBounds.top,
                    appBounds.right - sourceRectHint.right,
                    appBounds.bottom - sourceRectHint.bottom);
        }

        addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                InteractionJankMonitorWrapper.begin(view, CUJ_APP_CLOSE_TO_PIP);
                super.onAnimationStart(animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                InteractionJankMonitorWrapper.cancel(CUJ_APP_CLOSE_TO_PIP);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                InteractionJankMonitorWrapper.end(CUJ_APP_CLOSE_TO_PIP);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mHasAnimationEnded) return;
                super.onAnimationEnd(animation);
                mHasAnimationEnded = true;
            }
        });
        addUpdateListener(this::onAnimationUpdate);
    }

    /** sets the from rotation if it's different from the target rotation. */
    public void setFromRotation(TaskViewSimulator taskViewSimulator,
            @RecentsOrientedState.SurfaceRotation int fromRotation) {
        if (fromRotation != Surface.ROTATION_90 && fromRotation != Surface.ROTATION_270) {
            Log.wtf(TAG, "Not a supported rotation, rotation=" + fromRotation);
            return;
        }
        mFromRotation = fromRotation;
        final Matrix matrix = new Matrix();
        taskViewSimulator.applyWindowToHomeRotation(matrix);

        // map the destination bounds into window space. mDestinationBounds is always calculated
        // in the final home space and the animation runs in original window space.
        final RectF transformed = new RectF(mDestinationBounds);
        matrix.mapRect(transformed, new RectF(mDestinationBounds));
        transformed.round(mDestinationBoundsTransformed);

        // set the animation destination bounds for RectEvaluator calculation.
        // bounds and insets are calculated as if the transition is from mAppBounds to
        // mDestinationBoundsAnimation, separated from rotate / scale / position.
        mDestinationBoundsAnimation.set(mAppBounds.left, mAppBounds.top,
                mAppBounds.left + mDestinationBounds.width(),
                mAppBounds.top + mDestinationBounds.height());
    }

    private void onAnimationUpdate(ValueAnimator animator) {
        if (mHasAnimationEnded) return;
        final SurfaceControl.Transaction tx =
                PipSurfaceTransactionHelper.newSurfaceControlTransaction();
        onAnimationUpdate(tx, animator.getAnimatedFraction());
        tx.apply();
    }

    private PictureInPictureSurfaceTransaction onAnimationUpdate(SurfaceControl.Transaction tx,
            float fraction) {
        final Rect bounds = mRectEvaluator.evaluate(fraction, mStartBounds,
                mDestinationBoundsAnimation);
        final PictureInPictureSurfaceTransaction op;
        if (mSourceHintRectInsets == null) {
            // no source rect hint been set, directly scale the window down
            op = onAnimationScale(fraction, tx, bounds);
        } else {
            // scale and crop according to the source rect hint
            op = onAnimationScaleAndCrop(fraction, tx, bounds);
        }
        return op;
    }

    /** scale the window directly with no source rect hint being set */
    private PictureInPictureSurfaceTransaction onAnimationScale(
            float fraction, SurfaceControl.Transaction tx, Rect bounds) {
        if (mFromRotation == Surface.ROTATION_90 || mFromRotation == Surface.ROTATION_270) {
            final RotatedPosition rotatedPosition = getRotatedPosition(fraction);
            return mSurfaceTransactionHelper.scale(tx, mLeash, mAppBounds, bounds,
                    rotatedPosition.degree, rotatedPosition.positionX, rotatedPosition.positionY);
        } else {
            return mSurfaceTransactionHelper.scale(tx, mLeash, mAppBounds, bounds);
        }
    }

    /** scale and crop the window with source rect hint */
    private PictureInPictureSurfaceTransaction onAnimationScaleAndCrop(
            float fraction, SurfaceControl.Transaction tx,
            Rect bounds) {
        final Rect insets = mInsetsEvaluator.evaluate(fraction, mSourceInsets,
                mSourceHintRectInsets);
        if (mFromRotation == Surface.ROTATION_90 || mFromRotation == Surface.ROTATION_270) {
            final RotatedPosition rotatedPosition = getRotatedPosition(fraction);
            return mSurfaceTransactionHelper.scaleAndRotate(tx, mLeash, mAppBounds, bounds, insets,
                    rotatedPosition.degree, rotatedPosition.positionX, rotatedPosition.positionY);
        } else {
            return mSurfaceTransactionHelper.scaleAndCrop(tx, mLeash, mAppBounds, bounds, insets);
        }
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

    @Nullable
    public SurfaceControl getContentOverlay() {
        return mContentOverlay;
    }

    /** @return {@link PictureInPictureSurfaceTransaction} for the final leash transaction. */
    public PictureInPictureSurfaceTransaction getFinishTransaction() {
        // get the final leash operations but do not apply to the leash.
        final SurfaceControl.Transaction tx =
                PipSurfaceTransactionHelper.newSurfaceControlTransaction();
        return onAnimationUpdate(tx, FRACTION_END);
    }

    private RotatedPosition getRotatedPosition(float fraction) {
        final float degree, positionX, positionY;
        if (mFromRotation == Surface.ROTATION_90) {
            degree = -90 * fraction;
            positionX = fraction * (mDestinationBoundsTransformed.left - mAppBounds.left)
                    + mAppBounds.left;
            positionY = fraction * (mDestinationBoundsTransformed.bottom - mAppBounds.top)
                    + mAppBounds.top;
        } else {
            degree = 90 * fraction;
            positionX = fraction * (mDestinationBoundsTransformed.right - mAppBounds.left)
                    + mAppBounds.left;
            positionY = fraction * (mDestinationBoundsTransformed.top - mAppBounds.top)
                    + mAppBounds.top;
        }
        return new RotatedPosition(degree, positionX, positionY);
    }

    private static class RotatedPosition {
        private final float degree;
        private final float positionX;
        private final float positionY;

        private RotatedPosition(float degree, float positionX, float positionY) {
            this.degree = degree;
            this.positionX = positionX;
            this.positionY = positionY;
        }
    }
}
