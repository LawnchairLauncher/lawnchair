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
package com.android.launcher3.workprofile;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.views.ActivityContext;

/**
 * Supports two indicator colors, dedicated for personal and work tabs.
 */
public class PersonalWorkSlidingTabStrip extends LinearLayout implements PageIndicator {
    private final boolean mIsAlignOnIcon;
    private OnActivePageChangedListener mOnActivePageChangedListener;
    private int mLastActivePage = 0;

    public PersonalWorkSlidingTabStrip(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.PersonalWorkSlidingTabStrip);
        mIsAlignOnIcon = typedArray.getBoolean(
                R.styleable.PersonalWorkSlidingTabStrip_alignOnIcon, false);
        typedArray.recycle();
    }

    /**
     * Highlights tab with index pos
     */
    public void updateTabTextColor(int pos) {
        for (int i = 0; i < getChildCount(); i++) {
            Button tab = (Button) getChildAt(i);
            tab.setSelected(i == pos);
        }
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
    }

    @Override
    public void setActiveMarker(int activePage) {
        updateTabTextColor(activePage);
        if (mOnActivePageChangedListener != null && mLastActivePage != activePage) {
            mOnActivePageChangedListener.onActivePageChanged(activePage);
        }
        mLastActivePage = activePage;
    }

    public void setOnActivePageChangedListener(OnActivePageChangedListener listener) {
        mOnActivePageChangedListener = listener;
    }

    @Override
    public void setMarkersCount(int numMarkers) {
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mIsAlignOnIcon) {
            // If any padding is not specified, restrict the width to emulate padding
            int size = MeasureSpec.getSize(widthMeasureSpec);
            size = getTabWidth(getContext(), size);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Returns distance between left and right app icons
     */
    public static int getTabWidth(Context context, int totalWidth) {
        DeviceProfile grid = ActivityContext.lookupContext(context).getDeviceProfile();
        int iconPadding = totalWidth / grid.numShownAllAppsColumns - grid.allAppsIconSizePx;
        return totalWidth - iconPadding;
    }

    /**
     * Interface definition for a callback to be invoked when an active page has been changed.
     */
    public interface OnActivePageChangedListener {
        /** Called when the active page has been changed. */
        void onActivePageChanged(int currentActivePage);
    }
}
