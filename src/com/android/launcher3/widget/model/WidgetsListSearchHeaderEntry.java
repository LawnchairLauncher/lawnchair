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
package com.android.launcher3.widget.model;

import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;

import java.util.List;

/** An information holder for an app which has widgets or/and shortcuts, to be shown in search. */
public final class WidgetsListSearchHeaderEntry extends WidgetsListBaseEntry
        implements WidgetsListBaseEntry.Header<WidgetsListSearchHeaderEntry> {

    private final boolean mIsWidgetListShown;

    public WidgetsListSearchHeaderEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items) {
        this(pkgItem, titleSectionName, items, /* isWidgetListShown= */ false);
    }

    private WidgetsListSearchHeaderEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items, boolean isWidgetListShown) {
        super(pkgItem, titleSectionName, items);
        mIsWidgetListShown = isWidgetListShown;
    }

    /** Returns {@code true} if the widgets list associated with this header is shown. */
    @Override
    public boolean isWidgetListShown() {
        return mIsWidgetListShown;
    }

    @Override
    public String toString() {
        return "SearchHeader:" + mPkgItem.packageName + ":" + mWidgets.size();
    }

    @Override
    @Rank
    public int getRank() {
        return RANK_WIDGETS_LIST_SEARCH_HEADER;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof WidgetsListSearchHeaderEntry)) return false;
        WidgetsListSearchHeaderEntry otherEntry = (WidgetsListSearchHeaderEntry) obj;
        return mWidgets.equals(otherEntry.mWidgets) && mPkgItem.equals(otherEntry.mPkgItem)
                && mTitleSectionName.equals(otherEntry.mTitleSectionName)
                && mIsWidgetListShown == otherEntry.mIsWidgetListShown;
    }

    /** Returns a copy of this {@link WidgetsListSearchHeaderEntry} with the widget list shown. */
    @Override
    public WidgetsListSearchHeaderEntry withWidgetListShown() {
        if (mIsWidgetListShown) return this;
        return new WidgetsListSearchHeaderEntry(
                mPkgItem,
                mTitleSectionName,
                mWidgets,
                /* isWidgetListShown= */ true);
    }
}
