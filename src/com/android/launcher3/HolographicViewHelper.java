/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.ImageView;

public class HolographicViewHelper {

    private final Canvas mTempCanvas = new Canvas();

    private boolean mStatesUpdated;
    private int mHighlightColor, mHotwordColor;

    public HolographicViewHelper(Context context) {
        Resources res = context.getResources();
        mHighlightColor = res.getColor(android.R.color.holo_blue_light);
        mHotwordColor = res.getColor(android.R.color.holo_green_light);
    }

    /**
     * Generate the pressed/focused states if necessary.
     */
    void generatePressedFocusedStates(ImageView v) {
        if (!mStatesUpdated && v != null) {
            mStatesUpdated = true;
            Bitmap original = createOriginalImage(v, mTempCanvas);
            Bitmap outline = createImageWithOverlay(v, mTempCanvas, mHighlightColor);
            Bitmap hotword = createImageWithOverlay(v, mTempCanvas, mHotwordColor);
            FastBitmapDrawable originalD = new FastBitmapDrawable(original);
            FastBitmapDrawable outlineD = new FastBitmapDrawable(outline);
            FastBitmapDrawable hotwordD = new FastBitmapDrawable(hotword);

            StateListDrawable states = new StateListDrawable();

            states.addState(new int[] {android.R.attr.state_pressed}, outlineD);
            states.addState(new int[] {android.R.attr.state_focused}, outlineD);
            states.addState(new int[] {R.attr.stateHotwordOn}, hotwordD);
            states.addState(new int[] {}, originalD);
            v.setImageDrawable(states);
        }
    }

    /**
     * Invalidates the pressed/focused states.
     */
    void invalidatePressedFocusedStates(ImageView v) {
        mStatesUpdated = false;
        if (v != null) {
            v.invalidate();
        }
    }

    /**
     * Creates a copy of the original image.
     */
    private Bitmap createOriginalImage(ImageView v, Canvas canvas) {
        final Drawable d = v.getDrawable();
        final Bitmap b = Bitmap.createBitmap(
                d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        canvas.save();
        d.draw(canvas);
        canvas.restore();
        canvas.setBitmap(null);

        return b;
    }

    /**
     * Creates a new press state image which is the old image with a blue overlay.
     * Responsibility for the bitmap is transferred to the caller.
     */
    private Bitmap createImageWithOverlay(ImageView v, Canvas canvas, int color) {
        final Drawable d = v.getDrawable();
        final Bitmap b = Bitmap.createBitmap(
                d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        canvas.save();
        d.draw(canvas);
        canvas.restore();
        canvas.drawColor(color, PorterDuff.Mode.SRC_IN);
        canvas.setBitmap(null);

        return b;
    }
}
