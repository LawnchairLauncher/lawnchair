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

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.gui.BorderSettings;
import android.gui.BoxShadowSettings;
import android.view.Choreographer;
import android.view.SurfaceControl;

import androidx.annotation.Nullable;

import com.android.wm.shell.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.common.BoxShadowHelper;
import com.android.wm.shell.common.pip.PipUtils;
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

    private BoxShadowSettings mBoxShadowSettings;
    private BorderSettings mBorderSettings;

    public PipSurfaceTransactionHelper(Context context) {
        onDensityOrFontScaleChanged(context);
        onThemeChanged(context);
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
    public PipSurfaceTransactionHelper cropAndPosition(@NonNull SurfaceControl.Transaction tx,
            @NonNull SurfaceControl leash, @NonNull Rect destinationBounds) {
        tx.setWindowCrop(leash, destinationBounds.width(), destinationBounds.height())
                .setPosition(leash, destinationBounds.left, destinationBounds.top);
        return this;
    }

    /**
     * Operates {@link SurfaceControl.Transaction#setCrop} on a given transaction and leash.
     *
     * @param tx the transaction to  apply
     * @param leash the leash to crop
     * @param relativeDestinationBounds the bounds to crop, which is relative to the leash
     *                                  coordinate
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper crop(@NonNull SurfaceControl.Transaction tx,
            @NonNull SurfaceControl leash, @NonNull Rect relativeDestinationBounds) {
        tx.setCrop(leash, relativeDestinationBounds);
        return this;
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect sourceBounds, Rect destinationBounds) {
        mTmpDestinationRectF.set(destinationBounds);
        return scale(tx, leash, sourceBounds, mTmpDestinationRectF, 0 /* degrees */,
                true /* shouldOffset */);
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(@NonNull SurfaceControl.Transaction tx,
            @NonNull SurfaceControl leash, @NonNull Rect sourceBounds,
            @NonNull RectF destinationBounds) {
        return scale(tx, leash, sourceBounds, destinationBounds, 0 /* degrees */,
                true /* shouldOffset */);
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash.
     *
     * @param shouldOffset {@code true} to offset the leash to (0, 0)
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(@NonNull SurfaceControl.Transaction tx,
            @NonNull SurfaceControl leash, @NonNull Rect sourceBounds,
            @NonNull Rect destinationBounds, boolean shouldOffset) {
        mTmpDestinationRectF.set(destinationBounds);
        return scale(tx, leash, sourceBounds, mTmpDestinationRectF, 0 /* degrees */, shouldOffset);
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash.
     *
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(@NonNull SurfaceControl.Transaction tx,
            @NonNull SurfaceControl leash, @NonNull Rect sourceBounds,
            @NonNull Rect destinationBounds, float degrees) {
        return scale(tx, leash, sourceBounds, destinationBounds, degrees, true /* shouldOffset */);
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash.
     *
     * @param shouldOffset {@code true} to offset the leash to (0, 0)
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(@NonNull SurfaceControl.Transaction tx,
            @NonNull SurfaceControl leash, @NonNull Rect sourceBounds,
            @NonNull Rect destinationBounds, float degrees, boolean shouldOffset) {
        mTmpDestinationRectF.set(destinationBounds);
        return scale(tx, leash, sourceBounds, mTmpDestinationRectF, degrees, shouldOffset);
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash, along with a rotation.
     *
     * @param shouldOffset {@code true} to offset the leash to (0, 0)
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scale(@NonNull SurfaceControl.Transaction tx,
            @NonNull SurfaceControl leash, @NonNull Rect sourceBounds,
            @NonNull RectF destinationBounds, float degrees, boolean shouldOffset) {
        mTmpSourceRectF.set(sourceBounds);
        // We want the matrix to position the surface relative to the screen coordinates so offset
        // the source to (0, 0) if {@code shouldOffset} is true.
        if (shouldOffset) {
            mTmpSourceRectF.offsetTo(0, 0);
        }
        mTmpDestinationRectF.set(destinationBounds);
        mTmpTransform.setRectToRect(mTmpSourceRectF, mTmpDestinationRectF, Matrix.ScaleToFit.FILL);
        mTmpTransform.postRotate(degrees,
                mTmpDestinationRectF.centerX(), mTmpDestinationRectF.centerY());
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9);
        return this;
    }

    /**
     * Operates the scale (setMatrix) on a given transaction and leash.
     *
     * @param leashOffset the offset of the leash bounds relative to the screen coordinate
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper scaleAndCrop(@NonNull SurfaceControl.Transaction tx,
            @NonNull SurfaceControl leash, @NonNull Rect sourceRectHint, @NonNull Rect sourceBounds,
            @NonNull Rect destinationBounds, @NonNull Rect insets, boolean isInPipDirection,
            float fraction, @NonNull Point leashOffset) {
        mTmpDestinationRect.set(sourceBounds);
        // Similar to {@link #scale}, we want to position the surface relative to the screen
        // coordinates so offset the bounds relative to the leash.
        mTmpDestinationRect.offset(-leashOffset.x, -leashOffset.y);
        mTmpDestinationRect.inset(insets);
        // Scale to the bounds no smaller than the destination and offset such that the top/left
        // of the scaled inset source rect aligns with the top/left of the destination bounds
        final float scale;
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
        } else {
            scale = Math.max((float) destinationBounds.width() / sourceBounds.width(),
                    (float) destinationBounds.height() / sourceBounds.height());
        }
        float left = destinationBounds.left - mTmpDestinationRect.left * scale;
        float top = destinationBounds.top - mTmpDestinationRect.top * scale;
        if (scale == 1) {
            // Work around the 1 pixel off error by rounding the position down at very beginning.
            // We noticed such error from flicker tests, not visually.
            left = leashOffset.x;
            top = leashOffset.y;
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
     *
     * @param relativeEndWindowFrame specified if
     *   {@link android.app.TaskInfo#topActivityMainWindowFrame} is provided. It's only applied for
     *   the animation that {@code isExpanding} PIP to original task.
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper rotateAndScaleWithCrop(
            @NonNull SurfaceControl.Transaction tx, @NonNull SurfaceControl leash,
            @NonNull Rect sourceBounds, @NonNull Rect destinationBounds, @NonNull Rect insets,
            float degrees, float positionX, float positionY, boolean isExpanding,
            boolean clockwise, @Nullable Rect relativeEndWindowFrame) {
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
        if (isExpanding && relativeEndWindowFrame != null) {
            // If relative end window frame is provided, it usually means the top activity chooses
            // a customized layout which may not match parent. In this case, we should crop the
            // task surface with the window frame. Note that we don't need to consider the insets
            // because the main window frame excludes the insets.
            crop.set(relativeEndWindowFrame);
        } else {
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
        if (Flags.enablePipBoxShadows()) {
            if (applyShadowRadius) {
                tx.setBoxShadowSettings(leash, mBoxShadowSettings);
                tx.setBorderSettings(leash, mBorderSettings);
            } else {
                tx.setBoxShadowSettings(leash, new BoxShadowSettings());
                tx.setBorderSettings(leash, new BorderSettings());
            }
        } else {
            tx.setShadowRadius(leash, applyShadowRadius ? mShadowRadius : 0);
        }
        return this;
    }

    /**
     * Called when theme changes.
     *
     * @param context the current context
     */
    public void onThemeChanged(Context context) {
        if (PipUtils.isDarkSystemTheme(context)) {
            mBoxShadowSettings = BoxShadowHelper.getBoxShadowSettings(context,
                    new int[]{R.style.BoxShadowParamsPIPDark1,
                            R.style.BoxShadowParamsPIPDark2});
            mBorderSettings = BoxShadowHelper.getBorderSettings(context,
                    R.style.BorderSettingsPIPDark);
        } else {

            mBoxShadowSettings = BoxShadowHelper.getBoxShadowSettings(context,
                    new int[]{R.style.BoxShadowParamsPIPLight1,
                            R.style.BoxShadowParamsPIPLight2});

            mBorderSettings = BoxShadowHelper.getBorderSettings(context,
                    R.style.BorderSettingsPIPLight);
        }
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
