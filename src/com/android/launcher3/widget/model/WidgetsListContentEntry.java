/*
 * Copyright (C) 2016 The Android Open Source Project
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

import androidx.annotation.Px;

import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;

import java.util.List;

/**
 * Holder class to store all the information related to a list of widgets from the same app which is
 * shown in the {@link com.android.launcher3.widget.picker.WidgetsFullSheet}.
 */
public final class WidgetsListContentEntry extends WidgetsListBaseEntry {

    @Px private final int mMaxSpanSize;

    /**
     * Constructor for {@link WidgetsListContentEntry}.
     *
     * @param pkgItem package info associated with the entry
     * @param titleSectionName title section name associated with the entry.
     * @param items list of widgets for the package.
     */
    public WidgetsListContentEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items) {
        this(pkgItem, titleSectionName, items, /* maxSpanSize= */ 0);
    }

    /**
     * Constructor for {@link WidgetsListContentEntry}.
     *
     * @param pkgItem package info associated with the entry
     * @param titleSectionName title section name associated with the entry.
     * @param items list of widgets for the package.
     * @param maxSpanSize the max horizontal span in pixels that is allowed for grouping more
     *                           than one widgets in a table row.
     */
    public WidgetsListContentEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items, @Px int maxSpanSize) {
        super(pkgItem, titleSectionName, items);
        mMaxSpanSize = maxSpanSize;
    }

    @Override
    public String toString() {
        return "Content:" + mPkgItem.packageName + ":" + mWidgets.size() + " maxSpanSize: "
                + mMaxSpanSize;
    }

    /**
     * Returns a copy of this {@link WidgetsListContentEntry} with updated {@code maxSpanSize}.
     *
     * @param maxSpanSize the maximum horizontal span in pixels that is allowed for grouping
     *                           more than one widgets in a table row.
     */
    public WidgetsListContentEntry withMaxSpanSize(@Px int maxSpanSize) {
        if (mMaxSpanSize == maxSpanSize) return this;
        return new WidgetsListContentEntry(
                mPkgItem,
                mTitleSectionName,
                mWidgets,
                /* maxSpanSize= */ maxSpanSize);
    }

    /**
     * Returns the max horizontal span size in pixels that is allowed for grouping more than one
     * widget in a table row.
     */
    @Px
    public int getMaxSpanSize() {
        return mMaxSpanSize;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof WidgetsListContentEntry)) return false;
        WidgetsListContentEntry otherEntry = (WidgetsListContentEntry) obj;
        return mWidgets.equals(otherEntry.mWidgets) && mPkgItem.equals(otherEntry.mPkgItem)
                && mTitleSectionName.equals(otherEntry.mTitleSectionName)
                && mMaxSpanSize == otherEntry.mMaxSpanSize;
    }
}
