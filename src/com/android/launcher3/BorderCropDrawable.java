/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class BorderCropDrawable extends Drawable {

    private final Drawable mChild;
    private final Rect mBoundsShift;
    private final Rect mPadding;

    BorderCropDrawable(Drawable child, boolean cropLeft,
            boolean cropTop, boolean cropRight, boolean cropBottom) {
        mChild = child;

        mBoundsShift = new Rect();
        mPadding = new Rect();
        mChild.getPadding(mPadding);

        if (cropLeft) {
            mBoundsShift.left = -mPadding.left;
            mPadding.left = 0;
        }
        if (cropTop) {
            mBoundsShift.top = -mPadding.top;
            mPadding.top = 0;
        }
        if (cropRight) {
            mBoundsShift.right = mPadding.right;
            mPadding.right = 0;
        }
        if (cropBottom) {
            mBoundsShift.bottom = mPadding.bottom;
            mPadding.bottom = 0;
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mChild.setBounds(
                bounds.left + mBoundsShift.left,
                bounds.top + mBoundsShift.top,
                bounds.right + mBoundsShift.right,
                bounds.bottom + mBoundsShift.bottom);
    }

    @Override
    public boolean getPadding(Rect padding) {
        padding.set(mPadding);
        return (padding.left | padding.top | padding.right | padding.bottom) != 0;
    }

    @Override
    public void draw(Canvas canvas) {
        mChild.draw(canvas);
    }

    @Override
    public int getOpacity() {
        return mChild.getOpacity();
    }

    @Override
    public void setAlpha(int alpha) {
        mChild.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mChild.setColorFilter(cf);
    }
}
