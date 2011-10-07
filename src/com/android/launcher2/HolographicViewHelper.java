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

package com.android.launcher2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

public class HolographicViewHelper {

    private final HolographicOutlineHelper mOutlineHelper = new HolographicOutlineHelper();
    private final Canvas mTempCanvas = new Canvas();

    private boolean mStatesUpdated;
    private int mHighlightColor;

    public HolographicViewHelper(Context context) {
        Resources res = context.getResources();
        mHighlightColor = res.getColor(android.R.color.holo_blue_light);
    }

    /**
     * Generate the pressed/focused states if necessary.
     */
    void generatePressedFocusedStates(View v) {
        if (!mStatesUpdated) {
            mStatesUpdated = true;
            Bitmap outline = createGlowingOutline(v, mTempCanvas, mHighlightColor, mHighlightColor);
            FastBitmapDrawable d = new FastBitmapDrawable(outline);

            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_pressed}, d);
            states.addState(new int[] {android.R.attr.state_focused}, d);
            v.setBackgroundDrawable(states);
        }
    }

    /**
     * Returns a new bitmap to be used as the object outline, e.g. to visualize the drop location.
     * Responsibility for the bitmap is transferred to the caller.
     */
    private Bitmap createGlowingOutline(View v, Canvas canvas, int outlineColor, int glowColor) {
        final int padding = HolographicOutlineHelper.MAX_OUTER_BLUR_RADIUS;
        final Bitmap b = Bitmap.createBitmap(
                v.getWidth() + padding, v.getHeight() + padding, Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        canvas.save();
            v.draw(canvas);
        canvas.restore();
        mOutlineHelper.applyOuterBlur(b, canvas, outlineColor);
        canvas.setBitmap(null);

        return b;
    }
}
