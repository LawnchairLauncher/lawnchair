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

    @Nullable
    private static String buildWidgetsCountString(Context context, int wc, int sc) {
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
    }

    private final boolean mIsWidgetListShown;
    /** Selected widgets displayed */
    private final int mVisibleWidgetsCount;
    private final boolean mIsSearchEntry;

    private WidgetsListHeaderEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items, int visibleWidgetsCount,
            boolean isSearchEntry, boolean isWidgetListShown) {
        super(pkgItem, titleSectionName, items);
        mVisibleWidgetsCount = visibleWidgetsCount;
        mIsSearchEntry = isSearchEntry;
        mIsWidgetListShown = isWidgetListShown;
    }

    private WidgetsListHeaderEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items, boolean isSearchEntry, boolean isWidgetListShown) {
        super(pkgItem, titleSectionName, items);
        mVisibleWidgetsCount = (int) items.stream().filter(w -> w.widgetInfo != null).count();
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
        if (mIsSearchEntry) {
            return SUBTITLE_SEARCH.apply(context, this);
        } else {
            int shortcutsCount = Math.max(0,
                    (int) mWidgets.stream().filter(WidgetItem::isShortcut).count());
            return buildWidgetsCountString(context, mVisibleWidgetsCount, shortcutsCount);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof WidgetsListHeaderEntry)) return false;
        WidgetsListHeaderEntry otherEntry = (WidgetsListHeaderEntry) obj;
        return mWidgets.equals(otherEntry.mWidgets) && mPkgItem.equals(otherEntry.mPkgItem)
                && mTitleSectionName.equals(otherEntry.mTitleSectionName)
                && mIsWidgetListShown == otherEntry.mIsWidgetListShown
                && mVisibleWidgetsCount == otherEntry.mVisibleWidgetsCount
                && mIsSearchEntry == otherEntry.mIsSearchEntry;
    }

    /** Returns a copy of this {@link WidgetsListHeaderEntry} with the widget list shown. */
    public WidgetsListHeaderEntry withWidgetListShown() {
        if (mIsWidgetListShown) return this;
        return new WidgetsListHeaderEntry(
                mPkgItem,
                mTitleSectionName,
                mWidgets,
                mVisibleWidgetsCount,
                mIsSearchEntry,
                /* isWidgetListShown= */ true);
    }

    public static WidgetsListHeaderEntry create(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items) {
        return new WidgetsListHeaderEntry(
                pkgItem,
                titleSectionName,
                items,
                /* isSearchEntry= */ false,
                /* isWidgetListShown= */ false);
    }

    /**
     * Creates a widget list holder for an header ("app" / "suggestions") which has widgets or/and
     * shortcuts.
     *
     * @param pkgItem             package item info for the header section
     * @param titleSectionName    title string for the header
     * @param items               all items for the given header
     * @param visibleWidgetsCount widgets count when only selected widgets are shown due to
     *                            limited space.
     */
    public static WidgetsListHeaderEntry create(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items, int visibleWidgetsCount) {
        return new WidgetsListHeaderEntry(
                pkgItem,
                titleSectionName,
                items,
                visibleWidgetsCount,
                /* isSearchEntry= */ false,
                /* isWidgetListShown= */ false);
    }

    public static WidgetsListHeaderEntry createForSearch(PackageItemInfo pkgItem,
            String titleSectionName, List<WidgetItem> items) {
        return new WidgetsListHeaderEntry(
                pkgItem,
                titleSectionName,
                items,
                /* isSearchEntry */ true,
                /* isWidgetListShown= */ false);
    }
}
