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

package com.android.systemui.shared.pip;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.gui.BorderSettings;
import android.gui.BoxShadowSettings;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.window.PictureInPictureSurfaceTransaction;

import com.android.wm.shell.Flags;
import com.android.wm.shell.common.pip.IPipAnimationListener.PipResources;

 /**
  * TODO(b/171721389): unify this class with
 * {@link com.android.wm.shell.pip.PipSurfaceTransactionHelper}, for instance, there should be one
 * source of truth on enabling/disabling and the actual value of corner radius.
 */
public class PipSurfaceTransactionHelper {
    private final int mCornerRadius;
    private final int mShadowRadius;
    private BoxShadowSettings mBoxShadowSettings;
    private BorderSettings mBorderSettings;
    private final Matrix mTmpTransform = new Matrix();
    private final float[] mTmpFloat9 = new float[9];
    private final RectF mTmpSourceRectF = new RectF();
    private final RectF mTmpDestinationRectF = new RectF();
    private final Rect mTmpDestinationRect = new Rect();

    public PipSurfaceTransactionHelper(PipResources res) {
        mCornerRadius = res.cornerRadius;
        mShadowRadius = res.shadowRadius;
        mBoxShadowSettings = res.boxShadowSettings;
        mBorderSettings = res.borderSettings;
    }

    public PictureInPictureSurfaceTransaction scale(
            SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect sourceBounds, Rect destinationBounds) {
        float positionX = destinationBounds.left;
        float positionY = destinationBounds.top;
        mTmpSourceRectF.set(sourceBounds);
        mTmpDestinationRectF.set(destinationBounds);
        mTmpDestinationRectF.offsetTo(0, 0);
        mTmpTransform.setRectToRect(mTmpSourceRectF, mTmpDestinationRectF, Matrix.ScaleToFit.FILL);
        final float cornerRadius = getScaledCornerRadius(sourceBounds, destinationBounds);
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9)
                .setPosition(leash, positionX, positionY)
                .setCornerRadius(leash, cornerRadius);
        shadow(tx, leash, true);
        return newPipSurfaceTransaction(positionX, positionY,
                mTmpFloat9, 0 /* rotation */, cornerRadius, mShadowRadius, sourceBounds);
    }

    public PictureInPictureSurfaceTransaction scale(
            SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect sourceBounds, Rect destinationBounds,
            float degree, float positionX, float positionY) {
        mTmpSourceRectF.set(sourceBounds);
        mTmpDestinationRectF.set(destinationBounds);
        mTmpDestinationRectF.offsetTo(0, 0);
        mTmpTransform.setRectToRect(mTmpSourceRectF, mTmpDestinationRectF, Matrix.ScaleToFit.FILL);
        mTmpTransform.postRotate(degree, 0, 0);
        final float cornerRadius = getScaledCornerRadius(sourceBounds, destinationBounds);
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9)
                .setPosition(leash, positionX, positionY)
                .setCornerRadius(leash, cornerRadius);
        shadow(tx, leash, true);
        return newPipSurfaceTransaction(positionX, positionY,
                mTmpFloat9, degree, cornerRadius, mShadowRadius, sourceBounds);
    }

    public PictureInPictureSurfaceTransaction scaleAndCrop(
            SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect sourceRectHint, Rect sourceBounds, Rect destinationBounds, Rect insets,
            float progress) {
        mTmpSourceRectF.set(sourceBounds);
        mTmpDestinationRect.set(sourceBounds);
        mTmpDestinationRect.inset(insets);
        // Scale to the bounds no smaller than the destination and offset such that the top/left
        // of the scaled inset source rect aligns with the top/left of the destination bounds
        final float scale, left, top;
        if (sourceRectHint.isEmpty() || sourceRectHint.width() == sourceBounds.width()) {
            scale = Math.max((float) destinationBounds.width() / sourceBounds.width(),
                    (float) destinationBounds.height() / sourceBounds.height());
            // Work around the rounding error by fix the position at very beginning.
            left = scale == 1
                    ? 0 : destinationBounds.left - (insets.left + sourceBounds.left) * scale;
            top = scale == 1
                    ? 0 : destinationBounds.top - (insets.top + sourceBounds.top) * scale;
        } else {
            // scale by sourceRectHint if it's not edge-to-edge
            final float endScale = sourceRectHint.width() <= sourceRectHint.height()
                    ? (float) destinationBounds.width() / sourceRectHint.width()
                    : (float) destinationBounds.height() / sourceRectHint.height();
            final float startScale = sourceRectHint.width() <= sourceRectHint.height()
                    ? (float) destinationBounds.width() / sourceBounds.width()
                    : (float) destinationBounds.height() / sourceBounds.height();
            scale = Math.min((1 - progress) * startScale + progress * endScale, 1.0f);
            left = destinationBounds.left - (insets.left + sourceBounds.left) * scale;
            top = destinationBounds.top - (insets.top + sourceBounds.top) * scale;
        }
        mTmpTransform.setScale(scale, scale);
        final float cornerRadius = getScaledCornerRadius(mTmpDestinationRect, destinationBounds);
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9)
                .setCrop(leash, mTmpDestinationRect)
                .setPosition(leash, left, top)
                .setCornerRadius(leash, cornerRadius);
        shadow(tx, leash, true);
        return newPipSurfaceTransaction(left, top,
                mTmpFloat9, 0 /* rotation */, cornerRadius, mShadowRadius, mTmpDestinationRect);
    }

    public PictureInPictureSurfaceTransaction scaleAndRotate(
            SurfaceControl.Transaction tx, SurfaceControl leash,
            Rect sourceBounds, Rect destinationBounds, Rect insets,
            float degree, float positionX, float positionY) {
        mTmpSourceRectF.set(sourceBounds);
        mTmpDestinationRect.set(sourceBounds);
        mTmpDestinationRect.inset(insets);
        // Scale by the shortest edge and offset such that the top/left of the scaled inset
        // source rect aligns with the top/left of the destination bounds
        final float scale = Math.max((float) destinationBounds.width() / sourceBounds.width(),
                (float) destinationBounds.height() / sourceBounds.height());
        mTmpTransform.setRotate(degree, 0, 0);
        mTmpTransform.postScale(scale, scale);
        final float cornerRadius = getScaledCornerRadius(mTmpDestinationRect, destinationBounds);
        // adjust the positions, take account also the insets
        final float adjustedPositionX, adjustedPositionY;
        if (degree < 0) {
            // Counter-clockwise rotation.
            adjustedPositionX = positionX - insets.top * scale;
            adjustedPositionY = positionY + insets.left * scale;
        } else {
            // Clockwise rotation.
            adjustedPositionX = positionX + insets.top * scale;
            adjustedPositionY = positionY - insets.left * scale;
        }
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9)
                .setCrop(leash, mTmpDestinationRect)
                .setPosition(leash, adjustedPositionX, adjustedPositionY)
                .setCornerRadius(leash, cornerRadius);
        shadow(tx, leash, true);
        return newPipSurfaceTransaction(adjustedPositionX, adjustedPositionY,
                mTmpFloat9, degree, cornerRadius, mShadowRadius, mTmpDestinationRect);
    }

    /** @return the round corner radius scaled by given from and to bounds */
    private float getScaledCornerRadius(Rect fromBounds, Rect toBounds) {
        final float scale = (float) (Math.hypot(fromBounds.width(), fromBounds.height())
                / Math.hypot(toBounds.width(), toBounds.height()));
        return mCornerRadius * scale;
    }

    /**
     * Operates the shadow radius on a given transaction and leash
     * @return same {@link PipSurfaceTransactionHelper} instance for method chaining
     */
    public PipSurfaceTransactionHelper shadow(SurfaceControl.Transaction tx, SurfaceControl leash,
            boolean applyShadowRadius) {
        if (Flags.enablePipBoxShadows()) {
            // Override and disable elevation shadows set by freeform transition.
            //
            // PiP uses box shadows but freeform windows use
            // elevation shadows (i.e. setShadowRadius).
            // To avoid having double shadows applied, disable the shadows set by freeform.
            //
            // TODO(b/367464660): Remove this once freeform box shadows are enabled
            tx.setShadowRadius(leash, 0);

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

    private PictureInPictureSurfaceTransaction newPipSurfaceTransaction(
            float posX, float posY, float[] float9, float rotation,
            float cornerRadius, float shadowRadius, Rect windowCrop) {
        final PictureInPictureSurfaceTransaction.Builder builder =
                new PictureInPictureSurfaceTransaction.Builder()
                        .setPosition(posX, posY)
                        .setTransform(float9, rotation)
                        .setCornerRadius(cornerRadius)
                        .setWindowCrop(windowCrop);
        if (Flags.enablePipBoxShadows()) {
            builder.setShadowRadius(0);
            builder.setBoxShadowSettings(mBoxShadowSettings);
            builder.setBorderSettings(mBorderSettings);
        } else {
            builder.setShadowRadius(shadowRadius);
        }
        return builder.build();
    }

    /** @return {@link SurfaceControl.Transaction} instance with vsync-id */
    public static SurfaceControl.Transaction newSurfaceControlTransaction() {
        final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        tx.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        return tx;
    }
}
