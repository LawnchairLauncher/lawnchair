/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep.views;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.R;

/**
 * Square view that holds thumbnail and icon and shrinks them appropriately so that both fit nicely
 * within the view. Side length is determined by height.
 */
final class TaskThumbnailIconView extends ViewGroup {
    private final Rect mTmpFrameRect = new Rect();
    private final Rect mTmpChildRect = new Rect();
    private View mThumbnailView;
    private View mIconView;
    private static final float SUBITEM_FRAME_RATIO = .6f;

    public TaskThumbnailIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mThumbnailView = findViewById(R.id.task_thumbnail);
        mIconView = findViewById(R.id.task_icon);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        int width = height;
        setMeasuredDimension(width, height);

        int subItemSize = (int) (SUBITEM_FRAME_RATIO * height);
        if (mThumbnailView.getVisibility() != GONE) {
            boolean isPortrait =
                    (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT);
            int thumbnailHeightSpec =
                    makeMeasureSpec(isPortrait ? height : subItemSize, MeasureSpec.EXACTLY);
            int thumbnailWidthSpec =
                    makeMeasureSpec(isPortrait ? subItemSize : width, MeasureSpec.EXACTLY);
            measureChild(mThumbnailView, thumbnailWidthSpec, thumbnailHeightSpec);
        }
        if (mIconView.getVisibility() != GONE) {
            int iconHeightSpec = makeMeasureSpec(subItemSize, MeasureSpec.EXACTLY);
            int iconWidthSpec = makeMeasureSpec(subItemSize, MeasureSpec.EXACTLY);
            measureChild(mIconView, iconWidthSpec, iconHeightSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mTmpFrameRect.left = getPaddingLeft();
        mTmpFrameRect.right = right - left - getPaddingRight();
        mTmpFrameRect.top = getPaddingTop();
        mTmpFrameRect.bottom = bottom - top - getPaddingBottom();

        // Layout the thumbnail to the top-start corner of the view
        if (mThumbnailView.getVisibility() != GONE) {
            final int width = mThumbnailView.getMeasuredWidth();
            final int height = mThumbnailView.getMeasuredHeight();

            final int thumbnailGravity = Gravity.TOP | Gravity.START;
            Gravity.apply(thumbnailGravity, width, height, mTmpFrameRect, mTmpChildRect);

            mThumbnailView.layout(mTmpChildRect.left, mTmpChildRect.top,
                    mTmpChildRect.right, mTmpChildRect.bottom);
        }

        // Layout the icon to the bottom-end corner of the view
        if (mIconView.getVisibility() != GONE) {
            final int width = mIconView.getMeasuredWidth();
            final int height = mIconView.getMeasuredHeight();

            int thumbnailGravity = Gravity.BOTTOM | Gravity.END;
            Gravity.apply(thumbnailGravity, width, height, mTmpFrameRect, mTmpChildRect);

            mIconView.layout(mTmpChildRect.left, mTmpChildRect.top,
                    mTmpChildRect.right, mTmpChildRect.bottom);
        }
    }
}
