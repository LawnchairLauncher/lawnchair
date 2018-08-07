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

/**
 * A {@link RevealOutlineAnimation} that provides an outline that interpolates between two radii
 * and two {@link Rect}s.
 *
 * An example usage of this provider is an outline that starts out as a circle and ends
 * as a rounded rectangle.
 */
public class RoundedRectRevealOutlineProvider extends RevealOutlineAnimation {
    private final float mStartRadius;
    private final float mEndRadius;

    private final Rect mStartRect;
    private final Rect mEndRect;

    public RoundedRectRevealOutlineProvider(float startRadius, float endRadius, Rect startRect,
            Rect endRect) {
        mStartRadius = startRadius;
        mEndRadius = endRadius;
        mStartRect = startRect;
        mEndRect = endRect;
    }

    @Override
    public boolean shouldRemoveElevationDuringAnimation() {
        return false;
    }

    @Override
    public void setProgress(float progress) {
        mOutlineRadius = (1 - progress) * mStartRadius + progress * mEndRadius;

        mOutline.left = (int) ((1 - progress) * mStartRect.left + progress * mEndRect.left);
        mOutline.top = (int) ((1 - progress) * mStartRect.top + progress * mEndRect.top);
        mOutline.right = (int) ((1 - progress) * mStartRect.right + progress * mEndRect.right);
        mOutline.bottom = (int) ((1 - progress) * mStartRect.bottom + progress * mEndRect.bottom);
    }
}
