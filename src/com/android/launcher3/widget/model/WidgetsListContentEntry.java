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

import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.WidgetItemComparator;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Holder class to store all the information related to a list of widgets from the same app which is
 * shown in the {@link com.android.launcher3.widget.picker.WidgetsFullSheet}.
 */
public final class WidgetsListContentEntry extends WidgetsListBaseEntry {

    public final List<WidgetItem> mWidgets;

    public WidgetsListContentEntry(PackageItemInfo pkgItem, String titleSectionName,
            List<WidgetItem> items) {
        super(pkgItem, titleSectionName);
        this.mWidgets =
                items.stream().sorted(new WidgetItemComparator()).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return mPkgItem.packageName + ":" + mWidgets.size();
    }

    @Override
    @Rank
    public int getRank() {
        return RANK_WIDGETS_LIST_CONTENT;
    }
}
