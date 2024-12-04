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

package com.android.wm.shell.pip;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Choreographer;
import android.view.SurfaceControl;

import com.android.wm.shell.R;
import com.android.wm.shell.transition.Transitions;

/**
 * Abstracts the common operations on {@link SurfaceControl.Transaction} for PiP transition.
 */
public class PipSurfaceTransactionHelper {
    /** for {@link #scale(SurfaceControl.Transaction, SurfaceControl, Rect, Rect)} operation */
    private final Matrix mTmpTransform = new Matrix();
    private final float[] mTmpFloat9 = new float[9];
    private final RectF mTmpSourceRectF = new RectF();
    private final RectF mTmpDestinationRectF = new RectF();
    private final Rect mTmpDestinationRect = new Rect();

    private int mCornerRadius;
    private int mShadowRadius;

    public PipSurfaceTransactionHelper(Context context) {
        onDensityOrFontScaleChanged(context);
    }

    /**
     * Called when display size or font size of settings changed
     *
     * @param context the current context
     */
    public void onDensityOrFontScaleChanged(Context context) {
        mCornerRadius = context.getResources().getDimensionPixelSize(R.dimen.pip_corner_radius);
        mShadowRadius = context.getResources().getDimensionPixelSize(R.dimen.pip_shadow_radius);
    }

    /**
     * Operates the alpha on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper alpha(SurfaceControl.Transaction tx, SurfaceControl leash,
            float alpha) {
        tx.setAlpha(leash, alpha);
        return this;
    }

    /**
     * Operates the crop (and position) on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper crop(SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect destinationBounds) {
        tx.setWindowCrop(leash, destinationBounds.width(), destinationBounds.height())
                .setPosition(leash, destinationBounds.left, destinationBounds.top);
        return this;
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect sourceBounds, Rect destinationBounds) {
        mTmpDestinationRectF.set(destinationBounds);
        return scale(tx, leash, sourceBounds, mTmpDestinationRectF, 0 /* degrees */);
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect sourceBounds, RectF destinationBounds) {
        return scale(tx, leash, sourceBounds, destinationBounds, 0 /* degrees */);
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect sourceBounds, Rect destinationBounds, float degrees) {
        mTmpDestinationRectF.set(destinationBounds);
        return scale(tx, leash, sourceBounds, mTmpDestinationRectF, degrees);
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash, along with a rotation.
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect sourceBounds, RectF destinationBounds, float degrees) {
        mTmpSourceRectF.set(sourceBounds);
        // We want the matrix to position the surface relative to the screen coordinates so offset
        // the source to 0,0
        mTmpSourceRectF.offsetTo(0, 0);
        mTmpDestinationRectF.set(destinationBounds);
        mTmpTransform.setRectToRect(mTmpSourceRectF, mTmpDestinationRectF, Matrix.ScaleToFit.FILL);
        mTmpTransform.postRotate(degrees,
                mTmpDestinationRectF.centerX(), mTmpDestinationRectF.centerY());
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9);
        return this;
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scaleAndCrop(SurfaceControl.Transaction tx,
            SurfaceControl leash, Rect sourceRectHint,
            Rect sourceBounds, Rect destinationBounds, Rect insets,
            boolean isInPipDirection, float fraction) {
        mTmpDestinationRect.set(sourceBounds);
        // Similar to {@link #scale}, we want to position the surface relative to the screen
        // coordinates so offset the bounds to 0,0
        mTmpDestinationRect.offsetTo(0, 0);
        mTmpDestinationRect.inset(insets);
        // Scale to the bounds no smaller than the destination and offset such that the top/left
        // of the scaled inset source rect aligns with the top/left of the destination bounds
        final float scale, left, top;
        if (isInPipDirection
                && sourceRectHint != null && sourceRectHint.width() < sourceBounds.width()) {
            // scale by sourceRectHint if it's not edge-to-edge, for entering PiP transition only.
            final float endScale = sourceBounds.width() <= sourceBounds.height()
                    ? (float) destinationBounds.width() / sourceRectHint.width()
                    : (float) destinationBounds.height() / sourceRectHint.height();
            final float startScale = sourceBounds.width() <= sourceBounds.height()
                    ? (float) destinationBounds.width() / sourceBounds.width()
                    : (float) destinationBounds.height() / sourceBounds.height();
            scale = (1 - fraction) * startScale + fraction * endScale;
            left = destinationBounds.left - insets.left * scale;
            top = destinationBounds.top - insets.top * scale;
        } else {
            scale = Math.max((float) destinationBounds.width() / sourceBounds.width(),
                    (float) destinationBounds.height() / sourceBounds.height());
            // Work around the rounding error by fix the position at very beginning.
            left = scale == 1 ? 0 : destinationBounds.left - insets.left * scale;
            top = scale == 1 ? 0 : destinationBounds.top - insets.top * scale;
        }
        mTmpTransform.setScale(scale, scale);
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9)
                .setCrop(leash, mTmpDestinationRect)
                .setPosition(leash, left, top);
        return this;
    }

    /**
     * Operates the rotation according to the given degrees and scale (setMatrix) according to the
     * source bounds and rotated destination bounds. The crop will be the unscaled source bounds.
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper rotateAndScaleWithCrop(SurfaceControl.Transaction tx,
            SurfaceControl leash, Rect sourceBounds, Rect destinationBounds, Rect insets,
            float degrees, float positionX, float positionY, boolean isExpanding,
            boolean clockwise) {
        mTmpDestinationRect.set(sourceBounds);
        mTmpDestinationRect.inset(insets);
        final int srcW = mTmpDestinationRect.width();
        final int srcH = mTmpDestinationRect.height();
        final int destW = destinationBounds.width();
        final int destH = destinationBounds.height();
        // Scale by the short side so there won't be empty area if the aspect ratio of source and
        // destination are different.
        final float scale = srcW <= srcH ? (float) destW / srcW : (float) destH / srcH;
        final Rect crop = mTmpDestinationRect;
        crop.set(0, 0, Transitions.SHELL_TRANSITIONS_ROTATION ? destH
                : destW, Transitions.SHELL_TRANSITIONS_ROTATION ? destW : destH);
        // Inverse scale for crop to fit in screen coordinates.
        crop.scale(1 / scale);
        crop.offset(insets.left, insets.top);
        if (isExpanding) {
            // Expand bounds (shrink insets) in source orientation.
            positionX -= insets.left * scale;
            positionY -= insets.top * scale;
        } else {
            // Shrink bounds (expand insets) in destination orientation.
            if (clockwise) {
                positionX -= insets.top * scale;
                positionY += insets.left * scale;
            } else {
                positionX += insets.top * scale;
                positionY -= insets.left * scale;
            }
        }
        mTmpTransform.setScale(scale, scale);
        mTmpTransform.postRotate(degrees);
        mTmpTransform.postTranslate(positionX, positionY);
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9).setCrop(leash, crop);
        return this;
    }

    /**
     * Resets the scale (setMatrix) on a given transaction and leash if there's any
     *
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper resetScale(SurfaceControl.Transaction tx,
            SurfaceControl leash,
            Rect destinationBounds) {
        tx.setMatrix(leash, Matrix.IDENTITY_MATRIX, mTmpFloat9)
                .setPosition(leash, destinationBounds.left, destinationBounds.top);
        return this;
    }

    /**
     * Operates the round corner radius on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper round(SurfaceControl.Transaction tx, SurfaceControl leash,
            boolean applyCornerRadius) {
        tx.setCornerRadius(leash, applyCornerRadius ? mCornerRadius : 0);
        return this;
    }

    /**
     * Operates the round corner radius on a given transaction and leash, scaled by bounds
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper round(SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect fromBounds, Rect toBounds) {
        final float scale = (float) (Math.hypot(fromBounds.width(), fromBounds.height())
                / Math.hypot(toBounds.width(), toBounds.height()));
        tx.setCornerRadius(leash, mCornerRadius * scale);
        return this;
    }

    /**
     * Operates the shadow radius on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper shadow(SurfaceControl.Transaction tx, SurfaceControl leash,
            boolean applyShadowRadius) {
        tx.setShadowRadius(leash, applyShadowRadius ? mShadowRadius : 0);
        return this;
    }

    public interface SurfaceControlTransactionFactory {
        SurfaceControl.Transaction getTransaction();
    }

    /**
     * Implementation of {@link SurfaceControlTransactionFactory} that returns
     * {@link SurfaceControl.Transaction} with VsyncId being set.
     */
    public static class VsyncSurfaceControlTransactionFactory
            implements SurfaceControlTransactionFactory {
        @Override
        public SurfaceControl.Transaction getTransaction() {
            final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
            tx.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
            return tx;
        }
    }
}
