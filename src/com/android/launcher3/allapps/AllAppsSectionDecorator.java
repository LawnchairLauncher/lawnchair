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
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.allapps.search.SearchSectionInfo;
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
        // Iterate through views in recylerview and draw bounds around views in the same section.
        // Since views in the same section will follow each other, we can skip to a last view in
        // a section to get the bounds of the section without having to iterate on every item.
        int itemCount = parent.getChildCount();
        List<AllAppsGridAdapter.AdapterItem> adapterItems = mAppsView.getApps().getAdapterItems();
        SectionDecorationHandler lastDecorationHandler = null;
        int i = 0;
        while (i < itemCount) {
            View view = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(view);
            AllAppsGridAdapter.AdapterItem adapterItem = adapterItems.get(position);
            if (adapterItem.searchSectionInfo != null) {
                SearchSectionInfo sectionInfo = adapterItem.searchSectionInfo;
                int endIndex = Math.min(i + sectionInfo.getPosEnd() - position, itemCount - 1);
                SectionDecorationHandler decorationHandler = sectionInfo.getDecorationHandler();
                if (decorationHandler != lastDecorationHandler && lastDecorationHandler != null) {
                    drawDecoration(c, lastDecorationHandler, parent);
                }
                lastDecorationHandler = decorationHandler;
                if (decorationHandler != null) {
                    decorationHandler.extendBounds(view);
                }

                if (endIndex > i) {
                    i = endIndex;
                    continue;
                }
            }
            i++;
        }
        if (lastDecorationHandler != null) {
            drawDecoration(c, lastDecorationHandler, parent);
        }
    }

    private void drawDecoration(Canvas c, SectionDecorationHandler decorationHandler,
            RecyclerView parent) {
        if (decorationHandler == null) return;
        if (decorationHandler.mIsFullWidth) {
            decorationHandler.mBounds.left = parent.getPaddingLeft();
            decorationHandler.mBounds.right = parent.getWidth() - parent.getPaddingRight();
        }
        decorationHandler.onDraw(c);
        if (mAppsView.getFloatingHeaderView().getFocusedChild() == null
                && mAppsView.getApps().getFocusedChild() != null) {
            int index = mAppsView.getApps().getFocusedChildIndex();
            if (index >= 0 && index < parent.getChildCount()) {
                decorationHandler.onFocusDraw(c, parent.getChildAt(index));
            }
        }
        decorationHandler.reset();
    }

    /**
     * Handles grouping and drawing of items in the same all apps sections.
     */
    public static class SectionDecorationHandler {
        private static final int FILL_ALPHA = (int) (.3f * 255);
        private static final int FOCUS_ALPHA = (int) (.8f * 255);

        protected RectF mBounds = new RectF();
        private final boolean mIsFullWidth;
        private final float mRadius;

        protected int mFocusColor;
        protected int mFillcolor;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);


        public SectionDecorationHandler(Context context, boolean isFullWidth) {
            mIsFullWidth = isFullWidth;
            int endScrim = Themes.getAttrColor(context, R.attr.allAppsScrimColor);
            mFillcolor = ColorUtils.setAlphaComponent(endScrim, FILL_ALPHA);
            mFocusColor = ColorUtils.setAlphaComponent(endScrim, FOCUS_ALPHA);
            mRadius = Themes.getDialogCornerRadius(context);
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
        public void onDraw(Canvas canvas) {
            mPaint.setColor(mFillcolor);
            canvas.drawRoundRect(mBounds, mRadius, mRadius, mPaint);
        }

        /**
         * Draw the bound of the view to the canvas.
         */
        public void onFocusDraw(Canvas canvas, @Nullable View view) {
            if (view == null) {
                return;
            }
            mPaint.setColor(mFocusColor);
            canvas.drawRoundRect(view.getLeft(), view.getTop(),
                    view.getRight(), view.getBottom(), mRadius, mRadius, mPaint);
        }

        /**
         * Reset view bounds to empty.
         */
        public void reset() {
            mBounds.setEmpty();
        }
    }

}
