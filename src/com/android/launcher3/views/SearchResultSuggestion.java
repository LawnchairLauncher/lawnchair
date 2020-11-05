/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.android.launcher3.R;

/**
 * {@link SearchResultIconRow} with custom drawable resource
 */
public class SearchResultSuggestion extends SearchResultIcon {

    private final Drawable mCustomIcon;

    public SearchResultSuggestion(Context context) {
        this(context, null, 0);
    }

    public SearchResultSuggestion(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultSuggestion(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SearchResultSuggestion, defStyle, 0);
        mCustomIcon = a.getDrawable(R.styleable.SearchResultSuggestion_customIcon);
        a.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ViewGroup.LayoutParams lp = getLayoutParams();
        lp.height = BaseDragLayer.LayoutParams.WRAP_CONTENT;
    }

    @Override
    protected boolean loadIconFromResource() {
        setIcon(mCustomIcon);
        return true;
    }
}
