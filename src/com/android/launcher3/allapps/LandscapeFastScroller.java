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

package com.android.launcher3.allapps;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.launcher3.views.RecyclerViewFastScroller;

/**
 * Extension of {@link RecyclerViewFastScroller} to be used in landscape layout.
 */
public class LandscapeFastScroller extends RecyclerViewFastScroller {

    public LandscapeFastScroller(Context context) {
        super(context);
    }

    public LandscapeFastScroller(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LandscapeFastScroller(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean handleTouchEvent(MotionEvent ev) {
        // We handle our own touch event, no need to handle recycler view touch delegates.
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        event.offsetLocation(0, -mRv.getPaddingTop());
        if (super.handleTouchEvent(event)) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        event.offsetLocation(0, mRv.getPaddingTop());
        return true;
    }

    @Override
    public boolean shouldBlockIntercept(int x, int y) {
        // If the user touched the scroll bar area, block swipe
        return x >= 0 && x < getWidth() && y >= 0 && y < getHeight();
    }
}
