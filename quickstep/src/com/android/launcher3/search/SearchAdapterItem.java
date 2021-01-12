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

import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_ICON;
import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_ICON_ROW;
import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_PEOPLE;
import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_ROW;
import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_ROW_WITH_BUTTON;
import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_SLICE;
import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_SUGGEST;
import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_THUMBNAIL;
import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_WIDGET_LIVE;
import static com.android.launcher3.search.DeviceSearchAdapterProvider.VIEW_TYPE_SEARCH_WIDGET_PREVIEW;

import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.systemui.plugins.shared.SearchTarget;

/**
 * Extension of AdapterItem that contains an extra payload specific to item
 */
public class SearchAdapterItem extends AllAppsGridAdapter.AdapterItem {
    private SearchTarget mSearchTarget;


    private static final int AVAILABLE_FOR_ACCESSIBILITY = VIEW_TYPE_SEARCH_ROW_WITH_BUTTON
            | VIEW_TYPE_SEARCH_SLICE | VIEW_TYPE_SEARCH_ROW | VIEW_TYPE_SEARCH_PEOPLE
            | VIEW_TYPE_SEARCH_THUMBNAIL | VIEW_TYPE_SEARCH_ICON_ROW | VIEW_TYPE_SEARCH_ICON
            | VIEW_TYPE_SEARCH_WIDGET_PREVIEW | VIEW_TYPE_SEARCH_WIDGET_LIVE
            | VIEW_TYPE_SEARCH_SUGGEST;

    public SearchAdapterItem(SearchTarget searchTarget, int type) {
        mSearchTarget = searchTarget;
        viewType = type;
    }

    public SearchTarget getSearchTarget() {
        return mSearchTarget;
    }

    @Override
    protected boolean isCountedForAccessibility() {
        return (AVAILABLE_FOR_ACCESSIBILITY & viewType) == viewType;
    }
}
