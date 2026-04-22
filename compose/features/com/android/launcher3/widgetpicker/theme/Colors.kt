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

package com.android.launcher3.widgetpicker.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.android.launcher3.R
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerColors

/**
 * An adapter that maps the resource tokens for widget picker dark colors to the
 * [WidgetPickerColors] accepted by the widget picker module.
 */
@Composable
fun darkWidgetPickerColors() =
    WidgetPickerColors(
        // Bottom Sheet
        sheetBackground = colorResource(R.color.widget_picker_primary_surface_color_dark),
        dragHandle = colorResource(R.color.widget_picker_collapse_handle_color_dark),
        sheetTitle = colorResource(R.color.widget_picker_title_color_dark),
        sheetDescription = colorResource(R.color.widget_picker_description_color_dark),

        // Expand collapse list
        expandCollapseIndicatorIcon =
            colorResource(R.color.widget_picker_expand_icon_button_color_dark),
        expandCollapseIndicatorBackground =
            colorResource(R.color.widget_picker_expand_icon_button_background_dark),
        expandableListItemsBackground =
            colorResource(R.color.widget_picker_expandable_list_items_background_dark),

        // List header
        selectedListHeaderBackground =
            colorResource(R.color.widget_picker_clickable_list_header_background_dark),
        unselectedListHeaderBackground = Color.Transparent,
        featuredHeaderLeadingIconBackground =
            colorResource(R.color.widget_picker_featured_header_icon_background_dark),
        featuredHeaderLeadingIcon =
            colorResource(R.color.widget_picker_featured_header_icon_color_dark),
        listHeaderTitle = colorResource(R.color.widget_picker_header_app_title_color_dark),
        listHeaderSubTitle = colorResource(R.color.widget_picker_header_app_subtitle_color_dark),

        // Error message
        noWidgetsErrorText = colorResource(R.color.widget_picker_no_widget_error_color_dark),

        // Widgets container
        widgetsContainerBackground =
            colorResource(R.color.widget_picker_widgets_container_background_dark),

        // App icon
        placeholderAppIcon = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),

        // Widget details
        widgetLabel = colorResource(R.color.widget_cell_title_color_dark),
        widgetSpanText = colorResource(R.color.widget_cell_subtitle_color_dark),
        widgetDescription = colorResource(R.color.widget_cell_subtitle_color_dark),
        addButtonContent = colorResource(R.color.widget_picker_add_button_text_color_dark),
        addButtonBackground = colorResource(R.color.widget_picker_add_button_background_color_dark),

        // Widget preview
        widgetPlaceholderBackground =
            colorResource(R.color.widget_picker_preview_placeholder_background_dark),
        widgetPlaceholderContent =
            colorResource(R.color.widget_picker_preview_placeholder_content_dark),

        // Floating Toolbar
        toolbarBackground = colorResource(R.color.widget_picker_toolbar_background_dark),
        toolbarTabSelectedBackground =
            colorResource(R.color.widget_picker_toolbar_selected_tab_background_dark),
        toolbarTabUnSelectedBackground =
            colorResource(R.color.widget_picker_toolbar_unselected_tab_background_dark),
        toolbarTabContent = colorResource(R.color.widget_picker_toolbar_tab_content_color_dark),

        // Search bar
        searchBarBackground = colorResource(R.color.widget_picker_search_bar_background_color_dark),
        searchBarPlaceholderText = colorResource(R.color.widget_picker_search_text_color_dark),
        searchBarText = colorResource(R.color.widget_picker_search_text_color_dark),
        searchBarSearchIcon = colorResource(R.color.widget_picker_search_text_color_dark),
        searchBarClearButtonIcon = colorResource(R.color.widget_picker_search_text_color_dark),
        searchBarBackButtonIcon = colorResource(R.color.widget_picker_search_text_color_dark),
        searchBarCursor = colorResource(R.color.widget_picker_search_cursor_color_dark),
    )

/**
 * An adapter that maps the resource tokens for widget picker light colors to the
 * [WidgetPickerColors] accepted by the widget picker module
 */
@Composable
fun lightWidgetPickerColors() =
    WidgetPickerColors(
        // Bottom Sheet
        sheetBackground = colorResource(R.color.widget_picker_primary_surface_color_light),
        dragHandle = colorResource(R.color.widget_picker_collapse_handle_color_light),
        sheetTitle = colorResource(R.color.widget_picker_title_color_light),
        sheetDescription = colorResource(R.color.widget_picker_description_color_light),

        // Expand collapse list
        expandCollapseIndicatorIcon =
            colorResource(R.color.widget_picker_expand_icon_button_color_light),
        expandCollapseIndicatorBackground =
            colorResource(R.color.widget_picker_expand_icon_button_background_light),
        expandableListItemsBackground =
            colorResource(R.color.widget_picker_expandable_list_items_background_light),

        // List header
        selectedListHeaderBackground =
            colorResource(R.color.widget_picker_clickable_list_header_background_light),
        unselectedListHeaderBackground = Color.Transparent,
        featuredHeaderLeadingIconBackground =
            colorResource(R.color.widget_picker_featured_header_icon_background_light),
        featuredHeaderLeadingIcon =
            colorResource(R.color.widget_picker_featured_header_icon_color_light),
        listHeaderTitle = colorResource(R.color.widget_picker_header_app_title_color_light),
        listHeaderSubTitle = colorResource(R.color.widget_picker_header_app_subtitle_color_light),

        // Error message
        noWidgetsErrorText = colorResource(R.color.widget_picker_no_widget_error_color_light),

        // Widgets container
        widgetsContainerBackground =
            colorResource(R.color.widget_picker_widgets_container_background_light),

        // App icon
        placeholderAppIcon = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),

        // Widget details
        widgetLabel = colorResource(R.color.widget_cell_title_color_light),
        widgetSpanText = colorResource(R.color.widget_cell_subtitle_color_light),
        widgetDescription = colorResource(R.color.widget_cell_subtitle_color_light),
        addButtonContent = colorResource(R.color.widget_picker_add_button_text_color_light),
        addButtonBackground =
            colorResource(R.color.widget_picker_add_button_background_color_light),

        // Widget preview
        widgetPlaceholderBackground =
            colorResource(R.color.widget_picker_preview_placeholder_background_light),
        widgetPlaceholderContent =
            colorResource(R.color.widget_picker_preview_placeholder_content_light),

        // Floating Toolbar
        toolbarBackground = colorResource(R.color.widget_picker_toolbar_background_light),
        toolbarTabSelectedBackground =
            colorResource(R.color.widget_picker_toolbar_selected_tab_background_light),
        toolbarTabUnSelectedBackground =
            colorResource(R.color.widget_picker_toolbar_unselected_tab_background_light),
        toolbarTabContent = colorResource(R.color.widget_picker_toolbar_tab_content_color_light),

        // Search bar
        searchBarBackground =
            colorResource(R.color.widget_picker_search_bar_background_color_light),
        searchBarPlaceholderText = colorResource(R.color.widget_picker_search_text_color_light),
        searchBarText = colorResource(R.color.widget_picker_search_text_color_light),
        searchBarSearchIcon = colorResource(R.color.widget_picker_search_text_color_light),
        searchBarClearButtonIcon = colorResource(R.color.widget_picker_search_text_color_light),
        searchBarBackButtonIcon = colorResource(R.color.widget_picker_search_text_color_light),
        searchBarCursor = colorResource(R.color.widget_picker_search_cursor_color_light),
    )
