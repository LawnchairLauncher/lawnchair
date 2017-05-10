/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.anim;

import android.graphics.Rect;

import com.android.launcher3.util.PillRevealOutlineProvider;

/**
 * Extension of {@link PillRevealOutlineProvider} which only changes the height of the pill.
 * For now, we assume the height is added/removed from the bottom.
 */
public class PillHeightRevealOutlineProvider extends PillRevealOutlineProvider {

    private final int mNewHeight;

    public PillHeightRevealOutlineProvider(Rect pillRect, float radius, int newHeight) {
        super(0, 0, pillRect, radius);
        mOutline.set(pillRect);
        mNewHeight = newHeight;
    }

    @Override
    public void setProgress(float progress) {
        mOutline.top = 0;
        int heightDifference = mPillRect.height() - mNewHeight;
        mOutline.bottom = (int) (mPillRect.bottom - heightDifference * (1 - progress));
    }
}
