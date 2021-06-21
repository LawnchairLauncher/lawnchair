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
package com.android.launcher3.workprofile;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.launcher3.PagedView;

/**
 *  A {@link PagedView} for showing different views for the personal and work profile respectively.
 */
public class PersonalWorkPagedView extends PagedView<PersonalWorkSlidingTabStrip> {

    static final float START_DAMPING_TOUCH_SLOP_ANGLE = (float) Math.PI / 6;
    static final float MAX_SWIPE_ANGLE = (float) Math.PI / 3;
    static final float TOUCH_SLOP_DAMPING_FACTOR = 4;

    public PersonalWorkPagedView(Context context) {
        this(context, null);
    }

    public PersonalWorkPagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PersonalWorkPagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected String getCurrentPageDescription() {
        // Not necessary, tab-bar already has two tabs with their own descriptions.
        return "";
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mPageIndicator.setScroll(l, mMaxScroll);
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev) {
        float absDeltaX = Math.abs(ev.getX() - getDownMotionX());
        float absDeltaY = Math.abs(ev.getY() - getDownMotionY());

        if (Float.compare(absDeltaX, 0f) == 0) return;

        float slope = absDeltaY / absDeltaX;
        float theta = (float) Math.atan(slope);

        if (absDeltaX > mTouchSlop || absDeltaY > mTouchSlop) {
            cancelCurrentPageLongPress();
        }

        if (theta > MAX_SWIPE_ANGLE) {
            return;
        } else if (theta > START_DAMPING_TOUCH_SLOP_ANGLE) {
            theta -= START_DAMPING_TOUCH_SLOP_ANGLE;
            float extraRatio = (float)
                    Math.sqrt((theta / (MAX_SWIPE_ANGLE - START_DAMPING_TOUCH_SLOP_ANGLE)));
            super.determineScrollingStart(ev, 1 + TOUCH_SLOP_DAMPING_FACTOR * extraRatio);
        } else {
            super.determineScrollingStart(ev);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected boolean canScroll(float absVScroll, float absHScroll) {
        return (absHScroll > absVScroll) && super.canScroll(absVScroll, absHScroll);
    }
}
