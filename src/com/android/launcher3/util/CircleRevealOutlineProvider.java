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

public class CircleRevealOutlineProvider extends RevealOutlineAnimation {

    private int mCenterX;
    private int mCenterY;
    private float mRadius0;
    private float mRadius1;

    /**
     * @param x reveal center x
     * @param y reveal center y
     * @param r0 initial radius
     * @param r1 final radius
     */
    public CircleRevealOutlineProvider(int x, int y, float r0, float r1) {
        mCenterX = x;
        mCenterY = y;
        mRadius0 = r0;
        mRadius1 = r1;
    }

    @Override
    public boolean shouldRemoveElevationDuringAnimation() {
        return true;
    }

    @Override
    public void setProgress(float progress) {
        mOutlineRadius = (1 - progress) * mRadius0 + progress * mRadius1;

        mOutline.left = (int) (mCenterX - mOutlineRadius);
        mOutline.top = (int) (mCenterY - mOutlineRadius);
        mOutline.right = (int) (mCenterX + mOutlineRadius);
        mOutline.bottom = (int) (mCenterY + mOutlineRadius);
    }
}
