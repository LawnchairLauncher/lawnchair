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

package com.android.launcher3.widgetpicker.ui.fullcatalog.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.components.AppHeaderDescriptionStyle
import com.android.launcher3.widgetpicker.ui.components.SinglePaneLayout
import com.android.launcher3.widgetpicker.ui.components.TwoPaneLayout
import com.android.launcher3.widgetpicker.ui.components.WidgetAppHeaderStyle
import com.android.launcher3.widgetpicker.ui.components.WidgetAppsList
import com.android.launcher3.widgetpicker.ui.components.WidgetsGrid
import com.android.launcher3.widgetpicker.ui.components.WidgetsSearchBar
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.AppIconsState
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.PreviewsState

/** Screen showing the search results in the widget picker when browsing the full widget catalog. */
@Composable
fun SearchScreen(
    isCompact: Boolean,
    onExitSearchMode: () -> Unit,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    showDragShadow: Boolean,
    viewModel: SearchScreenViewModel,
) {
    SearchScreen(
        isCompact = isCompact,
        input = viewModel.query,
        resultsState = viewModel.resultsState,
        selectedWidgetAppId = viewModel.selectedWidgetAppId,
        appIconsState = viewModel.appIconsState,
        widgetPreviewsState = viewModel.previewsState,
        onSearch = viewModel::onQueryChange,
        onSelectedWidgetAppToggle = viewModel::onSelectedWidgetAppToggle,
        onExitSearchMode = onExitSearchMode,
        onWidgetInteraction = onWidgetInteraction,
        showDragShadow = showDragShadow,
    )
}

@Composable
private fun SearchScreen(
    isCompact: Boolean,
    input: String,
    resultsState: SearchResultsState,
    selectedWidgetAppId: WidgetAppId?,
    appIconsState: AppIconsState,
    widgetPreviewsState: PreviewsState,
    onSearch: (String) -> Unit,
    onSelectedWidgetAppToggle: (id: WidgetAppId) -> Unit,
    onExitSearchMode: () -> Unit,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    showDragShadow: Boolean,
) {
    val emptyWidgetsErrorMessage =
        if (input.isNotEmpty()) {
            stringResource(R.string.widgets_no_search_results)
        } else {
            ""
        }

    val searchBar: @Composable () -> Unit = {
        WidgetsSearchBar(
            text = input,
            isSearching = true,
            onSearch = onSearch,
            onToggleSearchMode = { searchModeEnabled ->
                if (!searchModeEnabled) {
                    onExitSearchMode()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (isCompact) {
        SearchScreenSinglePane(
            searchBar = searchBar,
            resultsState = resultsState,
            selectedWidgetAppId = selectedWidgetAppId,
            appIconsState = appIconsState,
            widgetPreviewsState = widgetPreviewsState,
            onSelectedWidgetAppChange = onSelectedWidgetAppToggle,
            onWidgetInteraction = onWidgetInteraction,
            showDragShadow = showDragShadow,
            emptyWidgetsErrorMessage = emptyWidgetsErrorMessage,
        )
    } else {
        SearchScreenTwoPane(
            searchBar = searchBar,
            resultsState = resultsState,
            selectedWidgetAppId = selectedWidgetAppId,
            appIconsState = appIconsState,
            widgetPreviewsState = widgetPreviewsState,
            onSelectedWidgetAppChange = onSelectedWidgetAppToggle,
            onWidgetInteraction = onWidgetInteraction,
            showDragShadow = showDragShadow,
            emptyWidgetsErrorMessage = emptyWidgetsErrorMessage,
        )
    }
}

@Composable
private fun SearchScreenSinglePane(
    searchBar: @Composable () -> Unit,
    resultsState: SearchResultsState,
    selectedWidgetAppId: WidgetAppId?,
    appIconsState: AppIconsState,
    widgetPreviewsState: PreviewsState,
    onSelectedWidgetAppChange: (id: WidgetAppId) -> Unit,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    showDragShadow: Boolean,
    emptyWidgetsErrorMessage: String,
) {
    SinglePaneLayout(
        searchBar = searchBar,
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                WidgetAppsList(
                    modifier = Modifier.fillMaxSize(),
                    widgetApps = resultsState.results,
                    selectedWidgetAppId = selectedWidgetAppId,
                    widgetAppHeaderStyle = WidgetAppHeaderStyle.EXPANDABLE,
                    headerDescriptionStyle = AppHeaderDescriptionStyle.COMBINED_WIDGETS_TITLE,
                    onWidgetAppClick = { widgetApp -> onSelectedWidgetAppChange(widgetApp.id) },
                    appIcons = appIconsState.icons,
                    widgetPreviews = widgetPreviewsState.previews,
                    onWidgetInteraction = onWidgetInteraction,
                    showDragShadow = showDragShadow,
                    emptyWidgetsErrorMessage = emptyWidgetsErrorMessage,
                )
            }
        },
    )
}

@Composable
fun SearchScreenTwoPane(
    searchBar: @Composable () -> Unit,
    resultsState: SearchResultsState,
    selectedWidgetAppId: WidgetAppId?,
    appIconsState: AppIconsState,
    widgetPreviewsState: PreviewsState,
    onSelectedWidgetAppChange: (id: WidgetAppId) -> Unit,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    showDragShadow: Boolean,
    emptyWidgetsErrorMessage: String,
) {
    TwoPaneLayout(
        searchBar = searchBar,
        leftContent = {
            Box(modifier = Modifier.fillMaxSize()) {
                WidgetAppsList(
                    modifier = Modifier.fillMaxSize(),
                    widgetApps = resultsState.results,
                    selectedWidgetAppId = selectedWidgetAppId,
                    widgetAppHeaderStyle = WidgetAppHeaderStyle.CLICKABLE,
                    headerDescriptionStyle = AppHeaderDescriptionStyle.COMBINED_WIDGETS_TITLE,
                    appIcons = appIconsState.icons,
                    widgetPreviews = widgetPreviewsState.previews,
                    onWidgetAppClick = { widgetApp -> onSelectedWidgetAppChange(widgetApp.id) },
                    onWidgetInteraction = onWidgetInteraction,
                    showDragShadow = showDragShadow,
                    emptyWidgetsErrorMessage = emptyWidgetsErrorMessage,
                )
            }
        },
        rightPaneTitle =
            rightPaneTitle(resultsState = resultsState, selectedWidgetAppId = selectedWidgetAppId),
        rightContent = {
            selectedWidgetAppId?.let { id ->
                val selectedWidgets =
                    remember(id, resultsState) {
                        id.let { selectedId ->
                            resultsState.results.find { it.id == selectedId }?.widgetSizeGroups
                        } ?: listOf()
                    }

                WidgetsGrid(
                    modifier = Modifier.fillMaxWidth().wrapContentSize(),
                    showAllWidgetDetails = true,
                    widgetSizeGroups = selectedWidgets,
                    previews = widgetPreviewsState.previews,
                    onWidgetInteraction = onWidgetInteraction,
                    showDragShadow = showDragShadow,
                )
            }
        },
    )
}

/**
 * Title for the right pane that is updated when selected tab on left changes. When set, a talkback
 * user can use four finger swipe down to switch to right pane.
 */
@Composable
private fun rightPaneTitle(
    resultsState: SearchResultsState,
    selectedWidgetAppId: WidgetAppId?,
): String? {
    val selectedAppName: CharSequence? =
        selectedWidgetAppId?.let { selectedId ->
            resultsState.results.find { it.id == selectedId }?.title
        }

    return if (selectedAppName != null) {
        stringResource(R.string.widget_picker_right_pane_accessibility_label, selectedAppName)
    } else {
        null
    }
}
