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

import static com.android.launcher3.allapps.AllAppsGridAdapter.VIEW_TYPE_ICON;

import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.systemui.plugins.shared.SearchTarget;

/**
 * Provides views for on-device search results
 */
public class DeviceSearchAdapterProvider extends SearchAdapterProvider {

    public static final int VIEW_TYPE_SEARCH_CORPUS_TITLE = 1 << 5;
    public static final int VIEW_TYPE_SEARCH_ROW_WITH_BUTTON = 1 << 7;
    public static final int VIEW_TYPE_SEARCH_ROW = 1 << 8;
    public static final int VIEW_TYPE_SEARCH_SLICE = 1 << 9;
    public static final int VIEW_TYPE_SEARCH_ICON_ROW = 1 << 10;
    public static final int VIEW_TYPE_SEARCH_PEOPLE = 1 << 11;
    public static final int VIEW_TYPE_SEARCH_THUMBNAIL = 1 << 12;
    public static final int VIEW_TYPE_SEARCH_SUGGEST = 1 << 13;
    public static final int VIEW_TYPE_SEARCH_ICON = (1 << 14) | VIEW_TYPE_ICON;
    public static final int VIEW_TYPE_SEARCH_WIDGET_LIVE = 1 << 15;
    public static final int VIEW_TYPE_SEARCH_WIDGET_PREVIEW = 1 << 16;


    private final SparseIntArray mViewTypeToLayoutMap = new SparseIntArray();

    public DeviceSearchAdapterProvider(Launcher launcher) {
        super(launcher);

        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_ICON, R.layout.search_result_icon);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_CORPUS_TITLE, R.layout.search_section_title);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_ROW_WITH_BUTTON,
                R.layout.search_result_play_item);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_ROW, R.layout.search_result_settings_row);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_SLICE, R.layout.search_result_slice);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_ICON_ROW, R.layout.search_result_icon_row);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_PEOPLE, R.layout.search_result_people_item);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_THUMBNAIL, R.layout.search_result_thumbnail);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_SUGGEST, R.layout.search_result_suggest);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_WIDGET_LIVE, R.layout.search_result_widget_live);
        mViewTypeToLayoutMap.put(VIEW_TYPE_SEARCH_WIDGET_PREVIEW,
                R.layout.search_result_widget_preview);
    }

    @Override
    public void onBindView(AllAppsGridAdapter.ViewHolder holder, int position) {
        SearchAdapterItem item = (SearchAdapterItem) Launcher.getLauncher(mLauncher)
                .getAppsView().getApps().getAdapterItems().get(position);
        SearchTargetHandler
                payloadResultView =
                (SearchTargetHandler) holder.itemView;
        payloadResultView.applySearchTarget(item.getSearchTarget());
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
    public boolean onAdapterItemSelected(AllAppsGridAdapter.AdapterItem focusedItem) {
        if (focusedItem instanceof SearchTargetHandler) {
            SearchTarget searchTarget = ((SearchAdapterItem) focusedItem).getSearchTarget();
            SearchEventTracker.INSTANCE.get(mLauncher).quickSelect(searchTarget);
            return true;
        }
        return false;
    }
}
