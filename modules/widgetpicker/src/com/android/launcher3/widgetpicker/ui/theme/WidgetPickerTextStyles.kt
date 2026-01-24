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

package com.android.launcher3.widgetpicker.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * [TextStyle]s for specific text elements within the widget picker.
 *
 * @param sheetTitle style for the title of entire bottom sheet
 * @param sheetDescription style for the long description displayed below the [sheetTitle].
 * @param selectedListHeaderTitle style for the label of widget app that's currently expanded /
 *   selected in the list of widget apps.
 * @param unSelectedListHeaderTitle style for the label of widget's app that's currently collapsed /
 *   not-selected in the list of widget apps.
 * @param selectedListHeaderSubTitle style for the sub-title text shown below the
 *   [selectedListHeaderTitle] for the widget app that's currently expanded / selected in the list
 *   of widget apps.
 * @param unSelectedListHeaderSubTitle style for the sub-title text shown below the
 *   [unSelectedListHeaderTitle] for the widget app that's collapsed / un-selected in the list of
 *   widget apps.
 * @param noWidgetsErrorText style for the text indicating that there are no widgets available.
 * @param widgetLabel style for the text showing widget's label below its preview.
 * @param widgetSpanText style for the text showing the dimensions of the widget.
 * @param widgetDescription style for the text showing the long description of the widget.
 * @param addWidgetButtonLabel style for the "add" text on the button shown to add the widget.
 * @param toolbarTabLabel style for the text shown on each tab within the floating toolbar (e.g.
 *   personal, work, browse, featured).
 * @param searchBarPlaceholderText style of the text shown as placeholder when user hasn't typed
 *   anything in searchbar.
 * @param searchBarText style of the text that user types in the search bar.
 */
data class WidgetPickerTextStyles(
    // Titled bottom sheet
    val sheetTitle: TextStyle,
    val sheetDescription: TextStyle,

    // Expandable list
    val selectedListHeaderTitle: TextStyle,
    val unSelectedListHeaderTitle: TextStyle,
    val selectedListHeaderSubTitle: TextStyle,
    val unSelectedListHeaderSubTitle: TextStyle,

    // No widgets error
    val noWidgetsErrorText: TextStyle,

    // Widget details
    val widgetLabel: TextStyle,
    val widgetSpanText: TextStyle,
    val widgetDescription: TextStyle,
    val addWidgetButtonLabel: TextStyle,
    val toolbarTabLabel: TextStyle,

    // Search bar
    val searchBarPlaceholderText: TextStyle,
    val searchBarText: TextStyle,
)

/**
 * Composition local for [WidgetPickerTextStyles].
 *
 * This used by the [WidgetPickerTheme] to access [WidgetPickerTextStyles] within a composable
 * that's part of the widget picker. It enables the host to map the widget picker text styles
 * different values than the default.
 */
val LocalWidgetPickerTextStyles =
    staticCompositionLocalOf<WidgetPickerTextStyles> {
        throw IllegalStateException(
            "No WidgetPickerTextStyles configured. Make sure to use WidgetPickerTheme " +
                "in your top level Composable."
        )
    }

/**
 * Default [TextStyle]s that can either be used as-is in host theme or used a reference when
 * defining your own [LocalWidgetPickerTextStyles].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun defaultWidgetPickerTextStyles() =
    WidgetPickerTextStyles(
        // Titled bottom sheet
        sheetTitle = MaterialTheme.typography.headlineSmallEmphasized,
        sheetDescription = MaterialTheme.typography.bodyMedium,

        // Expandable list
        selectedListHeaderTitle =
            MaterialTheme.typography.titleMediumEmphasized.copy(fontWeight = FontWeight.Medium),
        unSelectedListHeaderTitle =
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
        selectedListHeaderSubTitle =
            MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        unSelectedListHeaderSubTitle =
            MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),

        // No widgets error
        noWidgetsErrorText = MaterialTheme.typography.bodyLarge,

        // Widget details
        widgetLabel = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        widgetSpanText = MaterialTheme.typography.labelLarge,
        widgetDescription = MaterialTheme.typography.labelMedium,
        addWidgetButtonLabel = MaterialTheme.typography.labelLarge,

        // Floating toolbar
        toolbarTabLabel = MaterialTheme.typography.labelLarge,

        // Search bar
        searchBarPlaceholderText =
            MaterialTheme.typography.bodyLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
        searchBarText =
            MaterialTheme.typography.bodyLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
    )
