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
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class HolographicLinearLayout extends LinearLayout {

    private final HolographicViewHelper mHolographicHelper;

    public HolographicLinearLayout(Context context) {
        this(context, null);
    }

    public HolographicLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HolographicLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setWillNotDraw(false);
        mHolographicHelper = new HolographicViewHelper(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // One time call to generate the pressed/focused state -- must be called after
        // measure/layout
        mHolographicHelper.generatePressedFocusedStates(this);
    }
}
