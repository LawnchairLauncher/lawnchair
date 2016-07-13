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
package com.android.launcher3.pageindicators;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import com.android.launcher3.Launcher;

/**
 * Simply draws the caret drawable in the center. Used for the landscape layout.
 */
public class PageIndicatorCaretLandscape extends PageIndicator {
    // all apps pull up handle drawable.

    public PageIndicatorCaretLandscape(Context context) {
        this(context, null);
    }

    public PageIndicatorCaretLandscape(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicatorCaretLandscape(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setCaretDrawable(new CaretDrawable(context));
        Launcher l = (Launcher) context;
        setOnTouchListener(l.getHapticFeedbackTouchListener());
        setOnClickListener(l);
        setOnFocusChangeListener(l.mFocusHandler);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int size = bottom - top;
        int l = (right - left) / 2 - size / 2;
        getCaretDrawable().setBounds(l, 0, l + size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getCaretDrawable().draw(canvas);
    }
}
