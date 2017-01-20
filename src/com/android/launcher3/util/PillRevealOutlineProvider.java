/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.util;

import android.graphics.Rect;
import android.view.ViewOutlineProvider;

/**
 * A {@link ViewOutlineProvider} that animates a reveal in a "pill" shape.
 * A pill is simply a round rect, but we assume the width is greater than
 * the height and that the radius is equal to half the height.
 */
public class PillRevealOutlineProvider extends RevealOutlineAnimation {

    private int mCenterX;
    private int mCenterY;
    private float mFinalRadius;
    protected Rect mPillRect;

    /**
     * @param x reveal center x
     * @param y reveal center y
     * @param pillRect round rect that represents the final pill shape
     */
    public PillRevealOutlineProvider(int x, int y, Rect pillRect) {
        this(x, y, pillRect, pillRect.height() / 2f);
    }

    public PillRevealOutlineProvider(int x, int y, Rect pillRect, float radius) {
        mCenterX = x;
        mCenterY = y;
        mPillRect = pillRect;
        mOutlineRadius = mFinalRadius = radius;
    }

    @Override
    public boolean shouldRemoveElevationDuringAnimation() {
        return false;
    }

    @Override
    public void setProgress(float progress) {
        // Assumes width is greater than height.
        int centerToEdge = Math.max(mCenterX, mPillRect.width() - mCenterX);
        int currentSize = (int) (progress * centerToEdge);

        // Bound the outline to the final pill shape defined by mPillRect.
        mOutline.left = Math.max(mPillRect.left, mCenterX - currentSize);
        mOutline.top = Math.max(mPillRect.top, mCenterY - currentSize);
        mOutline.right = Math.min(mPillRect.right, mCenterX + currentSize);
        mOutline.bottom = Math.min(mPillRect.bottom, mCenterY + currentSize);
        mOutlineRadius = Math.min(mFinalRadius, mOutline.height() / 2);
    }
}
