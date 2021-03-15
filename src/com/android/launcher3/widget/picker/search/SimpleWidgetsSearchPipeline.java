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

import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.search.StringMatcherUtility.StringMatcher;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.model.WidgetsListSearchHeaderEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Implementation of {@link WidgetsPickerSearchPipeline} that performs search by prefix matching on
 * app names and widget labels.
 */
public final class SimpleWidgetsSearchPipeline implements WidgetsPickerSearchPipeline {

    private final List<WidgetsListBaseEntry> mAllEntries;

    public SimpleWidgetsSearchPipeline(List<WidgetsListBaseEntry> allEntries) {
        mAllEntries = allEntries;
    }

    @Override
    public void query(String input, Consumer<List<WidgetsListBaseEntry>> callback) {
        ArrayList<WidgetsListBaseEntry> results = new ArrayList<>();
        mAllEntries.stream().filter(entry -> entry instanceof WidgetsListHeaderEntry)
                .forEach(headerEntry -> {
                    List<WidgetItem> matchedWidgetItems = filterWidgetItems(
                            input, headerEntry.mPkgItem.title.toString(), headerEntry.mWidgets);
                    if (matchedWidgetItems.size() > 0) {
                        results.add(new WidgetsListSearchHeaderEntry(headerEntry.mPkgItem,
                                headerEntry.mTitleSectionName, matchedWidgetItems));
                        results.add(new WidgetsListContentEntry(headerEntry.mPkgItem,
                                headerEntry.mTitleSectionName, matchedWidgetItems));
                    }
                });
        callback.accept(results);
    }

    private List<WidgetItem> filterWidgetItems(String query, String packageTitle,
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
