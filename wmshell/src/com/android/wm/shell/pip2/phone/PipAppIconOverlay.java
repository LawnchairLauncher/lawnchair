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

package com.android.wm.shell.pip2.phone;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.util.TypedValue;
import android.view.SurfaceControl;

import com.android.wm.shell.shared.pip.PipContentOverlay;

/** A {@link PipContentOverlay} shows app icon on solid color background. */
public final class PipAppIconOverlay extends PipContentOverlay {
    private static final String TAG = PipAppIconOverlay.class.getSimpleName();
    // The maximum size for app icon in pixel.
    private static final int MAX_APP_ICON_SIZE_DP = 72;

    private final Context mContext;
    private final int mAppIconSizePx;
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

        mBitmap = Bitmap.createBitmap(overlaySize, overlaySize, Bitmap.Config.ARGB_8888);
        prepareAppIconOverlay(appIcon);
        mLeash = new SurfaceControl.Builder()
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
        final HardwareBuffer buffer = mBitmap.getHardwareBuffer();
        tx.show(mLeash);
        tx.setLayer(mLeash, Integer.MAX_VALUE);
        tx.setBuffer(mLeash, buffer);
        tx.setAlpha(mLeash, 0f);
        tx.reparent(mLeash, parentLeash);
        tx.apply();
        // Cleanup the bitmap and buffer after setting up the leash
        mBitmap.recycle();
        mBitmap = null;
        buffer.close();
    }

    @Override
    public void onAnimationUpdate(SurfaceControl.Transaction atomicTx,
            float scale, float fraction, Rect endBounds) {
        mTmpTransform.reset();
        // Scale back the bitmap with the pivot at parent origin
        mTmpTransform.setScale(scale, scale);
        // We are negative-cropping away from the final bounds crop in config-at-end enter PiP;
        // this means that the overlay shift depends on the final bounds.
        // Note: translation is also dependent on the scaling of the parent.
        mTmpTransform.postTranslate(endBounds.width() / 2f - mOverlayHalfSize * scale,
                endBounds.height() / 2f - mOverlayHalfSize * scale);
        atomicTx.setMatrix(mLeash, mTmpTransform, mTmpFloat9)
                .setAlpha(mLeash, fraction < 0.5f ? 0 : (fraction - 0.5f) * 2);
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
        Bitmap oldBitmap = mBitmap;
        mBitmap = mBitmap.copy(Bitmap.Config.HARDWARE, false /* mutable */);
        oldBitmap.recycle();
    }
}
