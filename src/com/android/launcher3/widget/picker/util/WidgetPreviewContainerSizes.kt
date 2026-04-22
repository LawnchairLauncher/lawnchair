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

package com.android.launcher3.widget.picker.util

/**
 * An ordered list of recommended sizes for the preview containers in handheld devices.
 *
 * Size of the preview container in which a widget's preview can be displayed.
 */
val HANDHELD_WIDGET_PREVIEW_SIZES: List<WidgetPreviewContainerSize> =
    listOf(
        WidgetPreviewContainerSize(spanX = 4, spanY = 3),
        WidgetPreviewContainerSize(spanX = 4, spanY = 2),
        WidgetPreviewContainerSize(spanX = 2, spanY = 3),
        WidgetPreviewContainerSize(spanX = 2, spanY = 2),
        WidgetPreviewContainerSize(spanX = 4, spanY = 1),
        WidgetPreviewContainerSize(spanX = 3, spanY = 1),
        WidgetPreviewContainerSize(spanX = 2, spanY = 1),
        WidgetPreviewContainerSize(spanX = 1, spanY = 1),
    )

/**
 * List of templates that can be used for selecting container sizes to show for featured widgets in
 * the widget picker for the handheld.
 */
val HANDHELD_FEATURED_CONTAINER_SIZES: List<List<WidgetPreviewContainerSize>> =
    listOf(
        listOf(
            WidgetPreviewContainerSize(1, 1),
            WidgetPreviewContainerSize(1, 1),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(4, 1),
            WidgetPreviewContainerSize(4, 2),
        ), listOf(
            WidgetPreviewContainerSize(2, 1),
            WidgetPreviewContainerSize(2, 1),
            WidgetPreviewContainerSize(4, 1),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(4, 2),
        ),
        listOf(
            WidgetPreviewContainerSize(4, 1),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(4, 1),
            WidgetPreviewContainerSize(4, 3),
        )
    )

/**
 * An ordered list of recommended sizes for the preview containers in tablet devices (with larger
 * grids).
 *
 * Size of the preview container in which a widget's preview can be displayed (by scaling the
 * preview if necessary).
 */
val TABLET_WIDGET_PREVIEW_SIZES: List<WidgetPreviewContainerSize> =
    listOf(
        WidgetPreviewContainerSize(spanX = 3, spanY = 4),
        WidgetPreviewContainerSize(spanX = 3, spanY = 3),
        WidgetPreviewContainerSize(spanX = 3, spanY = 2),
        WidgetPreviewContainerSize(spanX = 2, spanY = 3),
        WidgetPreviewContainerSize(spanX = 2, spanY = 2),
        WidgetPreviewContainerSize(spanX = 3, spanY = 1),
        WidgetPreviewContainerSize(spanX = 2, spanY = 1),
        WidgetPreviewContainerSize(spanX = 1, spanY = 1),
    )

/**
 * List of templates that can be used for selecting container sizes to show for featured widgets in
 * the widget picker for tablets.
 */
val TABLET_FEATURED_CONTAINER_SIZES: List<List<WidgetPreviewContainerSize>> =
    listOf(
        listOf(
            WidgetPreviewContainerSize(1, 1),
            WidgetPreviewContainerSize(1, 1),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(3, 1),
            WidgetPreviewContainerSize(3, 2),
        ), listOf(
            WidgetPreviewContainerSize(2, 1),
            WidgetPreviewContainerSize(2, 1),
            WidgetPreviewContainerSize(3, 1),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(3, 2),
        ),
        listOf(
            WidgetPreviewContainerSize(3, 1),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(2, 2),
            WidgetPreviewContainerSize(3, 1),
            WidgetPreviewContainerSize(3, 3),
        )
    )
