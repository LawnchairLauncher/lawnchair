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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter.AppsGridLayoutManager;
import com.android.launcher3.allapps.search.SectionDecorationInfo;
import com.android.launcher3.util.Themes;

import java.util.List;

/**
 * ItemDecoration class that groups items in {@link AllAppsRecyclerView}
 */
public class AllAppsSectionDecorator extends RecyclerView.ItemDecoration {

    private final AllAppsContainerView mAppsView;

    AllAppsSectionDecorator(AllAppsContainerView appsContainerView) {
        mAppsView = appsContainerView;
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        List<AllAppsGridAdapter.AdapterItem> adapterItems = mAppsView.getApps().getAdapterItems();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(view);
            AllAppsGridAdapter.AdapterItem adapterItem = adapterItems.get(position);
            if (adapterItem.sectionDecorationInfo != null) {
                SectionDecorationInfo sectionInfo = adapterItem.sectionDecorationInfo;
                SectionDecorationHandler decorationHandler = sectionInfo.getDecorationHandler();
                if (decorationHandler != null) {
                    decorationHandler.extendBounds(view);
                    if (sectionInfo.isFocusedView()) {
                        decorationHandler.onFocusDraw(c, view);
                    } else {
                        decorationHandler.onGroupDraw(c);
                    }
                }
            }
        }
    }

    // Fallback logic in case non of the SearchTarget is labeled as focused item.
    private void drawDecoration(@NonNull Canvas c,
            @NonNull SectionDecorationHandler decorationHandler,
            @NonNull RecyclerView parent) {
        if (decorationHandler.mIsFullWidth) {
            decorationHandler.mBounds.left = parent.getPaddingLeft();
            decorationHandler.mBounds.right = parent.getWidth() - parent.getPaddingRight();
        }
        if (mAppsView.getFloatingHeaderView().getFocusedChild() == null
                && mAppsView.getApps().getFocusedChild() != null) {
            int index = mAppsView.getApps().getFocusedChildIndex();
            AppsGridLayoutManager layoutManager = (AppsGridLayoutManager)
                    mAppsView.getActiveRecyclerView().getLayoutManager();
            if (layoutManager.findFirstVisibleItemPosition() <= index
                    && index < parent.getChildCount()) {
                RecyclerView.ViewHolder vh = parent.findViewHolderForAdapterPosition(index);
                if (vh != null) decorationHandler.onFocusDraw(c, vh.itemView);
            }
        }
        decorationHandler.reset();
    }

    /**
     * Handles grouping and drawing of items in the same all apps sections.
     */
    public static class SectionDecorationHandler {
        protected RectF mBounds = new RectF();
        private final boolean mIsFullWidth;
        private final float mRadius;

        protected final int mFocusColor; // main focused item color
        protected final int mFillcolor; // grouping color

        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean mIsTopRound;
        private final boolean mIsBottomRound;
        private float [] mCorners;
        private float mFillSpacing;

        public SectionDecorationHandler(Context context, boolean isFullWidth, int fillAlpha,
                boolean isTopRound, boolean isBottomRound) {

            mIsFullWidth = isFullWidth;
            int endScrim = Themes.getColorBackground(context);
            mFillcolor = ColorUtils.setAlphaComponent(endScrim, fillAlpha);
            mFocusColor = endScrim;

            mIsTopRound = isTopRound;
            mIsBottomRound = isBottomRound;

            mRadius = context.getResources().getDimensionPixelSize(
                    R.dimen.search_decoration_corner_radius);
            mFillSpacing = context.getResources().getDimensionPixelSize(
                    R.dimen.search_decoration_padding);
            mCorners = new float[]{
                    mIsTopRound ? mRadius : 0, mIsTopRound ? mRadius : 0, // Top left radius in px
                    mIsTopRound ? mRadius : 0, mIsTopRound ? mRadius : 0, // Top right radius in px
                    mIsBottomRound ? mRadius : 0, mIsBottomRound ? mRadius : 0, // Bottom right
                    mIsBottomRound ? mRadius : 0, mIsBottomRound ? mRadius : 0  // Bottom left
            };

        }

        /**
         * Extends current bounds to include the view.
         */
        public void extendBounds(View view) {
            if (mBounds.isEmpty()) {
                mBounds.set(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
            } else {
                mBounds.set(
                        Math.min(mBounds.left, view.getLeft()),
                        Math.min(mBounds.top, view.getTop()),
                        Math.max(mBounds.right, view.getRight()),
                        Math.max(mBounds.bottom, view.getBottom())
                );
            }
        }

        /**
         * Draw bounds onto canvas.
         */
        public void onGroupDraw(Canvas canvas) {
            mPaint.setColor(mFillcolor);
            onDraw(canvas);
        }

        /**
         * Draw the bound of the view to the canvas.
         */
        public void onFocusDraw(Canvas canvas, @Nullable View view) {
            if (view == null) {
                return;
            }
            mPaint.setColor(mFocusColor);
            mBounds.set(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
            onDraw(canvas);
        }


        private void onDraw(Canvas canvas) {
            final Path path = new Path();
            RectF finalBounds = new RectF(mBounds.left + mFillSpacing,
                    mBounds.top + mFillSpacing,
                    mBounds.right - mFillSpacing,
                    mBounds.bottom - mFillSpacing);
            path.addRoundRect(finalBounds, mCorners, Path.Direction.CW);
            canvas.drawPath(path, mPaint);
        }

        /**
         * Reset view bounds to empty.
         */
        public void reset() {
            mBounds.setEmpty();
        }
    }
}
