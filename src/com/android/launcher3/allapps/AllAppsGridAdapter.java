/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.net.Uri;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

import java.util.HashMap;
import java.util.List;


/**
 * The grid view adapter of all the apps.
 */
public class AllAppsGridAdapter extends RecyclerView.Adapter<AllAppsGridAdapter.ViewHolder> {

    public static final String TAG = "AppsGridAdapter";
    private static final boolean DEBUG = false;

    // A section break in the grid
    public static final int SECTION_BREAK_VIEW_TYPE = 0;
    // A normal icon
    public static final int ICON_VIEW_TYPE = 1;
    // A prediction icon
    public static final int PREDICTION_ICON_VIEW_TYPE = 2;
    // The message shown when there are no filtered results
    public static final int EMPTY_SEARCH_VIEW_TYPE = 3;
    // A divider that separates the apps list and the search market button
    public static final int SEARCH_MARKET_DIVIDER_VIEW_TYPE = 4;
    // The message to continue to a market search when there are no filtered results
    public static final int SEARCH_MARKET_VIEW_TYPE = 5;

    public interface BindViewCallback {
        public void onBindView(ViewHolder holder);
    }

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View mContent;

        public ViewHolder(View v) {
            super(v);
            mContent = v;
        }
    }

    /**
     * A subclass of GridLayoutManager that overrides accessibility values during app search.
     */
    public class AppsGridLayoutManager extends GridLayoutManager {

        public AppsGridLayoutManager(Context context) {
            super(context, 1, GridLayoutManager.VERTICAL, false);
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);

            // Ensure that we only report the number apps for accessibility not including other
            // adapter views
            final AccessibilityRecordCompat record = AccessibilityEventCompat
                    .asRecord(event);

            // count the number of SECTION_BREAK_VIEW_TYPE that is wrongfully
            // initialized as a node (also a row) for talk back.
            int numEmptyNode = getEmptyRowForAccessibility(-1 /* no view type */);
            record.setFromIndex(event.getFromIndex() - numEmptyNode);
            record.setToIndex(event.getToIndex() - numEmptyNode);
            record.setItemCount(mApps.getNumFilteredApps());
        }

        @Override
        public void onInitializeAccessibilityNodeInfoForItem(Recycler recycler,
                State state, View host, AccessibilityNodeInfoCompat info) {

            int viewType = getItemViewType(host);
            // Only initialize on node that is meaningful. Subtract empty row count.
            if (viewType == ICON_VIEW_TYPE || viewType == PREDICTION_ICON_VIEW_TYPE) {
                super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info);
                CollectionItemInfoCompat itemInfo = info.getCollectionItemInfo();
                if (itemInfo != null) {
                    final CollectionItemInfoCompat dstItemInfo = CollectionItemInfoCompat.obtain(
                            itemInfo.getRowIndex() - getEmptyRowForAccessibility(viewType),
                            itemInfo.getRowSpan(),
                            itemInfo.getColumnIndex(),
                            itemInfo.getColumnSpan(),
                            itemInfo.isHeading(),
                            itemInfo.isSelected());
                    info.setCollectionItemInfo(dstItemInfo);
                }
            }
        }

        @Override
        public int getRowCountForAccessibility(RecyclerView.Recycler recycler,
                RecyclerView.State state) {
            return super.getRowCountForAccessibility(recycler, state)
                    - getEmptyRowForAccessibility(-1 /* no view type */);
        }

        /**
         * Returns the total number of SECTION_BREAK_VIEW_TYPE that is wrongfully
         * initialized as a node (also a row) for talk back.
         */
        private int getEmptyRowForAccessibility(int viewType) {
            int numEmptyNode = 0;
            if (mApps.hasFilter()) {
                // search result screen has only one SECTION_BREAK_VIEW
                numEmptyNode = 1;
            } else {
                // default all apps screen may have one or two SECTION_BREAK_VIEW
                numEmptyNode = 1;
                if (mApps.hasPredictedComponents()) {
                    if (viewType == PREDICTION_ICON_VIEW_TYPE) {
                        numEmptyNode = 1;
                    } else if (viewType == ICON_VIEW_TYPE) {
                        numEmptyNode = 2;
                    }
                } else {
                    if (viewType == ICON_VIEW_TYPE) {
                        numEmptyNode = 1;
                    }
                }
            }
            return numEmptyNode;
        }
    }

    /**
     * Helper class to size the grid items.
     */
    public class GridSpanSizer extends GridLayoutManager.SpanSizeLookup {

        public GridSpanSizer() {
            super();
            setSpanIndexCacheEnabled(true);
        }

        @Override
        public int getSpanSize(int position) {
            switch (mApps.getAdapterItems().get(position).viewType) {
                case AllAppsGridAdapter.ICON_VIEW_TYPE:
                case AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE:
                    return 1;
                default:
                    // Section breaks span the full width
                    return mAppsPerRow;
            }
        }
    }

    /**
     * Helper class to draw the section headers
     */
    public class GridItemDecoration extends RecyclerView.ItemDecoration {

        private static final boolean DEBUG_SECTION_MARGIN = false;
        private static final boolean FADE_OUT_SECTIONS = false;

        private HashMap<String, PointF> mCachedSectionBounds = new HashMap<>();
        private Rect mTmpBounds = new Rect();

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            if (mApps.hasFilter() || mAppsPerRow == 0) {
                return;
            }

            if (DEBUG_SECTION_MARGIN) {
                Paint p = new Paint();
                p.setColor(0x33ff0000);
                c.drawRect(mBackgroundPadding.left, 0, mBackgroundPadding.left + mSectionNamesMargin,
                        parent.getMeasuredHeight(), p);
            }

            List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
            boolean hasDrawnPredictedAppsDivider = false;
            boolean showSectionNames = mSectionNamesMargin > 0;
            int childCount = parent.getChildCount();
            int lastSectionTop = 0;
            int lastSectionHeight = 0;
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                ViewHolder holder = (ViewHolder) parent.getChildViewHolder(child);
                if (!isValidHolderAndChild(holder, child, items)) {
                    continue;
                }

                if (shouldDrawItemDivider(holder, items) && !hasDrawnPredictedAppsDivider) {
                    // Draw the divider under the predicted apps
                    int top = child.getTop() + child.getHeight() + mPredictionBarDividerOffset;
                    c.drawLine(mBackgroundPadding.left, top,
                            parent.getWidth() - mBackgroundPadding.right, top,
                            mPredictedAppsDividerPaint);
                    hasDrawnPredictedAppsDivider = true;

                } else if (showSectionNames && shouldDrawItemSection(holder, i, items)) {
                    // At this point, we only draw sections for each section break;
                    int viewTopOffset = (2 * child.getPaddingTop());
                    int pos = holder.getPosition();
                    AlphabeticalAppsList.AdapterItem item = items.get(pos);
                    AlphabeticalAppsList.SectionInfo sectionInfo = item.sectionInfo;

                    // Draw all the sections for this index
                    String lastSectionName = item.sectionName;
                    for (int j = item.sectionAppIndex; j < sectionInfo.numApps; j++, pos++) {
                        AlphabeticalAppsList.AdapterItem nextItem = items.get(pos);
                        String sectionName = nextItem.sectionName;
                        if (nextItem.sectionInfo != sectionInfo) {
                            break;
                        }
                        if (j > item.sectionAppIndex && sectionName.equals(lastSectionName)) {
                            continue;
                        }


                        // Find the section name bounds
                        PointF sectionBounds = getAndCacheSectionBounds(sectionName);

                        // Calculate where to draw the section
                        int sectionBaseline = (int) (viewTopOffset + sectionBounds.y);
                        int x = mIsRtl ?
                                parent.getWidth() - mBackgroundPadding.left - mSectionNamesMargin :
                                        mBackgroundPadding.left;
                        x += (int) ((mSectionNamesMargin - sectionBounds.x) / 2f);
                        int y = child.getTop() + sectionBaseline;

                        // Determine whether this is the last row with apps in that section, if
                        // so, then fix the section to the row allowing it to scroll past the
                        // baseline, otherwise, bound it to the baseline so it's in the viewport
                        int appIndexInSection = items.get(pos).sectionAppIndex;
                        int nextRowPos = Math.min(items.size() - 1,
                                pos + mAppsPerRow - (appIndexInSection % mAppsPerRow));
                        AlphabeticalAppsList.AdapterItem nextRowItem = items.get(nextRowPos);
                        boolean fixedToRow = !sectionName.equals(nextRowItem.sectionName);
                        if (!fixedToRow) {
                            y = Math.max(sectionBaseline, y);
                        }

                        // In addition, if it overlaps with the last section that was drawn, then
                        // offset it so that it does not overlap
                        if (lastSectionHeight > 0 && y <= (lastSectionTop + lastSectionHeight)) {
                            y += lastSectionTop - y + lastSectionHeight;
                        }

                        // Draw the section header
                        if (FADE_OUT_SECTIONS) {
                            int alpha = 255;
                            if (fixedToRow) {
                                alpha = Math.min(255,
                                        (int) (255 * (Math.max(0, y) / (float) sectionBaseline)));
                            }
                            mSectionTextPaint.setAlpha(alpha);
                        }
                        c.drawText(sectionName, x, y, mSectionTextPaint);

                        lastSectionTop = y;
                        lastSectionHeight = (int) (sectionBounds.y + mSectionHeaderOffset);
                        lastSectionName = sectionName;
                    }
                    i += (sectionInfo.numApps - item.sectionAppIndex);
                }
            }
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            // Do nothing
        }

        /**
         * Given a section name, return the bounds of the given section name.
         */
        private PointF getAndCacheSectionBounds(String sectionName) {
            PointF bounds = mCachedSectionBounds.get(sectionName);
            if (bounds == null) {
                mSectionTextPaint.getTextBounds(sectionName, 0, sectionName.length(), mTmpBounds);
                bounds = new PointF(mSectionTextPaint.measureText(sectionName), mTmpBounds.height());
                mCachedSectionBounds.put(sectionName, bounds);
            }
            return bounds;
        }

        /**
         * Returns whether we consider this a valid view holder for us to draw a divider or section for.
         */
        private boolean isValidHolderAndChild(ViewHolder holder, View child,
                List<AlphabeticalAppsList.AdapterItem> items) {
            // Ensure item is not already removed
            GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams)
                    child.getLayoutParams();
            if (lp.isItemRemoved()) {
                return false;
            }
            // Ensure we have a valid holder
            if (holder == null) {
                return false;
            }
            // Ensure we have a holder position
            int pos = holder.getPosition();
            if (pos < 0 || pos >= items.size()) {
                return false;
            }
            return true;
        }

        /**
         * Returns whether to draw the divider for a given child.
         */
        private boolean shouldDrawItemDivider(ViewHolder holder,
                List<AlphabeticalAppsList.AdapterItem> items) {
            int pos = holder.getPosition();
            return items.get(pos).viewType == AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE;
        }

        /**
         * Returns whether to draw the section for the given child.
         */
        private boolean shouldDrawItemSection(ViewHolder holder, int childIndex,
                List<AlphabeticalAppsList.AdapterItem> items) {
            int pos = holder.getPosition();
            AlphabeticalAppsList.AdapterItem item = items.get(pos);

            // Ensure it's an icon
            if (item.viewType != AllAppsGridAdapter.ICON_VIEW_TYPE) {
                return false;
            }
            // Draw the section header for the first item in each section
            return (childIndex == 0) ||
                    (items.get(pos - 1).viewType == AllAppsGridAdapter.SECTION_BREAK_VIEW_TYPE);
        }
    }

    private final Launcher mLauncher;
    private final LayoutInflater mLayoutInflater;
    private final AlphabeticalAppsList mApps;
    private final GridLayoutManager mGridLayoutMgr;
    private final GridSpanSizer mGridSizer;
    private final GridItemDecoration mItemDecoration;
    private final View.OnTouchListener mTouchListener;
    private final View.OnClickListener mIconClickListener;
    private final View.OnLongClickListener mIconLongClickListener;

    private final Rect mBackgroundPadding = new Rect();
    private final boolean mIsRtl;

    // Section drawing
    private final int mSectionNamesMargin;
    private final int mSectionHeaderOffset;
    private final Paint mSectionTextPaint;
    private final Paint mPredictedAppsDividerPaint;

    private final int mPredictionBarDividerOffset;
    private int mAppsPerRow;
    private BindViewCallback mBindViewCallback;
    private AllAppsSearchBarController mSearchController;

    // The text to show when there are no search results and no market search handler.
    private String mEmptySearchMessage;
    // The name of the market app which handles searches, to be used in the format str
    // below when updating the search-market view.  Only needs to be loaded once.
    private String mMarketAppName;
    // The text to show when there is a market app which can handle a specific query, updated
    // each time the search query changes.
    private String mMarketSearchMessage;
    // The intent to send off to the market app, updated each time the search query changes.
    private Intent mMarketSearchIntent;

    public AllAppsGridAdapter(Launcher launcher, AlphabeticalAppsList apps,
            View.OnTouchListener touchListener, View.OnClickListener iconClickListener,
            View.OnLongClickListener iconLongClickListener) {
        Resources res = launcher.getResources();
        mLauncher = launcher;
        mApps = apps;
        mEmptySearchMessage = res.getString(R.string.all_apps_loading_message);
        mGridSizer = new GridSpanSizer();
        mGridLayoutMgr = new AppsGridLayoutManager(launcher);
        mGridLayoutMgr.setSpanSizeLookup(mGridSizer);
        mItemDecoration = new GridItemDecoration();
        mLayoutInflater = LayoutInflater.from(launcher);
        mTouchListener = touchListener;
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
        mSectionNamesMargin = res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin);
        mSectionHeaderOffset = res.getDimensionPixelSize(R.dimen.all_apps_grid_section_y_offset);
        mIsRtl = Utilities.isRtl(res);

        mSectionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSectionTextPaint.setTextSize(res.getDimensionPixelSize(
                R.dimen.all_apps_grid_section_text_size));
        mSectionTextPaint.setColor(res.getColor(R.color.all_apps_grid_section_text_color));

        mPredictedAppsDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPredictedAppsDividerPaint.setStrokeWidth(Utilities.pxFromDp(1f, res.getDisplayMetrics()));
        mPredictedAppsDividerPaint.setColor(0x1E000000);
        mPredictionBarDividerOffset =
                (-res.getDimensionPixelSize(R.dimen.all_apps_prediction_icon_bottom_padding) +
                        res.getDimensionPixelSize(R.dimen.all_apps_icon_top_bottom_padding)) / 2;
    }

    /**
     * Sets the number of apps per row.
     */
    public void setNumAppsPerRow(int appsPerRow) {
        mAppsPerRow = appsPerRow;
        mGridLayoutMgr.setSpanCount(appsPerRow);
    }

    public void setSearchController(AllAppsSearchBarController searchController) {
        mSearchController = searchController;

        // Resolve the market app handling additional searches
        PackageManager pm = mLauncher.getPackageManager();
        ResolveInfo marketInfo = pm.resolveActivity(mSearchController.createMarketSearchIntent(""),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (marketInfo != null) {
            mMarketAppName = marketInfo.loadLabel(pm).toString();
        }
    }

    /**
     * Sets the last search query that was made, used to show when there are no results and to also
     * seed the intent for searching the market.
     */
    public void setLastSearchQuery(String query) {
        Resources res = mLauncher.getResources();
        String formatStr = res.getString(R.string.all_apps_no_search_results);
        mEmptySearchMessage = String.format(formatStr, query);
        if (mMarketAppName != null) {
            mMarketSearchMessage = String.format(res.getString(R.string.all_apps_search_market_message),
                    mMarketAppName);
            mMarketSearchIntent = mSearchController.createMarketSearchIntent(query);
        }
    }

    /**
     * Sets the callback for when views are bound.
     */
    public void setBindViewCallback(BindViewCallback cb) {
        mBindViewCallback = cb;
    }

    /**
     * Notifies the adapter of the background padding so that it can draw things correctly in the
     * item decorator.
     */
    public void updateBackgroundPadding(Rect padding) {
        mBackgroundPadding.set(padding);
    }

    /**
     * Returns the grid layout manager.
     */
    public GridLayoutManager getLayoutManager() {
        return mGridLayoutMgr;
    }

    /**
     * Returns the item decoration for the recycler view.
     */
    public RecyclerView.ItemDecoration getItemDecoration() {
        // We don't draw any headers when we are uncomfortably dense
        return mItemDecoration;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case SECTION_BREAK_VIEW_TYPE:
                return new ViewHolder(new View(parent.getContext()));
            case ICON_VIEW_TYPE: {
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.all_apps_icon, parent, false);
                icon.setOnTouchListener(mTouchListener);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setLongPressTimeout(ViewConfiguration.get(parent.getContext())
                        .getLongPressTimeout());
                icon.setFocusable(true);
                return new ViewHolder(icon);
            }
            case PREDICTION_ICON_VIEW_TYPE: {
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.all_apps_prediction_bar_icon, parent, false);
                icon.setOnTouchListener(mTouchListener);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setLongPressTimeout(ViewConfiguration.get(parent.getContext())
                        .getLongPressTimeout());
                icon.setFocusable(true);
                return new ViewHolder(icon);
            }
            case EMPTY_SEARCH_VIEW_TYPE:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_empty_search,
                        parent, false));
            case SEARCH_MARKET_DIVIDER_VIEW_TYPE:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_search_market_divider,
                        parent, false));
            case SEARCH_MARKET_VIEW_TYPE:
                View searchMarketView = mLayoutInflater.inflate(R.layout.all_apps_search_market,
                        parent, false);
                searchMarketView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mLauncher.startActivitySafely(v, mMarketSearchIntent, null);
                    }
                });
                return new ViewHolder(searchMarketView);
            default:
                throw new RuntimeException("Unexpected view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case ICON_VIEW_TYPE: {
                AppInfo info = mApps.getAdapterItems().get(position).appInfo;
                BubbleTextView icon = (BubbleTextView) holder.mContent;
                icon.applyFromApplicationInfo(info);
                icon.setAccessibilityDelegate(
                        LauncherAppState.getInstance().getAccessibilityDelegate());
                break;
            }
            case PREDICTION_ICON_VIEW_TYPE: {
                AppInfo info = mApps.getAdapterItems().get(position).appInfo;
                BubbleTextView icon = (BubbleTextView) holder.mContent;
                icon.applyFromApplicationInfo(info);
                icon.setAccessibilityDelegate(
                        LauncherAppState.getInstance().getAccessibilityDelegate());
                break;
            }
            case EMPTY_SEARCH_VIEW_TYPE:
                TextView emptyViewText = (TextView) holder.mContent;
                emptyViewText.setText(mEmptySearchMessage);
                emptyViewText.setGravity(mApps.hasNoFilteredResults() ? Gravity.CENTER :
                        Gravity.START | Gravity.CENTER_VERTICAL);
                break;
            case SEARCH_MARKET_VIEW_TYPE:
                TextView searchView = (TextView) holder.mContent;
                if (mMarketSearchIntent != null) {
                    searchView.setVisibility(View.VISIBLE);
                    searchView.setContentDescription(mMarketSearchMessage);
                    searchView.setGravity(mApps.hasNoFilteredResults() ? Gravity.CENTER :
                            Gravity.START | Gravity.CENTER_VERTICAL);
                    searchView.setText(mMarketSearchMessage);
                } else {
                    searchView.setVisibility(View.GONE);
                }
                break;
        }
        if (mBindViewCallback != null) {
            mBindViewCallback.onBindView(holder);
        }
    }

    @Override
    public boolean onFailedToRecycleView(ViewHolder holder) {
        // Always recycle and we will reset the view when it is bound
        return true;
    }

    @Override
    public int getItemCount() {
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }
}
