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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.views.ActivityContext;

import java.util.List;

/**
 * The grid view adapter of all the apps.
 *
 * @param <T> Type of context inflating all apps.
 */
public class AllAppsGridAdapter<T extends Context & ActivityContext> extends
        BaseAllAppsAdapter<T> {

    public static final String TAG = "AppsGridAdapter";
    private final GridLayoutManager mGridLayoutMgr;
    private final GridSpanSizer mGridSizer;

    public AllAppsGridAdapter(T activityContext, LayoutInflater inflater,
            AlphabeticalAppsList apps, BaseAdapterProvider[] adapterProviders) {
        super(activityContext, inflater, apps, adapterProviders);
        mGridSizer = new GridSpanSizer();
        mGridLayoutMgr = new AppsGridLayoutManager(mActivityContext);
        mGridLayoutMgr.setSpanSizeLookup(mGridSizer);
        setAppsPerRow(activityContext.getDeviceProfile().numShownAllAppsColumns);
    }

    /**
     * Returns the grid layout manager.
     */
    public RecyclerView.LayoutManager getLayoutManager() {
        return mGridLayoutMgr;
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
            record.setItemCount(mApps.getNumFilteredApps());
            record.setFromIndex(Math.max(0,
                    record.getFromIndex() - getRowsNotForAccessibility(record.getFromIndex())));
            record.setToIndex(Math.max(0,
                    record.getToIndex() - getRowsNotForAccessibility(record.getToIndex())));
        }

        @Override
        public int getRowCountForAccessibility(RecyclerView.Recycler recycler,
                RecyclerView.State state) {
            return super.getRowCountForAccessibility(recycler, state) -
                    getRowsNotForAccessibility(mApps.getAdapterItems().size() - 1);
        }

        @Override
        public void onInitializeAccessibilityNodeInfoForItem(RecyclerView.Recycler recycler,
                RecyclerView.State state, View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info);

            ViewGroup.LayoutParams lp = host.getLayoutParams();
            AccessibilityNodeInfoCompat.CollectionItemInfoCompat cic = info.getCollectionItemInfo();
            if (!(lp instanceof LayoutParams) || (cic == null)) {
                return;
            }
            LayoutParams glp = (LayoutParams) lp;
            info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    cic.getRowIndex() - getRowsNotForAccessibility(glp.getViewAdapterPosition()),
                    cic.getRowSpan(),
                    cic.getColumnIndex(),
                    cic.getColumnSpan(),
                    cic.isHeading(),
                    cic.isSelected()));
        }

        /**
         * Returns the number of rows before {@param adapterPosition}, including this position
         * which should not be counted towards the collection info.
         */
        private int getRowsNotForAccessibility(int adapterPosition) {
            List<AdapterItem> items = mApps.getAdapterItems();
            adapterPosition = Math.max(adapterPosition, items.size() - 1);
            int extraRows = 0;
            for (int i = 0; i <= adapterPosition; i++) {
                if (!isViewType(items.get(i).viewType, VIEW_TYPE_MASK_ICON)) {
                    extraRows++;
                }
            }
            return extraRows;
        }
    }

    @Override
    public void setAppsPerRow(int appsPerRow) {
        mAppsPerRow = appsPerRow;
        int totalSpans = mAppsPerRow;
        for (BaseAdapterProvider adapterProvider : mAdapterProviders) {
            for (int itemPerRow : adapterProvider.getSupportedItemsPerRowArray()) {
                if (totalSpans % itemPerRow != 0) {
                    totalSpans *= itemPerRow;
                }
            }
        }
        mGridLayoutMgr.setSpanCount(totalSpans);
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
            int viewType = mApps.getAdapterItems().get(position).viewType;
            int totalSpans = mGridLayoutMgr.getSpanCount();
            if (isIconViewType(viewType)) {
                return totalSpans / mAppsPerRow;
            } else {
                BaseAdapterProvider adapterProvider = getAdapterProvider(viewType);
                if (adapterProvider != null) {
                    return totalSpans / adapterProvider.getItemsPerRow(viewType, mAppsPerRow);
                }

                // Section breaks span the full width
                return totalSpans;
            }
        }
    }
}
