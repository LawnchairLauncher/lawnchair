/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/**
 * A {@link LinearLayout} container for holding search and widgets recommendation.
 *
 * <p>This class intercepts touch events and dispatch them to the right view.
 */
public class SearchAndRecommendationsView extends LinearLayout {
    private SearchAndRecommendationsScrollController mController;

    public SearchAndRecommendationsView(Context context) {
        this(context, /* attrs= */ null);
    }

    public SearchAndRecommendationsView(Context context, AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public SearchAndRecommendationsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, /* defStyleRes= */ 0);
    }

    public SearchAndRecommendationsView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setSearchAndRecommendationScrollController(
            SearchAndRecommendationsScrollController controller) {
        mController = controller;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mController.onInterceptTouchEvent(event) || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mController.onTouchEvent(event) || super.onTouchEvent(event);
    }
}
