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
public final class WidgetsListSearchHeaderEntry extends WidgetsListBaseEntry {

    private boolean mIsWidgetListShown = false;
    private boolean mHasEntryUpdated = false;

    public WidgetsListSearchHeaderEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items) {
        super(pkgItem, titleSectionName, items);
    }

    /** Sets if the widgets list associated with this header is shown. */
    public void setIsWidgetListShown(boolean isWidgetListShown) {
        if (mIsWidgetListShown != isWidgetListShown) {
            this.mIsWidgetListShown = isWidgetListShown;
            mHasEntryUpdated = true;
        } else {
            mHasEntryUpdated = false;
        }
    }

    /** Returns {@code true} if the widgets list associated with this header is shown. */
    public boolean isWidgetListShown() {
        return mIsWidgetListShown;
    }

    /** Returns {@code true} if this entry has been updated due to user interactions. */
    public boolean hasEntryUpdated() {
        return mHasEntryUpdated;
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
                && mTitleSectionName.equals(otherEntry.mTitleSectionName);
    }
}
