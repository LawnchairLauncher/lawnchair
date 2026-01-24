/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.widgetpicker.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId

/**
 * Information about the widget app transformed for displaying as a expandable section in UI.
 *
 * @param id unique id for the app section
 * @param title title for the widget
 * @param widgetSizeGroups groups of similar sized widgets that can be displayed together
 * @param widgetsCount total number of widgets in the app
 */
@Stable
@Immutable
data class DisplayableWidgetApp(
    val id: WidgetAppId,
    val title: CharSequence?,
    val widgetSizeGroups: List<WidgetSizeGroup>,
    val widgetsCount: Int,
) {
    companion object {
        /**
         * Helper function to create a [DisplayableWidgetApp] from a [WidgetApp].
         * Converts the list of widgets in the app to a list of [WidgetSizeGroup]s.
         */
        fun fromWidgetApp(widgetApp: WidgetApp): DisplayableWidgetApp =
            DisplayableWidgetApp(
                id = widgetApp.id,
                title = widgetApp.title,
                widgetSizeGroups = widgetApp.widgets.groupBy {
                    Pair(it.sizeInfo.containerWidthPx, it.sizeInfo.containerHeightPx)
                }.map { (containerSize, value) ->
                    WidgetSizeGroup(
                        previewContainerWidthPx = containerSize.first,
                        previewContainerHeightPx = containerSize.second,
                        widgets = value
                    )
                },
                widgetsCount = widgetApp.widgets.size,
            )

        fun List<DisplayableWidgetApp>.getWidgetIdsForApp(appId: WidgetAppId) =
            find { it.id == appId }?.widgetSizeGroups?.flatMap { group ->
                group.widgets.map { it.id }
            } ?: listOf()
    }
}
