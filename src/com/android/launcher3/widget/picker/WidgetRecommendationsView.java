/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.launcher3.widget.util.WidgetsTableUtils.groupWidgetItemsUsingRowPxWithoutReordering;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.Px;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.pageindicators.PageIndicatorDots;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A {@link PagedView} that displays widget recommendations in categories with dots as paged
 * indicators.
 */
public final class WidgetRecommendationsView extends PagedView<PageIndicatorDots> {
    private @Px float mAvailableHeight = Float.MAX_VALUE;

    private static final int MAX_CATEGORIES = 3;
    private TextView mRecommendationPageTitle;
    private final List<String> mCategoryTitles = new ArrayList<>();

    @Nullable
    private OnLongClickListener mWidgetCellOnLongClickListener;
    @Nullable
    private OnClickListener mWidgetCellOnClickListener;

    public WidgetRecommendationsView(Context context) {
        this(context, /* attrs= */ null);
    }

    public WidgetRecommendationsView(Context context, AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public WidgetRecommendationsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void initParentViews(View parent) {
        super.initParentViews(parent);
        mRecommendationPageTitle = parent.findViewById(R.id.recommendations_page_title);
    }

    /** Sets a {@link android.view.View.OnLongClickListener} for all widget cells in this table. */
    public void setWidgetCellLongClickListener(OnLongClickListener onLongClickListener) {
        mWidgetCellOnLongClickListener = onLongClickListener;
    }

    /** Sets a {@link android.view.View.OnClickListener} for all widget cells in this table. */
    public void setWidgetCellOnClickListener(OnClickListener widgetCellOnClickListener) {
        mWidgetCellOnClickListener = widgetCellOnClickListener;
    }

    /**
     * Displays all the provided recommendations in a single table if they fit.
     *
     * @param recommendedWidgets list of widgets to be displayed in recommendation section.
     * @param deviceProfile      the current {@link DeviceProfile}
     * @param availableHeight    height in px that can be used to display the recommendations;
     *                           recommendations that don't fit in this height won't be shown
     * @param availableWidth     width in px that the recommendations should display in
     * @param cellPadding        padding in px that should be applied to each widget in the
     *                           recommendations
     * @return {@code false} if no recommendations could fit in the available space.
     */
    public boolean setRecommendations(
            List<WidgetItem> recommendedWidgets, DeviceProfile deviceProfile,
            final @Px float availableHeight, final @Px int availableWidth,
            final @Px int cellPadding) {
        this.mAvailableHeight = availableHeight;
        removeAllViews();

        maybeDisplayInTable(recommendedWidgets, deviceProfile, availableWidth, cellPadding);
        updateTitleAndIndicator();
        return getChildCount() > 0;
    }

    /**
     * Displays the recommendations grouped by categories as pages.
     * <p>In case of a single category, no title is displayed for it.</p>
     *
     * @param recommendations a map of widget items per recommendation category
     * @param deviceProfile   the current {@link DeviceProfile}
     * @param availableHeight height in px that can be used to display the recommendations;
     *                        recommendations that don't fit in this height won't be shown
     * @param availableWidth  width in px that the recommendations should display in
     * @param cellPadding     padding in px that should be applied to each widget in the
     *                        recommendations
     * @return {@code false} if no recommendations could fit in the available space.
     */
    public boolean setRecommendations(
            Map<WidgetRecommendationCategory, List<WidgetItem>> recommendations,
            DeviceProfile deviceProfile,
            final @Px float availableHeight, final @Px int availableWidth,
            final @Px int cellPadding) {
        this.mAvailableHeight = availableHeight;
        Context context = getContext();
        mPageIndicator.setPauseScroll(true, deviceProfile.isTwoPanels);
        removeAllViews();

        int displayedCategories = 0;

        // Render top MAX_CATEGORIES in separate tables. Each table becomes a page.
        for (Map.Entry<WidgetRecommendationCategory, List<WidgetItem>> entry :
                new TreeMap<>(recommendations).entrySet()) {
            // If none of the recommendations for the category could fit in the mAvailableHeight, we
            // don't want to add that category; and we look for the next one.
            if (maybeDisplayInTable(entry.getValue(), deviceProfile, availableWidth, cellPadding)) {
                mCategoryTitles.add(
                        context.getResources().getString(entry.getKey().categoryTitleRes));
                displayedCategories++;
            }

            if (displayedCategories == MAX_CATEGORIES) {
                break;
            }
        }

        updateTitleAndIndicator();
        mPageIndicator.setPauseScroll(false, deviceProfile.isTwoPanels);
        return getChildCount() > 0;
    }

    /** Displays the page title and paging indicator if there are multiple pages. */
    private void updateTitleAndIndicator() {
        boolean showPaginatedView = getPageCount() > 1;
        int titleAndIndicatorVisibility = showPaginatedView ? View.VISIBLE : View.GONE;
        mRecommendationPageTitle.setVisibility(titleAndIndicatorVisibility);
        mPageIndicator.setVisibility(titleAndIndicatorVisibility);
        if (showPaginatedView) {
            mPageIndicator.setActiveMarker(0);
            setCurrentPage(0);
            mRecommendationPageTitle.setText(mCategoryTitles.get(0));
        }
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        if (getPageCount() > 1) {
            // Since the title is outside the paging scroll, we update the title on page switch.
            mRecommendationPageTitle.setText(mCategoryTitles.get(getNextPage()));
            super.notifyPageSwitchListener(prevPage);
            requestLayout();
        }
    }

    @Override
    protected boolean canScroll(float absVScroll, float absHScroll) {
        // Allow only horizontal scroll.
        return (absHScroll > absVScroll) && super.canScroll(absVScroll, absHScroll);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mPageIndicator.setScroll(l, mMaxScroll);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean hasMultiplePages = getChildCount() > 0;

        if (hasMultiplePages) {
            int finalWidth = MeasureSpec.getSize(widthMeasureSpec);
            int desiredHeight = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                if (mAvailableHeight == Float.MAX_VALUE) {
                    // When we are not limited by height, use currentPage's height. This is the case
                    // when the paged layout is placed in a scrollable container. We cannot use
                    // height
                    // of tallest child in such case, as it will display a scrollbar even for
                    // smaller
                    // pages that don't have more content.
                    if (i == mCurrentPage) {
                        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
                        desiredHeight = Math.max(parentHeight, child.getMeasuredHeight());
                    }
                } else {
                    // Use height of tallest child when we are limited to a certain height.
                    desiredHeight = Math.max(desiredHeight, child.getMeasuredHeight());
                }
            }

            int finalHeight = resolveSizeAndState(desiredHeight, heightMeasureSpec, 0);
            setMeasuredDimension(finalWidth, finalHeight);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * Groups the provided recommendations into rows and displays them in a table if at least one
     * fits.
     * <p>Returns false if none of the recommendations could fit.</p>
     */
    private boolean maybeDisplayInTable(List<WidgetItem> recommendedWidgets,
            DeviceProfile deviceProfile,
            final @Px int availableWidth, final @Px int cellPadding) {
        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        List<ArrayList<WidgetItem>> rows = groupWidgetItemsUsingRowPxWithoutReordering(
                recommendedWidgets,
                context,
                deviceProfile,
                availableWidth,
                cellPadding);

        WidgetsRecommendationTableLayout recommendationsTable =
                (WidgetsRecommendationTableLayout) inflater.inflate(
                        R.layout.widget_recommendations_table,
                        /* root=*/ this,
                        /* attachToRoot=*/ false);
        recommendationsTable.setWidgetCellOnClickListener(mWidgetCellOnClickListener);
        recommendationsTable.setWidgetCellLongClickListener(mWidgetCellOnLongClickListener);

        boolean displayedAtLeastOne = recommendationsTable.setRecommendedWidgets(rows,
                deviceProfile, mAvailableHeight);
        if (displayedAtLeastOne) {
            addView(recommendationsTable);
        }

        return displayedAtLeastOne;
    }

    /** Returns location of a widget cell for displaying the "touch and hold" education tip. */
    public View getViewForEducationTip() {
        if (getChildCount() > 0) {
            // first page (a table layout) -> first item (a widget cell).
            return ((ViewGroup) getChildAt(0)).getChildAt(0);
        }
        return null;
    }
}
