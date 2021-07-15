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

/** An information holder for an app which has widgets or/and shortcuts. */
public final class WidgetsListHeaderEntry extends WidgetsListBaseEntry
        implements WidgetsListBaseEntry.Header<WidgetsListHeaderEntry> {

    public final int widgetsCount;
    public final int shortcutsCount;

    private final boolean mIsWidgetListShown;

    public WidgetsListHeaderEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items) {
        this(pkgItem, titleSectionName, items, /* isWidgetListShown= */ false);
    }

    private WidgetsListHeaderEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items, boolean isWidgetListShown) {
        super(pkgItem, titleSectionName, items);
        widgetsCount = (int) items.stream().filter(item -> item.widgetInfo != null).count();
        shortcutsCount = Math.max(0, items.size() - widgetsCount);
        mIsWidgetListShown = isWidgetListShown;
    }

    /** Returns {@code true} if the widgets list associated with this header is shown. */
    @Override
    public boolean isWidgetListShown() {
        return mIsWidgetListShown;
    }

    @Override
    public String toString() {
        return "Header:" + mPkgItem.packageName + ":" + mWidgets.size();
    }

    @Override
    @Rank
    public int getRank() {
        return RANK_WIDGETS_LIST_HEADER;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof WidgetsListHeaderEntry)) return false;
        WidgetsListHeaderEntry otherEntry = (WidgetsListHeaderEntry) obj;
        return mWidgets.equals(otherEntry.mWidgets) && mPkgItem.equals(otherEntry.mPkgItem)
                && mTitleSectionName.equals(otherEntry.mTitleSectionName)
                && mIsWidgetListShown == otherEntry.mIsWidgetListShown;
    }

    /** Returns a copy of this {@link WidgetsListHeaderEntry} with the widget list shown. */
    @Override
    public WidgetsListHeaderEntry withWidgetListShown() {
        if (mIsWidgetListShown) return this;
        return new WidgetsListHeaderEntry(
                mPkgItem,
                mTitleSectionName,
                mWidgets,
                /* isWidgetListShown= */ true);
    }
}
