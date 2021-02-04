/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.search;

import static com.android.launcher3.allapps.AllAppsGridAdapter.VIEW_TYPE_ALL_APPS_DIVIDER;
import static com.android.launcher3.allapps.AllAppsGridAdapter.VIEW_TYPE_ICON;

import android.app.search.SearchTarget;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.app.search.LayoutType;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.config.FeatureFlags;

/**
 * Provides views for on-device search results
 */
public class DeviceSearchAdapterProvider extends SearchAdapterProvider {

    public static final int VIEW_TYPE_SEARCH_CORPUS_TITLE = 1 << 5;
    public static final int VIEW_TYPE_SEARCH_SLICE = 1 << 7;
    public static final int VIEW_TYPE_SEARCH_ICON = (1 << 8) | VIEW_TYPE_ICON;
    public static final int VIEW_TYPE_SEARCH_ICON_ROW = (1 << 9);
    public static final int VIEW_TYPE_SEARCH_SMALL_ICON_ROW = (1 << 10);
    public static final int VIEW_TYPE_SEARCH_THUMBNAIL = 1 << 12;
    public static final int VIEW_TYPE_SEARCH_WIDGET_LIVE = 1 << 15;
    public static final int VIEW_TYPE_SEARCH_WIDGET_PREVIEW = 1 << 16;

    private static final String TAG = "SearchServiceAdapter";

    private final AllAppsContainerView mAppsView;
    private final SparseIntArray mViewTypeToLayoutMap = new SparseIntArray();

    public DeviceSearchAdapterProvider(Launcher launcher, AllAppsContainerView appsView) {
        super(launcher);
        mAppsView = appsView;

        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_CORPUS_TITLE, R.layout.search_section_title);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_ICON, R.layout.search_result_icon);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_ICON_ROW, R.layout.search_result_icon_row);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_SMALL_ICON_ROW, R.layout.search_result_small_icon_row);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_SLICE, R.layout.search_result_slice);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_THUMBNAIL, R.layout.search_result_thumbnail);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_WIDGET_LIVE, R.layout.search_result_widget_live);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_WIDGET_PREVIEW,
                R.layout.search_result_widget_preview);
        mViewTypeToLayoutMap.put(VIEW_TYPE_ALL_APPS_DIVIDER, R.layout.all_apps_divider);
    }

    @Override
    public void onBindView(AllAppsGridAdapter.ViewHolder holder, int position) {
        SearchAdapterItem item = (SearchAdapterItem) mAppsView.getApps().getAdapterItems().get(
                position);
        SearchTargetHandler
                payloadResultView =
                (SearchTargetHandler) holder.itemView;
        payloadResultView.apply(item.getSearchTarget(), item.getInlineItems());
    }

    @Override
    public boolean isSearchView(int viewType) {
        return mViewTypeToLayoutMap.get(viewType, -1) != -1;
    }

    @Override
    public AllAppsGridAdapter.ViewHolder onCreateViewHolder(LayoutInflater inflater,
            ViewGroup parent, int viewType) {
        return new AllAppsGridAdapter.ViewHolder(inflater.inflate(
                mViewTypeToLayoutMap.get(viewType), parent, false));
    }

    @Override
    public int getGridSpanSize(int viewType, int appsPerRow) {
        if (viewType == VIEW_TYPE_SEARCH_THUMBNAIL
                || viewType == VIEW_TYPE_SEARCH_WIDGET_PREVIEW) {
            return appsPerRow;
        }
        return super.getGridSpanSize(viewType, appsPerRow);
    }


    @Override
    public boolean onAdapterItemSelected(AllAppsGridAdapter.AdapterItem adapterItem, View view) {
        if (view instanceof SearchTargetHandler) {
            return ((SearchTargetHandler) view).quickSelect();
        }
        return false;
    }

    /**
     * Determines what view type should be used to present search target.
     * Returns -1 if viewType is not found or if required field is not present
     * to render the viewType.
     */
    public int getViewTypeForSearchTarget(SearchTarget t) {
        switch (t.getLayoutType()) {
            case LayoutType.TEXT_HEADER:
                return VIEW_TYPE_SEARCH_CORPUS_TITLE;
            case LayoutType.ICON_SINGLE_VERTICAL_TEXT:
                return VIEW_TYPE_SEARCH_ICON;
            case LayoutType.ICON_SLICE:
                if (FeatureFlags.DISABLE_SLICE_IN_ALLAPPS.get()) {
                    return -1;
                }
                if (t.getSliceUri() != null) {
                    return VIEW_TYPE_SEARCH_SLICE;
                }
                Log.w(TAG, "LayoutType.ICON_SLICE target doesn't contain sliceUri.");
                break;
            case LayoutType.ICON_DOUBLE_HORIZONTAL_TEXT:
            case LayoutType.ICON_SINGLE_HORIZONTAL_TEXT:
            case LayoutType.ICON_DOUBLE_HORIZONTAL_TEXT_BUTTON:
            case LayoutType.ICON_HORIZONTAL_TEXT:
                return VIEW_TYPE_SEARCH_ICON_ROW;
            case LayoutType.SMALL_ICON_HORIZONTAL_TEXT:
                return VIEW_TYPE_SEARCH_SMALL_ICON_ROW;
            case LayoutType.THUMBNAIL:
                if (t.getSearchAction() != null) {
                    return VIEW_TYPE_SEARCH_THUMBNAIL;
                }
                Log.w(TAG, "LayoutType.THUMBNAIL target doesn't contain searchAction.");
                break;
            case LayoutType.WIDGET_PREVIEW:
                return VIEW_TYPE_SEARCH_WIDGET_PREVIEW;
            case LayoutType.WIDGET_LIVE:
                return VIEW_TYPE_SEARCH_WIDGET_LIVE;
            case LayoutType.DIVIDER:
                return VIEW_TYPE_ALL_APPS_DIVIDER;
        }

        return -1;
    }
}
