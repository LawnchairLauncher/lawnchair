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

package com.android.launcher3.keyboard;

import android.graphics.Canvas;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemDecoration;
import android.support.v7.widget.RecyclerView.State;
import android.view.View;
import android.view.View.OnFocusChangeListener;

import com.android.launcher3.keyboard.FocusIndicatorHelper.SimpleFocusIndicatorHelper;

/**
 * {@link ItemDecoration} for drawing and animating focused view background.
 */
public class FocusedItemDecorator extends ItemDecoration {

    private FocusIndicatorHelper mHelper;

    public FocusedItemDecorator(View container) {
        mHelper = new SimpleFocusIndicatorHelper(container);
    }

    public OnFocusChangeListener getFocusListener() {
        return mHelper;
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, State state) {
        mHelper.draw(c);
    }
}
