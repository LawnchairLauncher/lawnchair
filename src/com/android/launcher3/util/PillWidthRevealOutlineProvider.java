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

/**
 * Extension of {@link PillRevealOutlineProvider} which only changes the width of the pill.
 */
public class PillWidthRevealOutlineProvider extends PillRevealOutlineProvider {

    private final int mStartLeft;
    private final int mStartRight;

    public PillWidthRevealOutlineProvider(Rect pillRect, int left, int right) {
        super(0, 0, pillRect);
        mOutline.set(pillRect);
        mStartLeft = left;
        mStartRight = right;
    }

    @Override
    public void setProgress(float progress) {
        mOutline.left = (int) (progress * mPillRect.left + (1 - progress) * mStartLeft);
        mOutline.right = (int) (progress * mPillRect.right + (1 - progress) * mStartRight);
    }
}
