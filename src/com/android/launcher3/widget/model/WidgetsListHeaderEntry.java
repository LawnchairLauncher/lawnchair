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

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.util.PluralMessageFormat;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** An information holder for an app which has widgets or/and shortcuts. */
public final class WidgetsListHeaderEntry extends WidgetsListBaseEntry {

    private static final BiFunction<Context, WidgetsListHeaderEntry, String> SUBTITLE_SEARCH =
            (context, entry) -> entry.mWidgets.stream()
                    .map(item -> item.label).sorted().collect(Collectors.joining(", "));

    private static final BiFunction<Context, WidgetsListHeaderEntry, String> SUBTITLE_DEFAULT =
            (context, entry) -> {
                List<WidgetItem> items = entry.mWidgets;
                int wc = (int) items.stream().filter(item -> item.widgetInfo != null).count();
                int sc = Math.max(0, items.size() - wc);

                Resources resources = context.getResources();
                if (wc == 0 && sc == 0) {
                    return null;
                }

                String subtitle;
                if (wc > 0 && sc > 0) {
                    String widgetsCount = PluralMessageFormat.getIcuPluralString(context,
                            R.string.widgets_count, wc);
                    String shortcutsCount = PluralMessageFormat.getIcuPluralString(context,
                            R.string.shortcuts_count, sc);
                    subtitle = resources.getString(R.string.widgets_and_shortcuts_count,
                            widgetsCount, shortcutsCount);
                } else if (wc > 0) {
                    subtitle = PluralMessageFormat.getIcuPluralString(context,
                            R.string.widgets_count, wc);
                } else {
                    subtitle = PluralMessageFormat.getIcuPluralString(context,
                            R.string.shortcuts_count, sc);
                }
                return subtitle;
            };

    private final boolean mIsWidgetListShown;
    private final boolean mIsSearchEntry;

    private WidgetsListHeaderEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items, boolean isSearchEntry, boolean isWidgetListShown) {
        super(pkgItem, titleSectionName, items);
        mIsSearchEntry = isSearchEntry;
        mIsWidgetListShown = isWidgetListShown;
    }

    /** Returns {@code true} if the widgets list associated with this header is shown. */
    public boolean isWidgetListShown() {
        return mIsWidgetListShown;
    }

    @Override
    public String toString() {
        return "Header:" + mPkgItem.packageName + ":" + mWidgets.size();
    }

    public boolean isSearchEntry() {
        return mIsSearchEntry;
    }

    @Nullable
    public String getSubtitle(Context context) {
        return mIsSearchEntry
                ? SUBTITLE_SEARCH.apply(context, this) : SUBTITLE_DEFAULT.apply(context, this);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof WidgetsListHeaderEntry)) return false;
        WidgetsListHeaderEntry otherEntry = (WidgetsListHeaderEntry) obj;
        return mWidgets.equals(otherEntry.mWidgets) && mPkgItem.equals(otherEntry.mPkgItem)
                && mTitleSectionName.equals(otherEntry.mTitleSectionName)
                && mIsWidgetListShown == otherEntry.mIsWidgetListShown
                && mIsSearchEntry == otherEntry.mIsSearchEntry;
    }

    /** Returns a copy of this {@link WidgetsListHeaderEntry} with the widget list shown. */
    public WidgetsListHeaderEntry withWidgetListShown() {
        if (mIsWidgetListShown) return this;
        return new WidgetsListHeaderEntry(
                mPkgItem,
                mTitleSectionName,
                mWidgets,
                mIsSearchEntry,
                /* isWidgetListShown= */ true);
    }

    public static WidgetsListHeaderEntry create(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items) {
        return new WidgetsListHeaderEntry(
                pkgItem,
                titleSectionName,
                items,
                /* forSearch */ false,
                /* isWidgetListShown= */ false);
    }

    public static WidgetsListHeaderEntry createForSearch(PackageItemInfo pkgItem,
            String titleSectionName, List<WidgetItem> items) {
        return new WidgetsListHeaderEntry(
                pkgItem,
                titleSectionName,
                items,
                /* forSearch */ true,
                /* isWidgetListShown= */ false);
    }
}
