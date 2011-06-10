/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * The linear layout used strictly for the widget/wallpaper tab of the customization tray
 */
public class PagedViewExtendedLayout extends LinearLayout implements Page {
    static final String TAG = "PagedViewWidgetLayout";
    float mChildrenAlpha = 1f;
    private boolean mHasFixedWidth;

    public PagedViewExtendedLayout(Context context) {
        this(context, null);
    }

    public PagedViewExtendedLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewExtendedLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setHasFixedWidth(boolean hasFixedWidth) {
        mHasFixedWidth = hasFixedWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mHasFixedWidth) {
            // PagedView currently has issues with different-sized pages since it calculates the
            // offset of each page to scroll to before it updates the actual size of each page
            // (which canchange depending on the content if the contains aren't a fixed size).
            // We work around this by having a fixed size on each widget page).
            int widthSpecSize = getSuggestedMinimumWidth();
            int widthSpecMode = MeasureSpec.EXACTLY;
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(widthSpecSize, widthSpecMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // We eat up the touch events here, since the PagedView (which uses the same swiping
        // touch code as Workspace previously) uses onInterceptTouchEvent() to determine when
        // the user is scrolling between pages.  This means that if the pages themselves don't
        // handle touch events, it gets forwarded up to PagedView itself, and it's own
        // onTouchEvent() handling will prevent further intercept touch events from being called
        // (it's the same view in that case).  This is not ideal, but to prevent more changes,
        // we just always mark the touch event as handled.
        return super.onTouchEvent(event) || true;
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        return true;
    }

    @Override
    public void setAlpha(float alpha) {
        mChildrenAlpha = alpha;
        setChildrenAlpha(alpha);
        super.setAlpha(alpha);
    }

    private void setChildrenAlpha(float alpha) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).setAlpha(alpha);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        child.setAlpha(mChildrenAlpha);
    }


    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        super.addView(child, params);
        child.setAlpha(mChildrenAlpha);
    }

    @Override
    public void addView(View child, int index) {
        super.addView(child, index);
        child.setAlpha(mChildrenAlpha);
    }

    @Override
    public void addView(View child) {
        super.addView(child);
        child.setAlpha(mChildrenAlpha);
    }

    @Override
    public void addView(View child, int width, int height) {
        super.addView(child, width, height);
        child.setAlpha(mChildrenAlpha);
    }

    @Override
    public void removeAllViewsOnPage() {
        removeAllViews();
    }

    @Override
    public void removeViewOnPageAt(int index) {
        removeViewAt(index);
    }

    @Override
    public int getPageChildCount() {
        return getChildCount();
    }

    @Override
    public View getChildOnPageAt(int i) {
        return getChildAt(i);
    }

    @Override
    public int indexOfChildOnPage(View v) {
        return indexOfChild(v);
    }
}
