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

import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.android.launcher3.R;

public class FloatingHeaderHandler extends RecyclerView.OnScrollListener {

    private final int mMaxTranslation;
    private final View mHeaderView;
    private final PredictionRowView mContentView;
    private final RecyclerView mMainRV;
    private final RecyclerView mWorkRV;
    private final Rect mClip = new Rect(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private boolean mHeaderHidden;
    private int mSnappedScrolledY;
    private int mTranslationY;
    private int mMainScrolledY;
    private int mWorkScrolledY;
    private boolean mMainRVActive;

    public FloatingHeaderHandler(@NonNull View header, @NonNull RecyclerView personalRV,
            @Nullable RecyclerView workRV, int contentHeight) {
        mHeaderView = header;
        mContentView = mHeaderView.findViewById(R.id.header_content);
        mContentView.getLayoutParams().height = contentHeight;
        mMaxTranslation = contentHeight;
        mMainRV = personalRV;
        mMainRV.addOnScrollListener(this);
        mWorkRV = workRV;
        if (workRV != null) {
            workRV.addOnScrollListener(this);
        }
        setMainActive(true);
    }

    public void setMainActive(boolean active) {
        mMainRVActive = active;
        mSnappedScrolledY = getCurrentScroll() - mMaxTranslation;
        setExpanded(true);
    }

    public View getHeaderView() {
        return mHeaderView;
    }

    public PredictionRowView getContentView() {
        return mContentView;
    }

    @Override
    public void onScrolled(RecyclerView rv, int dx, int dy) {
        boolean isMainRV = rv == mMainRV;
        if (isMainRV != mMainRVActive) {
            return;
        }

        int current = isMainRV
                ? (mMainScrolledY -= dy)
                : (mWorkScrolledY -= dy);

        if (dy == 0) {
            setExpanded(true);
        } else {
            moved(current);
            apply();
        }
    }

    private void moved(final int currentScrollY) {
        if (mHeaderHidden) {
            if (currentScrollY <= mSnappedScrolledY) {
                mSnappedScrolledY = currentScrollY;
            } else {
                mHeaderHidden = false;
            }
            mTranslationY = currentScrollY;
        } else {
            mTranslationY = currentScrollY - mSnappedScrolledY - mMaxTranslation;

            // update state vars
            if (mTranslationY >= 0) { // expanded: must not move down further
                mTranslationY = 0;
                mSnappedScrolledY = currentScrollY - mMaxTranslation;
            } else if (mTranslationY <= -mMaxTranslation) { // hide or stay hidden
                mHeaderHidden = true;
                mSnappedScrolledY = currentScrollY;
            }
        }
    }

    private void apply() {
        mTranslationY = Math.max(mTranslationY, -mMaxTranslation);
        mHeaderView.setTranslationY(mTranslationY);
        mClip.top = mMaxTranslation + mTranslationY;
        mMainRV.setClipBounds(mClip);
        if (mWorkRV != null) {
            mWorkRV.setClipBounds(mClip);
        }
    }

    private void setExpanded(boolean expand) {
        int translateTo = expand ? 0 : -mMaxTranslation;
        mTranslationY = translateTo;
        apply();

        mHeaderHidden = !expand;
        mSnappedScrolledY = expand ? getCurrentScroll() - mMaxTranslation : getCurrentScroll();
    }

    public boolean isExpanded() {
        return !mHeaderHidden;
    }

    private int getCurrentScroll() {
        return mMainRVActive ? mMainScrolledY : mWorkScrolledY;
    }

}
