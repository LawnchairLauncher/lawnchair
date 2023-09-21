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
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.window.PictureInPictureSurfaceTransaction;

/**
 * TODO(b/171721389): unify this class with
 * {@link com.android.wm.shell.pip.PipSurfaceTransactionHelper}, for instance, there should be one
 * source of truth on enabling/disabling and the actual value of corner radius.
 */
public class PipSurfaceTransactionHelper {
    private final int mCornerRadius;
    private final int mShadowRadius;
    private final Matrix mTmpTransform = new Matrix();
    private final float[] mTmpFloat9 = new float[9];
    private final RectF mTmpSourceRectF = new RectF();
    private final RectF mTmpDestinationRectF = new RectF();
    private final Rect mTmpDestinationRect = new Rect();

    public PipSurfaceTransactionHelper(int cornerRadius, int shadowRadius) {
        mCornerRadius = cornerRadius;
        mShadowRadius = shadowRadius;
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
                .setCornerRadius(leash, cornerRadius)
                .setShadowRadius(leash, mShadowRadius);
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
                .setCornerRadius(leash, cornerRadius)
                .setShadowRadius(leash, mShadowRadius);
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
        final float scale;
        if (sourceRectHint.isEmpty() || sourceRectHint.width() == sourceBounds.width()) {
            scale = Math.max((float) destinationBounds.width() / sourceBounds.width(),
                    (float) destinationBounds.height() / sourceBounds.height());
        } else {
            // scale by sourceRectHint if it's not edge-to-edge
            final float endScale = sourceRectHint.width() <= sourceRectHint.height()
                    ? (float) destinationBounds.width() / sourceRectHint.width()
                    : (float) destinationBounds.height() / sourceRectHint.height();
            final float startScale = sourceRectHint.width() <= sourceRectHint.height()
                    ? (float) destinationBounds.width() / sourceBounds.width()
                    : (float) destinationBounds.height() / sourceBounds.height();
            scale = (1 - progress) * startScale + progress * endScale;
        }
        final float left = destinationBounds.left - (insets.left + sourceBounds.left) * scale;
        final float top = destinationBounds.top - (insets.top + sourceBounds.top) * scale;
        mTmpTransform.setScale(scale, scale);
        final float cornerRadius = getScaledCornerRadius(mTmpDestinationRect, destinationBounds);
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9)
                .setCrop(leash, mTmpDestinationRect)
                .setPosition(leash, left, top)
                .setCornerRadius(leash, cornerRadius)
                .setShadowRadius(leash, mShadowRadius);
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
        final float scale = sourceBounds.width() <= sourceBounds.height()
                ? (float) destinationBounds.width() / sourceBounds.width()
                : (float) destinationBounds.height() / sourceBounds.height();
        mTmpTransform.setRotate(degree, 0, 0);
        mTmpTransform.postScale(scale, scale);
        final float cornerRadius = getScaledCornerRadius(mTmpDestinationRect, destinationBounds);
        // adjust the positions, take account also the insets
        final float adjustedPositionX, adjustedPositionY;
        if (degree < 0) {
            adjustedPositionX = positionX + insets.top * scale;
            adjustedPositionY = positionY + insets.left * scale;
        } else {
            adjustedPositionX = positionX - insets.top * scale;
            adjustedPositionY = positionY - insets.left * scale;
        }
        tx.setMatrix(leash, mTmpTransform, mTmpFloat9)
                .setCrop(leash, mTmpDestinationRect)
                .setPosition(leash, adjustedPositionX, adjustedPositionY)
                .setCornerRadius(leash, cornerRadius)
                .setShadowRadius(leash, mShadowRadius);
        return newPipSurfaceTransaction(adjustedPositionX, adjustedPositionY,
                mTmpFloat9, degree, cornerRadius, mShadowRadius, mTmpDestinationRect);
    }

    /** @return the round corner radius scaled by given from and to bounds */
    private float getScaledCornerRadius(Rect fromBounds, Rect toBounds) {
        final float scale = (float) (Math.hypot(fromBounds.width(), fromBounds.height())
                / Math.hypot(toBounds.width(), toBounds.height()));
        return mCornerRadius * scale;
    }

    private static PictureInPictureSurfaceTransaction newPipSurfaceTransaction(
            float posX, float posY, float[] float9, float rotation,
            float cornerRadius, float shadowRadius, Rect windowCrop) {
        return new PictureInPictureSurfaceTransaction.Builder()
                .setPosition(posX, posY)
                .setTransform(float9, rotation)
                .setCornerRadius(cornerRadius)
                .setShadowRadius(shadowRadius)
                .setWindowCrop(windowCrop)
                .build();
    }

    /** @return {@link SurfaceControl.Transaction} instance with vsync-id */
    public static SurfaceControl.Transaction newSurfaceControlTransaction() {
        final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        tx.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        return tx;
    }
}
