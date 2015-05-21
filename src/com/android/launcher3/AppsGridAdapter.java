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
package com.android.launcher3;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.launcher3.util.Thunk;

import java.util.HashMap;
import java.util.List;


/**
 * The grid view adapter of all the apps.
 */
class AppsGridAdapter extends RecyclerView.Adapter<AppsGridAdapter.ViewHolder> {

    public static final String TAG = "AppsGridAdapter";
    private static final boolean DEBUG = false;

    // A section break in the grid
    public static final int SECTION_BREAK_VIEW_TYPE = 0;
    // A normal icon
    public static final int ICON_VIEW_TYPE = 1;
    // The message shown when there are no filtered results
    public static final int EMPTY_VIEW_TYPE = 2;
    // The spacer used for the prediction bar
    public static final int PREDICTION_BAR_SPACER_TYPE = 3;

    /**
     * Callback for when the prediction bar spacer is bound.
     */
    public interface PredictionBarSpacerCallbacks {
        void onBindPredictionBar();
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
     * Helper class to size the grid items.
     */
    public class GridSpanSizer extends GridLayoutManager.SpanSizeLookup {

        public GridSpanSizer() {
            super();
            setSpanIndexCacheEnabled(true);
        }

        @Override
        public int getSpanSize(int position) {
            if (mApps.hasNoFilteredResults()) {
                // Empty view spans full width
                return mAppsPerRow;
            }

            if (mApps.getAdapterItems().get(position).viewType != AppsGridAdapter.ICON_VIEW_TYPE) {
                // Both the section breaks and predictive bar span the full width
                return mAppsPerRow;
            } else {
                return 1;
            }
        }
    }

    /**
     * Helper class to draw the section headers
     */
    public class GridItemDecoration extends RecyclerView.ItemDecoration {

        private static final boolean FADE_OUT_SECTIONS = false;

        private HashMap<String, PointF> mCachedSectionBounds = new HashMap<>();
        private Rect mTmpBounds = new Rect();
        private Launcher mLauncher;

        public GridItemDecoration(Context context) {
            mLauncher = (Launcher) context;
        }

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            if (mApps.hasFilter()) {
                return;
            }

            DeviceProfile grid = mLauncher.getDeviceProfile();
            List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
            boolean hasDrawnPredictedAppsDivider = false;
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
                    int top = child.getTop() + child.getHeight();
                    c.drawLine(mBackgroundPadding.left, top,
                            parent.getWidth() - mBackgroundPadding.right, top,
                            mPredictedAppsDividerPaint);
                    hasDrawnPredictedAppsDivider = true;

                } else if (grid.isPhone() && shouldDrawItemSection(holder, i, items)) {
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
                        int x = mIsRtl ? parent.getWidth() - mPaddingStart - mStartMargin :
                                mPaddingStart;
                        x += (int) ((mStartMargin - sectionBounds.x) / 2f);
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
            return items.get(pos).viewType == AppsGridAdapter.PREDICTION_BAR_SPACER_TYPE;
        }

        /**
         * Returns whether to draw the section for the given child.
         */
        private boolean shouldDrawItemSection(ViewHolder holder, int childIndex,
                List<AlphabeticalAppsList.AdapterItem> items) {
            int pos = holder.getPosition();
            AlphabeticalAppsList.AdapterItem item = items.get(pos);

            // Ensure it's an icon
            if (item.viewType != AppsGridAdapter.ICON_VIEW_TYPE) {
                return false;
            }
            // Draw the section header for the first item in each section
            return (childIndex == 0) ||
                    (items.get(pos - 1).viewType == AppsGridAdapter.SECTION_BREAK_VIEW_TYPE);
        }
    }

    private Handler mHandler;
    private LayoutInflater mLayoutInflater;
    @Thunk AlphabeticalAppsList mApps;
    private GridLayoutManager mGridLayoutMgr;
    private GridSpanSizer mGridSizer;
    private GridItemDecoration mItemDecoration;
    private PredictionBarSpacerCallbacks mPredictionBarCb;
    private View.OnTouchListener mTouchListener;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;
    @Thunk final Rect mBackgroundPadding = new Rect();
    @Thunk int mPredictionBarHeight;
    @Thunk int mAppsPerRow;
    @Thunk boolean mIsRtl;
    private String mEmptySearchText;

    // Section drawing
    @Thunk int mPaddingStart;
    @Thunk int mStartMargin;
    @Thunk int mSectionHeaderOffset;
    @Thunk Paint mSectionTextPaint;
    @Thunk Paint mPredictedAppsDividerPaint;

    public AppsGridAdapter(Context context, AlphabeticalAppsList apps, int appsPerRow,
            PredictionBarSpacerCallbacks pbCb, View.OnTouchListener touchListener,
            View.OnClickListener iconClickListener, View.OnLongClickListener iconLongClickListener) {
        Resources res = context.getResources();
        mHandler = new Handler();
        mApps = apps;
        mAppsPerRow = appsPerRow;
        mPredictionBarCb = pbCb;
        mGridSizer = new GridSpanSizer();
        mGridLayoutMgr = new GridLayoutManager(context, appsPerRow, GridLayoutManager.VERTICAL,
                false);
        mGridLayoutMgr.setSpanSizeLookup(mGridSizer);
        mItemDecoration = new GridItemDecoration(context);
        mLayoutInflater = LayoutInflater.from(context);
        mTouchListener = touchListener;
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
        mStartMargin = res.getDimensionPixelSize(R.dimen.apps_grid_view_start_margin);
        mSectionHeaderOffset = res.getDimensionPixelSize(R.dimen.apps_grid_section_y_offset);
        mPaddingStart = res.getDimensionPixelSize(R.dimen.apps_container_inset);

        mSectionTextPaint = new Paint();
        mSectionTextPaint.setTextSize(res.getDimensionPixelSize(
                R.dimen.apps_view_section_text_size));
        mSectionTextPaint.setColor(res.getColor(R.color.apps_view_section_text_color));
        mSectionTextPaint.setAntiAlias(true);

        mPredictedAppsDividerPaint = new Paint();
        mPredictedAppsDividerPaint.setStrokeWidth(Utilities.pxFromDp(1f, res.getDisplayMetrics()));
        mPredictedAppsDividerPaint.setColor(0x1E000000);
        mPredictedAppsDividerPaint.setAntiAlias(true);
    }

    /**
     * Sets the number of apps per row.
     */
    public void setNumAppsPerRow(int appsPerRow) {
        mAppsPerRow = appsPerRow;
        mGridLayoutMgr.setSpanCount(appsPerRow);
    }

    /**
     * Sets the prediction row height.
     */
    public void setPredictionRowHeight(int height) {
        mPredictionBarHeight = height;
    }

    /**
     * Sets whether we are in RTL mode.
     */
    public void setRtl(boolean rtl) {
        mIsRtl = rtl;
    }

    /**
     * Sets the text to show when there are no apps.
     */
    public void setEmptySearchText(String query) {
        mEmptySearchText = query;
    }

    /**
     * Notifies the adapter of the background padding so that it can draw things correctly in the
     * item decorator.
     */
    public void updateBackgroundPadding(Drawable background) {
        background.getPadding(mBackgroundPadding);
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

    /**
     * Returns the left padding for the recycler view.
     */
    public int getContentMarginStart() {
        return mStartMargin;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case EMPTY_VIEW_TYPE:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.apps_empty_view, parent,
                        false));
            case SECTION_BREAK_VIEW_TYPE:
                return new ViewHolder(new View(parent.getContext()));
            case PREDICTION_BAR_SPACER_TYPE:
                // Create a view of a specific height to match the floating prediction bar
                View v = new View(parent.getContext());
                ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, mPredictionBarHeight);
                v.setLayoutParams(lp);
                return new ViewHolder(v);
            case ICON_VIEW_TYPE:
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.apps_grid_icon_view, parent, false);
                icon.setOnTouchListener(mTouchListener);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setFocusable(true);
                return new ViewHolder(icon);
            default:
                throw new RuntimeException("Unexpected view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case ICON_VIEW_TYPE:
                AppInfo info = mApps.getAdapterItems().get(position).appInfo;
                BubbleTextView icon = (BubbleTextView) holder.mContent;
                icon.applyFromApplicationInfo(info);
                break;
            case PREDICTION_BAR_SPACER_TYPE:
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mPredictionBarCb != null) {
                            mPredictionBarCb.onBindPredictionBar();
                        }
                    }
                });
                break;
            case EMPTY_VIEW_TYPE:
                TextView emptyViewText = (TextView) holder.mContent.findViewById(R.id.empty_text);
                emptyViewText.setText(mEmptySearchText);
                break;
        }
    }

    @Override
    public int getItemCount() {
        if (mApps.hasNoFilteredResults()) {
            // For the empty view
            return 1;
        }
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (mApps.hasNoFilteredResults()) {
            return EMPTY_VIEW_TYPE;
        }

        AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }
}
