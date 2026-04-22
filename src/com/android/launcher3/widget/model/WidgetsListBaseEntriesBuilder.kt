/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.widget.model

import android.content.Context
import com.android.launcher3.compat.AlphabeticIndexCompat
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.PackageItemInfo
import java.util.function.Predicate

/**
 * A helper class that builds the list of [WidgetsListBaseEntry]s used by the UI to display widgets.
 */
class WidgetsListBaseEntriesBuilder(val context: Context) {

    /** Builds the widgets list entries in a format understandable by the widget picking UI. */
    @JvmOverloads
    fun build(
        widgetsByPackageItem: Map<PackageItemInfo, List<WidgetItem>>,
        widgetFilter: Predicate<WidgetItem> = Predicate<WidgetItem> { true },
    ): List<WidgetsListBaseEntry> {
        val indexer = AlphabeticIndexCompat(context)

        return buildList {
            for ((pkgItem, widgetItems) in widgetsByPackageItem.entries) {
                val filteredWidgetItems = widgetItems.filter { widgetFilter.test(it) }
                if (filteredWidgetItems.isNotEmpty()) {
                    // Enables fast scroll popup to show right characters in all locales.
                    val sectionName = pkgItem.title?.let { indexer.computeSectionName(it) } ?: ""

                    add(WidgetsListHeaderEntry.create(pkgItem, sectionName, filteredWidgetItems))
                    add(WidgetsListContentEntry(pkgItem, sectionName, filteredWidgetItems))
                }
            }
        }
    }
}
