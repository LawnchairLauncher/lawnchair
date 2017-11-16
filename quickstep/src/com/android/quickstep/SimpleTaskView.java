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
package com.android.quickstep;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;

/**
 * A simple view which keeps its size proportional to the display size
 */
public class SimpleTaskView extends View {

    private static final Point sTempPoint = new Point();

    public SimpleTaskView(Context context) {
        super(context);
    }

    public SimpleTaskView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SimpleTaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        getContext().getSystemService(WindowManager.class)
                .getDefaultDisplay().getRealSize(sTempPoint);

        int width = (int) ((float) height * sTempPoint.x / sTempPoint.y);
        setMeasuredDimension(width, height);
    }
}
