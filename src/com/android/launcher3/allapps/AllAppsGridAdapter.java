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
import androidx.recyclerview.widget.RecyclerView.Adapter;

import com.android.launcher3.util.ScrollableLayoutManager;
import com.android.launcher3.views.ActivityContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The grid view adapter of all the apps.
 *
 * @param <T> Type of context inflating all apps.
 */
public class AllAppsGridAdapter<T extends Context & ActivityContext> extends
        BaseAllAppsAdapter<T> {

    public static final String TAG = "AppsGridAdapter";
    private final AppsGridLayoutManager mGridLayoutMgr;
    private final CopyOnWriteArrayList<OnLayoutCompletedListener> mOnLayoutCompletedListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Listener for {@link RecyclerView.LayoutManager#onLayoutCompleted(RecyclerView.State)}
     */
    public interface OnLayoutCompletedListener {
        void onLayoutCompleted();
    }

    /**
     * Adds a {@link OnLayoutCompletedListener} to receive a callback when {@link
     * RecyclerView.LayoutManager#onLayoutCompleted(RecyclerView.State)} is called
     */
    public void addOnLayoutCompletedListener(OnLayoutCompletedListener listener) {
        mOnLayoutCompletedListeners.add(listener);
    }

    /**
     * Removes a {@link OnLayoutCompletedListener} to not receive a callback when {@link
     * RecyclerView.LayoutManager#onLayoutCompleted(RecyclerView.State)} is called
     */
    public void removeOnLayoutCompletedListener(OnLayoutCompletedListener listener) {
        mOnLayoutCompletedListeners.remove(listener);
    }


    public AllAppsGridAdapter(T activityContext, LayoutInflater inflater,
            AlphabeticalAppsList apps, BaseAdapterProvider[] adapterProviders) {
        super(activityContext, inflater, apps, adapterProviders);
        mGridLayoutMgr = new AppsGridLayoutManager(mActivityContext);
        mGridLayoutMgr.setSpanSizeLookup(new GridSpanSizer());
        setAppsPerRow(activityContext.getDeviceProfile().numShownAllAppsColumns);
    }

    /**
     * Returns the grid layout manager.
     */
    public AppsGridLayoutManager getLayoutManager() {
        return mGridLayoutMgr;
    }

    /** @return the column index that the given adapter index falls. */
    public int getSpanIndex(int adapterIndex) {
        AppsGridLayoutManager lm = getLayoutManager();
        return lm.getSpanSizeLookup().getSpanIndex(adapterIndex, lm.getSpanCount());
    }

    /**
     * A subclass of GridLayoutManager that overrides accessibility values during app search.
     */
    public class AppsGridLayoutManager extends ScrollableLayoutManager {

        public AppsGridLayoutManager(Context context) {
            super(context);
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
            for (int i = 0; i <= adapterPosition && i < items.size(); i++) {
                if (!isViewType(items.get(i).viewType, VIEW_TYPE_MASK_ICON)) {
                    extraRows++;
                }
            }
            return extraRows;
        }

        @Override
        public void onLayoutCompleted(RecyclerView.State state) {
            super.onLayoutCompleted(state);
            for (OnLayoutCompletedListener listener : mOnLayoutCompletedListeners) {
                listener.onLayoutCompleted();
            }
        }

        @Override
        protected int incrementTotalHeight(Adapter adapter, int position, int heightUntilLastPos) {
            AllAppsGridAdapter.AdapterItem item = mApps.getAdapterItems().get(position);
            // only account for the first icon in the row since they are the same size within a row
            return (isIconViewType(item.viewType) && item.rowAppIndex != 0)
                    ? heightUntilLastPos
                    : (heightUntilLastPos + mCachedSizes.get(item.viewType));
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
            int totalSpans = mGridLayoutMgr.getSpanCount();
            List<AdapterItem> items = mApps.getAdapterItems();
            if (position >= items.size()) {
                return totalSpans;
            }
            int viewType = items.get(position).viewType;
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
