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

package com.android.launcher3.widget.picker.search;

import static com.android.launcher3.search.StringMatcherUtility.matches;

import android.os.Handler;

import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.search.StringMatcherUtility.StringMatcher;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.picker.search.WidgetsSearchBar.WidgetsSearchDataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SearchAlgorithm} that posts a task to query on the main thread.
 */
public final class SimpleWidgetsSearchAlgorithm implements SearchAlgorithm<WidgetsListBaseEntry> {

    private final Handler mResultHandler;
    private final WidgetsSearchDataProvider mDataProvider;

    public SimpleWidgetsSearchAlgorithm(WidgetsSearchDataProvider dataProvider) {
        mResultHandler = new Handler();
        mDataProvider = dataProvider;
    }

    @Override
    public void doSearch(String query, SearchCallback<WidgetsListBaseEntry> callback) {
        ArrayList<WidgetsListBaseEntry> result = getFilteredWidgets(mDataProvider, query);
        mResultHandler.post(() -> callback.onSearchResult(query, result));
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(/*token= */null);
        }
    }

    /**
     * Returns entries for all matched widgets
     */
    public static ArrayList<WidgetsListBaseEntry> getFilteredWidgets(
            WidgetsSearchDataProvider dataProvider, String input) {
        ArrayList<WidgetsListBaseEntry> results = new ArrayList<>();
        dataProvider.getWidgets().stream()
                .filter(entry -> entry instanceof WidgetsListHeaderEntry)
                .forEach(headerEntry -> {
                    List<WidgetItem> matchedWidgetItems = filterWidgetItems(
                            input, headerEntry.mPkgItem.title.toString(), headerEntry.mWidgets);
                    if (matchedWidgetItems.size() > 0) {
                        results.add(WidgetsListHeaderEntry.createForSearch(headerEntry.mPkgItem,
                                headerEntry.mTitleSectionName, matchedWidgetItems));
                        results.add(new WidgetsListContentEntry(headerEntry.mPkgItem,
                                headerEntry.mTitleSectionName, matchedWidgetItems));
                    }
                });
        return results;
    }

    private static List<WidgetItem> filterWidgetItems(String query, String packageTitle,
            List<WidgetItem> items) {
        StringMatcher matcher = StringMatcher.getInstance();
        if (matches(query, packageTitle, matcher)) {
            return items;
        }
        return items.stream()
                .filter(item -> matches(query, item.label, matcher))
                .collect(Collectors.toList());
    }
}
