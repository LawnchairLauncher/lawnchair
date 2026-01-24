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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color tokens corresponding to specific UI elements in widget picker.
 *
 * @param sheetBackground background color of the widget picker bottom sheet.
 * @param dragHandle color of the drag handle shown in the widget picker bottom sheet.
 * @param sheetTitle color of the title of entire bottom sheet
 * @param sheetDescription color of long description displayed below the title of the bottom sheet.
 * @param expandCollapseIndicatorIcon color of the expand collapse button shown in the expandable
 *   list of apps in the single pane view of the widget picker.
 * @param expandCollapseIndicatorBackground background color shown below the
 *   [expandCollapseIndicatorIcon]
 * @param expandableListItemsBackground background color of the expandable list of widget apps shown
 *   in the single pane view of widget picker.
 * @param selectedListHeaderBackground background color of a widget app header that's selected (in
 *   left pane of two pane picker view).
 * @param unselectedListHeaderBackground background color of a widget app headers that's not
 *   selected (in left pane of two pane picker view).
 * @param widgetsContainerBackground background color of the container that displays widgets.
 * @param featuredHeaderLeadingIconBackground background color of the leading icon of the featured
 *   widgets header (in left pane of two pane picker view).
 * @param featuredHeaderLeadingIcon color of the leading icon of the featured widgets header (in
 *   left pane of two pane picker view).
 * @param listHeaderTitle color of the title text of widget app headers (in left pane of two pane
 *   picker view).
 * @param listHeaderSubTitle color of the subtitle text of widget app headers (in left pane of two
 *   pane picker view).
 * @param noWidgetsErrorText color of the message shown when no widgets are available or matched.
 * @param placeholderAppIcon base color of the placeholder (without alpha) shown while app icon
 *   isn't loaded yet.
 * @param widgetLabel color of the text showing widget's label below its preview.
 * @param widgetSpanText color of text showing the dimensions of the widget.
 * @param widgetDescription color of the text showing the long description of the widget.
 * @param addButtonContent color of the text / icon shown within the add button shown for a widget.
 * @param addButtonBackground background color of the add button shown for a widget.
 * @param widgetPlaceholderBackground background color of the placeholder shown while a widget's
 *   preview isn't loaded yet.
 * @param widgetPlaceholderContent color of the content (e.g. loading spinner) shown over the
 *   [widgetPlaceholderBackground]
 * @param toolbarBackground color of the background of the floating toolbar that shows the tabs.
 * @param toolbarTabSelectedBackground background color of a selected tab (personal / work / browse
 *   / featured) in a floating toolbar.
 * @param toolbarTabUnSelectedBackground background color of an unselected tab (personal / work /
 *   browse / featured) in a floating toolbar.
 * @param toolbarTabContent color of the text and icons in a tab within a floating toolbar.
 * @param searchBarBackground background color of the search bar.
 * @param searchBarPlaceholderText color of the placeholder text shown in the search bar.
 * @param searchBarText color of the text that use types in the search bar.
 * @param searchBarSearchIcon color of the search icon shown leading the text in search bar.
 * @param searchBarBackButtonIcon color of the icon in the back button shown in place of
 *   [searchBarSearchIcon] when user is in search mode.
 * @param searchBarClearButtonIcon color of close icon button shown trailing in the search bar when
 *   user has typed some text.
 * @param searchBarCursor color of the cursor in the search bar
 */
data class WidgetPickerColors(
    // Titled bottom sheet
    val sheetBackground: Color,
    val dragHandle: Color,
    val sheetTitle: Color,
    val sheetDescription: Color,

    // Expand collapse list
    val expandableListItemsBackground: Color,
    val selectedListHeaderBackground: Color,
    val unselectedListHeaderBackground: Color,
    val featuredHeaderLeadingIconBackground: Color,
    val featuredHeaderLeadingIcon: Color,
    val listHeaderTitle: Color,
    val listHeaderSubTitle: Color,
    // trailing indicator
    val expandCollapseIndicatorIcon: Color,
    val expandCollapseIndicatorBackground: Color,

    // Error message
    val noWidgetsErrorText: Color,

    // Widgets container
    val widgetsContainerBackground: Color,

    // App icon
    val placeholderAppIcon: Color,

    // Widget details
    val widgetLabel: Color,
    val widgetSpanText: Color,
    val widgetDescription: Color,
    val addButtonContent: Color,
    val addButtonBackground: Color,

    // Widget preview
    val widgetPlaceholderBackground: Color,
    val widgetPlaceholderContent: Color,

    // Floating toolbar
    val toolbarBackground: Color,
    val toolbarTabSelectedBackground: Color,
    val toolbarTabUnSelectedBackground: Color,
    val toolbarTabContent: Color,

    // Search bar
    val searchBarBackground: Color,
    val searchBarPlaceholderText: Color,
    val searchBarText: Color,
    val searchBarSearchIcon: Color,
    val searchBarClearButtonIcon: Color,
    val searchBarBackButtonIcon: Color,
    val searchBarCursor: Color,
)

/**
 * Composition local for [WidgetPickerColors].
 *
 * This is used by the [WidgetPickerTheme] to access [WidgetPickerColors] within a composable that's
 * part of the widget picker. It enables the host to map the widget picker colors different system
 * tokens than default.
 */
val LocalWidgetPickerColors =
    staticCompositionLocalOf<WidgetPickerColors> {
        throw IllegalStateException(
            "No WidgetPickerColors configured. Make sure to use WidgetPickerTheme in your top level " +
                "Composable."
        )
    }

/**
 * Default colors that can either be used as is in host theme or used a reference to define your own
 * [LocalWidgetPickerColors].
 */
@Composable
fun defaultWidgetPickerColors() =
    WidgetPickerColors(
        // Titled bottom sheet
        sheetBackground = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = MaterialTheme.colorScheme.outline,
        sheetTitle = MaterialTheme.colorScheme.onSurface,
        sheetDescription = MaterialTheme.colorScheme.onSurfaceVariant,

        // Leading icon toolbar tab
        toolbarBackground = MaterialTheme.colorScheme.surfaceBright,
        toolbarTabSelectedBackground = MaterialTheme.colorScheme.secondaryContainer,
        toolbarTabUnSelectedBackground = Color.Transparent,
        toolbarTabContent = MaterialTheme.colorScheme.onSecondaryContainer,

        // Expand collapse list
        expandableListItemsBackground = MaterialTheme.colorScheme.surfaceBright,
        selectedListHeaderBackground = MaterialTheme.colorScheme.secondaryContainer,
        unselectedListHeaderBackground = Color.Transparent,
        widgetsContainerBackground = MaterialTheme.colorScheme.surfaceBright,
        featuredHeaderLeadingIconBackground = MaterialTheme.colorScheme.surfaceBright,
        featuredHeaderLeadingIcon = MaterialTheme.colorScheme.primary,
        listHeaderTitle = MaterialTheme.colorScheme.onSurface,
        listHeaderSubTitle = MaterialTheme.colorScheme.onSurfaceVariant,
        // trailing indicator
        expandCollapseIndicatorIcon = MaterialTheme.colorScheme.onSecondaryContainer,
        expandCollapseIndicatorBackground = MaterialTheme.colorScheme.secondaryContainer,

        // Error text
        noWidgetsErrorText = MaterialTheme.colorScheme.onSurface,

        // App icon
        placeholderAppIcon = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),

        // Widget details
        widgetLabel = MaterialTheme.colorScheme.onSurface,
        widgetSpanText = MaterialTheme.colorScheme.onSurfaceVariant,
        widgetDescription = MaterialTheme.colorScheme.onSurfaceVariant,
        addButtonBackground = MaterialTheme.colorScheme.primary,
        addButtonContent = MaterialTheme.colorScheme.onPrimary,

        // Widget preview
        widgetPlaceholderBackground = MaterialTheme.colorScheme.secondaryContainer,
        widgetPlaceholderContent = MaterialTheme.colorScheme.onSecondaryContainer,

        // Search bar
        searchBarBackground = MaterialTheme.colorScheme.surfaceBright,
        searchBarPlaceholderText = MaterialTheme.colorScheme.onSurfaceVariant,
        searchBarText = MaterialTheme.colorScheme.onSurfaceVariant,
        searchBarSearchIcon = MaterialTheme.colorScheme.onSurfaceVariant,
        searchBarBackButtonIcon = MaterialTheme.colorScheme.onSurfaceVariant,
        searchBarClearButtonIcon = MaterialTheme.colorScheme.onSurfaceVariant,
        searchBarCursor = MaterialTheme.colorScheme.primary,
    )
