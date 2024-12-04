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

package com.android.wm.shell.pip;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.SurfaceControl;
import android.view.SurfaceSession;
import android.window.TaskSnapshot;

/**
 * Represents the content overlay used during the entering PiP animation.
 */
public abstract class PipContentOverlay {
    // Fixed string used in WMShellFlickerTests
    protected static final String LAYER_NAME = "PipContentOverlay";

    protected SurfaceControl mLeash;

    /** Attaches the internal {@link #mLeash} to the given parent leash. */
    public abstract void attach(SurfaceControl.Transaction tx, SurfaceControl parentLeash);

    /** Detaches the internal {@link #mLeash} from its parent by removing itself. */
    public void detach(SurfaceControl.Transaction tx) {
        if (mLeash != null && mLeash.isValid()) {
            tx.remove(mLeash);
            tx.apply();
        }
    }

    @Nullable
    public SurfaceControl getLeash() {
        return mLeash;
    }

    /**
     * Animates the internal {@link #mLeash} by a given fraction.
     * @param atomicTx {@link SurfaceControl.Transaction} to operate, you should not explicitly
     *                 call apply on this transaction, it should be applied on the caller side.
     * @param currentBounds {@link Rect} of the current animation bounds.
     * @param fraction progress of the animation ranged from 0f to 1f.
     */
    public abstract void onAnimationUpdate(SurfaceControl.Transaction atomicTx,
            Rect currentBounds, float fraction);

    /** A {@link PipContentOverlay} uses solid color. */
    public static final class PipColorOverlay extends PipContentOverlay {
        private static final String TAG = PipColorOverlay.class.getSimpleName();

        private final Context mContext;

        public PipColorOverlay(Context context) {
            mContext = context;
            mLeash = new SurfaceControl.Builder(new SurfaceSession())
                    .setCallsite(TAG)
                    .setName(LAYER_NAME)
                    .setColorLayer()
                    .build();
        }

        @Override
        public void attach(SurfaceControl.Transaction tx, SurfaceControl parentLeash) {
            tx.show(mLeash);
            tx.setLayer(mLeash, Integer.MAX_VALUE);
            tx.setColor(mLeash, getContentOverlayColor(mContext));
            tx.setAlpha(mLeash, 0f);
            tx.reparent(mLeash, parentLeash);
            tx.apply();
        }

        @Override
        public void onAnimationUpdate(SurfaceControl.Transaction atomicTx,
                Rect currentBounds, float fraction) {
            atomicTx.setAlpha(mLeash, fraction < 0.5f ? 0 : (fraction - 0.5f) * 2);
        }

        private float[] getContentOverlayColor(Context context) {
            final TypedArray ta = context.obtainStyledAttributes(new int[] {
                    android.R.attr.colorBackground });
            try {
                int colorAccent = ta.getColor(0, 0);
                return new float[] {
                        Color.red(colorAccent) / 255f,
                        Color.green(colorAccent) / 255f,
                        Color.blue(colorAccent) / 255f };
            } finally {
                ta.recycle();
            }
        }
    }

    /** A {@link PipContentOverlay} uses {@link TaskSnapshot}. */
    public static final class PipSnapshotOverlay extends PipContentOverlay {
        private static final String TAG = PipSnapshotOverlay.class.getSimpleName();

        private final TaskSnapshot mSnapshot;
        private final Rect mSourceRectHint;

        public PipSnapshotOverlay(TaskSnapshot snapshot, Rect sourceRectHint) {
            mSnapshot = snapshot;
            mSourceRectHint = new Rect(sourceRectHint);
            mLeash = new SurfaceControl.Builder(new SurfaceSession())
                    .setCallsite(TAG)
                    .setName(LAYER_NAME)
                    .build();
        }

        @Override
        public void attach(SurfaceControl.Transaction tx, SurfaceControl parentLeash) {
            final float taskSnapshotScaleX = (float) mSnapshot.getTaskSize().x
                    / mSnapshot.getHardwareBuffer().getWidth();
            final float taskSnapshotScaleY = (float) mSnapshot.getTaskSize().y
                    / mSnapshot.getHardwareBuffer().getHeight();
            tx.show(mLeash);
            tx.setLayer(mLeash, Integer.MAX_VALUE);
            tx.setBuffer(mLeash, mSnapshot.getHardwareBuffer());
            // Relocate the content to parentLeash's coordinates.
            tx.setPosition(mLeash, -mSourceRectHint.left, -mSourceRectHint.top);
            tx.setScale(mLeash, taskSnapshotScaleX, taskSnapshotScaleY);
            tx.reparent(mLeash, parentLeash);
            tx.apply();
        }

        @Override
        public void onAnimationUpdate(SurfaceControl.Transaction atomicTx,
                Rect currentBounds, float fraction) {
            // Do nothing. Keep the snapshot till animation ends.
        }
    }

    /** A {@link PipContentOverlay} shows app icon on solid color background. */
    public static final class PipAppIconOverlay extends PipContentOverlay {
        private static final String TAG = PipAppIconOverlay.class.getSimpleName();
        // The maximum size for app icon in pixel.
        private static final int MAX_APP_ICON_SIZE_DP = 72;

        private final Context mContext;
        private final int mAppIconSizePx;
        private final Rect mAppBounds;
        private final int mOverlayHalfSize;
        private final Matrix mTmpTransform = new Matrix();
        private final float[] mTmpFloat9 = new float[9];

        private Bitmap mBitmap;

        public PipAppIconOverlay(Context context, Rect appBounds, Rect destinationBounds,
                Drawable appIcon, int appIconSizePx) {
            mContext = context;
            final int maxAppIconSizePx = (int) TypedValue.applyDimension(COMPLEX_UNIT_DIP,
                    MAX_APP_ICON_SIZE_DP, context.getResources().getDisplayMetrics());
            mAppIconSizePx = Math.min(maxAppIconSizePx, appIconSizePx);

            final int overlaySize = getOverlaySize(appBounds, destinationBounds);
            mOverlayHalfSize = overlaySize >> 1;

            // When the activity is in the secondary split, make sure the scaling center is not
            // offset.
            mAppBounds = new Rect(0, 0, appBounds.width(), appBounds.height());

            mBitmap = Bitmap.createBitmap(overlaySize, overlaySize, Bitmap.Config.ARGB_8888);
            prepareAppIconOverlay(appIcon);
            mLeash = new SurfaceControl.Builder(new SurfaceSession())
                    .setCallsite(TAG)
                    .setName(LAYER_NAME)
                    .build();
        }

        /**
         * Returns the size of the app icon overlay.
         *
         * In order to have the overlay always cover the pip window during the transition,
         * the overlay will be drawn with the max size of the start and end bounds in different
         * rotation.
         */
        public static int getOverlaySize(Rect appBounds, Rect destinationBounds) {
            final int appWidth = appBounds.width();
            final int appHeight = appBounds.height();

            return Math.max(Math.max(appWidth, appHeight),
                    Math.max(destinationBounds.width(), destinationBounds.height())) + 1;
        }

        @Override
        public void attach(SurfaceControl.Transaction tx, SurfaceControl parentLeash) {
            tx.show(mLeash);
            tx.setLayer(mLeash, Integer.MAX_VALUE);
            tx.setBuffer(mLeash, mBitmap.getHardwareBuffer());
            tx.setAlpha(mLeash, 0f);
            tx.reparent(mLeash, parentLeash);
            tx.apply();
        }

        @Override
        public void onAnimationUpdate(SurfaceControl.Transaction atomicTx,
                Rect currentBounds, float fraction) {
            mTmpTransform.reset();
            // In order for the overlay to always cover the pip window, the overlay may have a
            // size larger than the pip window. Make sure that app icon is at the center.
            final int appBoundsCenterX = mAppBounds.centerX();
            final int appBoundsCenterY = mAppBounds.centerY();
            mTmpTransform.setTranslate(
                    appBoundsCenterX - mOverlayHalfSize,
                    appBoundsCenterY - mOverlayHalfSize);
            // Scale back the bitmap with the pivot point at center.
            final float scale = Math.min(
                    (float) mAppBounds.width() / currentBounds.width(),
                    (float) mAppBounds.height() / currentBounds.height());
            mTmpTransform.postScale(scale, scale, appBoundsCenterX, appBoundsCenterY);
            atomicTx.setMatrix(mLeash, mTmpTransform, mTmpFloat9)
                    .setAlpha(mLeash, fraction < 0.5f ? 0 : (fraction - 0.5f) * 2);
        }

        @Override
        public void detach(SurfaceControl.Transaction tx) {
            super.detach(tx);
            if (mBitmap != null && !mBitmap.isRecycled()) {
                mBitmap.recycle();
            }
        }

        private void prepareAppIconOverlay(Drawable appIcon) {
            final Canvas canvas = new Canvas();
            canvas.setBitmap(mBitmap);
            final TypedArray ta = mContext.obtainStyledAttributes(new int[] {
                    android.R.attr.colorBackground });
            try {
                int colorAccent = ta.getColor(0, 0);
                canvas.drawRGB(
                        Color.red(colorAccent),
                        Color.green(colorAccent),
                        Color.blue(colorAccent));
            } finally {
                ta.recycle();
            }
            final Rect appIconBounds = new Rect(
                    mOverlayHalfSize - mAppIconSizePx / 2,
                    mOverlayHalfSize - mAppIconSizePx / 2,
                    mOverlayHalfSize + mAppIconSizePx / 2,
                    mOverlayHalfSize + mAppIconSizePx / 2);
            appIcon.setBounds(appIconBounds);
            appIcon.draw(canvas);
            mBitmap = mBitmap.copy(Bitmap.Config.HARDWARE, false /* mutable */);
        }
    }
}
