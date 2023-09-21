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
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.WidgetItemComparator;

import java.util.List;
import java.util.stream.Collectors;

/** Holder class to store the package information of an entry shown in the widgets list. */
public abstract class WidgetsListBaseEntry {
    public final PackageItemInfo mPkgItem;

    /**
     * Character that is used as a section name for the {@link ItemInfo#title}.
     * (e.g., "G" will be stored if title is "Google")
     */
    public final String mTitleSectionName;

    public final List<WidgetItem> mWidgets;

    public WidgetsListBaseEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items) {
        mPkgItem = pkgItem;
        mTitleSectionName = titleSectionName;
        this.mWidgets =
                items.stream().sorted(new WidgetItemComparator()).collect(Collectors.toList());
    }
}
