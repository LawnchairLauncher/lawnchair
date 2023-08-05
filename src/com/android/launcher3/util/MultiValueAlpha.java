/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;

import android.view.View;

import com.android.launcher3.anim.AlphaUpdateListener;

/**
 * Utility class to handle separating a single value as a factor of multiple values
 */
public class MultiValueAlpha extends MultiPropertyFactory<View> {

    private static final FloatBiFunction ALPHA_AGGREGATOR = (a, b) -> a * b;

    // Whether we should change from INVISIBLE to VISIBLE and vice versa at low alpha values.
    private boolean mUpdateVisibility;

    private final int mHiddenVisibility;

    public MultiValueAlpha(View view, int size) {
        this(view, size, View.INVISIBLE);
    }

    public MultiValueAlpha(View view, int size, int hiddenVisibility) {
        super(view, VIEW_ALPHA, size, ALPHA_AGGREGATOR, 1f);
        this.mHiddenVisibility = hiddenVisibility;
    }

    /** Sets whether we should update between INVISIBLE and VISIBLE based on alpha. */
    public void setUpdateVisibility(boolean updateVisibility) {
        mUpdateVisibility = updateVisibility;
    }

    @Override
    protected void apply(float value) {
        super.apply(value);
        if (mUpdateVisibility) {
            AlphaUpdateListener.updateVisibility(mTarget, mHiddenVisibility);
        }
    }
}
