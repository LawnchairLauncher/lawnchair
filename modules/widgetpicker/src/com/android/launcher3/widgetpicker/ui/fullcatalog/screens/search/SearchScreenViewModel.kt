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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.android.launcher3.widgetpicker.WidgetPickerSingleton
import com.android.launcher3.widgetpicker.domain.interactor.WidgetAppIconsInteractor
import com.android.launcher3.widgetpicker.domain.interactor.WidgetsInteractor
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.ui.ViewModel
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.AppIconsState
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.PreviewsState
import com.android.launcher3.widgetpicker.ui.model.DisplayableWidgetApp
import com.android.launcher3.widgetpicker.ui.model.DisplayableWidgetApp.Companion.getWidgetIdsForApp
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * View model for the search screen that allows searching for all widgets in the full catalog view
 * of the widget picker.
 */
class SearchScreenViewModel @AssistedInject constructor(
    private val widgetsInteractor: WidgetsInteractor,
    private val widgetAppIconsInteractor: WidgetAppIconsInteractor
) : ViewModel {
    /** Data backing the results (widget apps & their widgets) that match the provided input. */
    var resultsState by mutableStateOf(SearchResultsState())
        private set

    /** Current query that is input in search bar by the user. */
    var query by mutableStateOf("")
        private set

    /** Data about all app icons shown on search screen. */
    var appIconsState by mutableStateOf(AppIconsState())
        private set

    /**
     * Id of the app that is currently selected / expanded in search results. When null, implies
     * none of the apps is expanded.
     */
    var selectedWidgetAppId by mutableStateOf<WidgetAppId?>(null)
    private val _selectedWidgetAppWidgetIds = snapshotFlow {
        selectedWidgetAppId?.let {
            resultsState.results.getWidgetIdsForApp(it)
        } ?: listOf()
    }

    /** Preview information about the widgets in the search results. */
    var previewsState by mutableStateOf(PreviewsState())
        private set

    override suspend fun onInit() {
        coroutineScope {
            launch { initializeSearchResults() }
            launch { initAppIcons() }
            launch { initialWidgetPreviews() }
        }
    }

    private suspend fun initializeSearchResults() {
        snapshotFlow { query }.collectLatest { newInput ->
            if (newInput.isEmpty()) {
                resultsState = SearchResultsState()
            } else {
                widgetsInteractor.searchWidgetApps(query).collectLatest { matchedResults ->
                    resultsState = SearchResultsState(
                        results = matchedResults.map { DisplayableWidgetApp.fromWidgetApp(it) }
                    )
                }
            }
        }

        awaitCancellation()
    }

    private suspend fun initAppIcons() {
        widgetAppIconsInteractor.getAllWidgetAppIcons().collect { result ->
            appIconsState = AppIconsState(icons = result)
        }

        awaitCancellation()
    }

    private suspend fun initialWidgetPreviews() {
        _selectedWidgetAppWidgetIds.collect { ids ->
            previewsState =
                PreviewsState(
                    previewsState.previews +
                            ids.associateWith { widgetsInteractor.getWidgetPreview(it) })
        }

        awaitCancellation()
    }

    /**
     * Callback for when user types a search query and its changed. Updates the [query] state
     * so that the search results can update as well.
     */
    fun onQueryChange(query: String) {
        this.query = query
    }

    /**
     * Callback to handle the visibility toggle of a widget app in search results; e.g. when user
     * clicks an app to expand or collapse.
     *
     * @param id id of the widget app that was tapped; if its same as last id that was tapped, the
     * [selectedWidgetAppId] is reset to null (aka toggled); if its different, then the provided id
     * is set as the [selectedWidgetAppId]. To clear the [selectedWidgetAppId] invoke this with
     * `null`
     */
    fun onSelectedWidgetAppToggle(id: WidgetAppId) {
        selectedWidgetAppId = if (selectedWidgetAppId != id) {
            id
        } else {
            null
        }
    }

    /** A factory that should be injected whenever [SearchScreenViewModel] is required. */
    @AssistedFactory
    @WidgetPickerSingleton
    interface Factory {
        fun create(): SearchScreenViewModel
    }
}

/** Represents data that backs the list of widget apps (and their widgets) shown in the results. */
@Stable
@Immutable
data class SearchResultsState(
    val results: List<DisplayableWidgetApp> = emptyList()
)
