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

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
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
import com.android.launcher3.Utilities;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.pageindicators.PageIndicatorDots;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A {@link PagedView} that displays widget recommendations in categories with dots as paged
 * indicators.
 */
public final class WidgetRecommendationsView extends PagedView<PageIndicatorDots> {
    private @Px float mAvailableHeight = Float.MAX_VALUE;
    private @Px float mAvailableWidth = 0;
    private static final String INITIALLY_DISPLAYED_WIDGETS_STATE_KEY =
            "widgetRecommendationsView:mDisplayedWidgets";
    private static final int MAX_CATEGORIES = 3;
    private TextView mRecommendationPageTitle;
    private final List<String> mCategoryTitles = new ArrayList<>();

    /** Callbacks to run when page changes */
    private final List<Consumer<Integer>> mPageSwitchListeners = new ArrayList<>();

    @Nullable
    private OnLongClickListener mWidgetCellOnLongClickListener;
    @Nullable
    private OnClickListener mWidgetCellOnClickListener;
    private Set<ComponentName> mDisplayedWidgets = Collections.emptySet();

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

    /**
     * Saves the necessary state in the provided bundle. To be called in case of orientation /
     * other config changes.
     */
    public void saveState(Bundle bundle) {
        // Save the widgets that were displayed, so that, on rotation / fold / unfold, we can
        // maintain the "initial" set of widgets that user first saw (if they fit).
        bundle.putParcelableArrayList(INITIALLY_DISPLAYED_WIDGETS_STATE_KEY,
                new ArrayList<>(mDisplayedWidgets));
    }

    /**
     * Restores the state that was saved by the saveState method during orientation / other config
     * changes.
     */
    public void restoreState(Bundle bundle) {
        ArrayList<ComponentName> componentList;
        if (Utilities.ATLEAST_T) {
            componentList = bundle.getParcelableArrayList(
                    INITIALLY_DISPLAYED_WIDGETS_STATE_KEY, ComponentName.class);
        } else {
            componentList = bundle.getParcelableArrayList(
                    INITIALLY_DISPLAYED_WIDGETS_STATE_KEY);
        }

        // Restore the "initial" set of widgets that were displayed, so that, on rotation / fold /
        // unfold, we can maintain the set of widgets that user first saw (if they fit).
        if (componentList != null) {
            mDisplayedWidgets = new HashSet<>(componentList);
        }
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
     * Add a callback to run when the current displayed page changes.
     */
    public void addPageSwitchListener(Consumer<Integer> pageChangeListener) {
        mPageSwitchListeners.add(pageChangeListener);
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
     * @return number of recommendations that could fit in the available space.
     */
    public int setRecommendations(
            List<WidgetItem> recommendedWidgets, DeviceProfile deviceProfile,
            final @Px float availableHeight, final @Px int availableWidth,
            final @Px int cellPadding) {
        this.mAvailableHeight = availableHeight;
        this.mAvailableWidth = availableWidth;
        clear();

        Set<ComponentName> displayedWidgets = maybeDisplayInTable(recommendedWidgets,
                deviceProfile,
                availableWidth, cellPadding);

        if (mDisplayedWidgets.isEmpty()) {
            // Save the widgets shown for the first time user opened the picker; so that, they can
            // be maintained across orientation changes.
            mDisplayedWidgets = displayedWidgets;
        }

        updateTitleAndIndicator(/* requestedPage= */ 0);
        return displayedWidgets.size();
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
     * @param requestedPage   page number to display initially.
     * @return number of recommendations that could fit in the available space.
     */
    public int setRecommendations(
            Map<WidgetRecommendationCategory, List<WidgetItem>> recommendations,
            DeviceProfile deviceProfile, final @Px float availableHeight,
            final @Px int availableWidth, final @Px int cellPadding, final int requestedPage) {
        this.mAvailableHeight = availableHeight;
        this.mAvailableWidth = availableWidth;
        Context context = getContext();
        // For purpose of recommendations section, we don't want paging dots to be halved in two
        // pane display, so, we always provide isTwoPanels = "false".
        mPageIndicator.setPauseScroll(/*pause=*/true, /*isTwoPanels=*/ false);
        clear();

        int displayedCategories = 0;
        Set<ComponentName> allDisplayedWidgets = new HashSet<>();

        // Render top MAX_CATEGORIES in separate tables. Each table becomes a page.
        for (Map.Entry<WidgetRecommendationCategory, List<WidgetItem>> entry :
                new TreeMap<>(recommendations).entrySet()) {
            // If none of the recommendations for the category could fit in the mAvailableHeight, we
            // don't want to add that category; and we look for the next one.
            Set<ComponentName> displayedWidgetsForCategory = maybeDisplayInTable(entry.getValue(),
                    deviceProfile,
                    availableWidth, cellPadding);
            if (!displayedWidgetsForCategory.isEmpty()) {
                mCategoryTitles.add(
                        context.getResources().getString(entry.getKey().categoryTitleRes));
                displayedCategories++;
                allDisplayedWidgets.addAll(displayedWidgetsForCategory);
            }

            if (displayedCategories == MAX_CATEGORIES) {
                break;
            }
        }

        if (mDisplayedWidgets.isEmpty()) {
            // Save the widgets shown for the first time user opened the picker; so that, they can
            // be maintained across orientation changes.
            mDisplayedWidgets = allDisplayedWidgets;
        }

        updateTitleAndIndicator(requestedPage);
        // For purpose of recommendations section, we don't want paging dots to be halved in two
        // pane display, so, we always provide isTwoPanels = "false".
        mPageIndicator.setPauseScroll(/*pause=*/false, /*isTwoPanels=*/false);
        return allDisplayedWidgets.size();
    }

    private void clear() {
        mCategoryTitles.clear();
        removeAllViews();
        setCurrentPage(0);
        mPageIndicator.setActiveMarker(0);
    }

    /** Displays the page title and paging indicator if there are multiple pages. */
    private void updateTitleAndIndicator(int requestedPage) {
        boolean showPaginatedView = getPageCount() > 1;
        int titleAndIndicatorVisibility = showPaginatedView ? View.VISIBLE : View.GONE;
        mRecommendationPageTitle.setVisibility(titleAndIndicatorVisibility);
        mPageIndicator.setVisibility(titleAndIndicatorVisibility);
        if (showPaginatedView) {
            if (requestedPage <= 0 || requestedPage >= getPageCount()) {
                requestedPage = 0;
            }
            setCurrentPage(requestedPage);
            mPageIndicator.setActiveMarker(requestedPage);
            mRecommendationPageTitle.setText(mCategoryTitles.get(requestedPage));
        }
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        if (getPageCount() > 1) {
            // Since the title is outside the paging scroll, we update the title on page switch.
            int nextPage = getNextPage();
            mRecommendationPageTitle.setText(mCategoryTitles.get(nextPage));
            mPageSwitchListeners.forEach(listener -> listener.accept(nextPage));
            super.notifyPageSwitchListener(prevPage);
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
            int desiredHeight = 0;
            int desiredWidth = Math.round(mAvailableWidth);

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                // Measure children based on available height and width.
                measureChild(child,
                        MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.round(mAvailableHeight),
                                MeasureSpec.AT_MOST));
                // Use height of tallest child as we have limited height.
                int childHeight = child.getMeasuredHeight();
                desiredHeight = Math.max(desiredHeight, childHeight);
            }

            int finalHeight = resolveSizeAndState(desiredHeight, heightMeasureSpec, 0);
            int finalWidth = resolveSizeAndState(desiredWidth, widthMeasureSpec, 0);

            setMeasuredDimension(finalWidth, finalHeight);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * Groups the provided recommendations into rows and displays ones that fit in a table.
     * <p>Returns the set of widgets that could fit.</p>
     */
    private Set<ComponentName> maybeDisplayInTable(List<WidgetItem> recommendedWidgets,
            DeviceProfile deviceProfile,
            final @Px int availableWidth, final @Px int cellPadding) {
        List<WidgetItem> filteredRecommendedWidgets = recommendedWidgets;
        // Show only those widgets that were displayed when user first opened the picker.
        if (!mDisplayedWidgets.isEmpty()) {
            filteredRecommendedWidgets = recommendedWidgets.stream().filter(
                    w -> mDisplayedWidgets.contains(w.componentName)).toList();
        }
        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Since we are limited by space, we don't sort recommendations - to show most relevant
        // (if possible).
        List<ArrayList<WidgetItem>> rows = groupWidgetItemsUsingRowPxWithoutReordering(
                filteredRecommendedWidgets,
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

        List<ArrayList<WidgetItem>> displayedItems = recommendationsTable.setRecommendedWidgets(
                rows,
                deviceProfile, mAvailableHeight);

        if (!displayedItems.isEmpty()) {
            addView(recommendationsTable);
        }

        return displayedItems.stream().flatMap(
                        items -> items.stream().map(w -> w.componentName))
                .collect(Collectors.toSet());
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
