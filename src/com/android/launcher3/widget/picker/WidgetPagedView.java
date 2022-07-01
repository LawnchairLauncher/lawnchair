/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.android.launcher3.PagedView;
import com.android.launcher3.workprofile.PersonalWorkPagedView;

/**
 * A {@link PagedView} for showing different widgets for the personal and work profile.
 */
public class WidgetPagedView extends PersonalWorkPagedView {

    public WidgetPagedView(Context context) {
        this(context, null);
    }

    public WidgetPagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetPagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPageSpacing(getPaddingLeft());
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        outRect.left += getPaddingLeft();
        outRect.right -= getPaddingRight();
    }
}
