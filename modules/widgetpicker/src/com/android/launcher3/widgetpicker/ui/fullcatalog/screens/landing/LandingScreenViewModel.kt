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

package com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.android.launcher3.widgetpicker.WidgetPickerSingleton
import com.android.launcher3.widgetpicker.domain.interactor.WidgetAppIconsInteractor
import com.android.launcher3.widgetpicker.domain.interactor.WidgetsInteractor
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfile
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfileType
import com.android.launcher3.widgetpicker.ui.ViewModel
import com.android.launcher3.widgetpicker.ui.model.DisplayableWidgetApp
import com.android.launcher3.widgetpicker.ui.model.DisplayableWidgetApp.Companion.getWidgetIdsForApp
import com.android.launcher3.widgetpicker.ui.model.WidgetSizeGroup
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A view model responsible for providing the data to the landing screen UI within the full catalog
 * of widgets in widget picker.
 */
class LandingScreenViewModel @AssistedInject constructor(
    private val widgetsInteractor: WidgetsInteractor,
    private val widgetAppIconsInteractor: WidgetAppIconsInteractor
) : ViewModel {
    override suspend fun onInit() {
        coroutineScope {
            launch { initBrowseWidgets() }
            launch { initBrowseWidgetPreviews() }
            launch { initWorkWidgetPreviews() }
            launch { initFeaturedWidgets() }
            launch { initAppIcons() }

            awaitCancellation()
        }
    }

    /** Data backing the browse section in the landing screen. */
    var browseWidgetsState by mutableStateOf<BrowseWidgetsState>(BrowseWidgetsState.NoData)
        private set

    /** Previews for the widgets shown for personal tab in browse section of landing screen. */
    var personalWidgetsPreviewsState by mutableStateOf(PreviewsState())
        private set

    /** Previews for the widgets shown for work tab in browse section of landing screen. */
    var workWidgetsPreviewState by mutableStateOf(PreviewsState())
        private set

    /** Data about all app icons shown on landing screen. */
    var appIconsState by mutableStateOf(AppIconsState())
        private set

    /**
     * Id of the app that is currently selected / expanded in personal list of apps. When null,
     *  implies none of the apps is expanded.
     */
    var selectedPersonalAppId by mutableStateOf<WidgetAppId?>(null)
        private set
    private val _selectedPersonalWidgetAppWidgetIds = snapshotFlow {
        val selectedId = selectedPersonalAppId
        val currentBrowseState = browseWidgetsState
        when {
            selectedId != null && currentBrowseState is BrowseWidgetsState.Data ->
                currentBrowseState.personalWidgetApps.getWidgetIdsForApp(selectedId)

            else ->
                listOf()
        }
    }

    /**
     * Id of the app that is currently selected / expanded in work list of apps. When null,
     *  implies none of the apps is expanded.
     */
    var selectedWorkAppId by mutableStateOf<WidgetAppId?>(null)
        private set
    private val _selectedWorkWidgetAppWidgetIds = snapshotFlow {
        val selectedId = selectedWorkAppId
        val currentBrowseState = browseWidgetsState
        when {
            selectedId != null && currentBrowseState is BrowseWidgetsState.Data ->
                currentBrowseState.workWidgetApps.getWidgetIdsForApp(selectedId)

            else ->
                listOf()
        }
    }

    /** Data backing the featured section in landing screen; holds widgets that are featured. */
    var featuredWidgetsState by mutableStateOf(FeaturedWidgetsState())
        private set

    /** Preview information about the widgets shown in the featured section. */
    var featuredWidgetPreviewsState by mutableStateOf(PreviewsState())
        private set

    private suspend fun initBrowseWidgets() {
        widgetsInteractor.getWidgetAppsByProfile().collect { result ->
            val personalEntry =
                result.entries.find { it.key.type == WidgetUserProfileType.PERSONAL }
            val workEntry = result.entries.find { it.key.type == WidgetUserProfileType.WORK }
            browseWidgetsState = if (personalEntry == null) {
                BrowseWidgetsState.NoData
            } else {
                BrowseWidgetsState.Data(
                    personalProfile = personalEntry.key,
                    personalWidgetApps = personalEntry.value.map {
                        DisplayableWidgetApp.fromWidgetApp(it)
                    },
                    workProfile = workEntry?.key,
                    workWidgetApps = workEntry?.value?.map {
                        DisplayableWidgetApp.fromWidgetApp(it)
                    } ?: emptyList(),
                )
            }
        }

        awaitCancellation()
    }

    private suspend fun initBrowseWidgetPreviews() {
        _selectedPersonalWidgetAppWidgetIds.collect { ids ->
            personalWidgetsPreviewsState =
                PreviewsState(
                    personalWidgetsPreviewsState.previews + ids.associateWith {
                        widgetsInteractor.getWidgetPreview(
                            it
                        )
                    })
        }

        awaitCancellation()
    }

    private suspend fun initWorkWidgetPreviews() {
        _selectedWorkWidgetAppWidgetIds.collect { ids ->
            workWidgetsPreviewState =
                PreviewsState(workWidgetsPreviewState.previews + ids.associateWith {
                    widgetsInteractor.getWidgetPreview(
                        it
                    )
                })
        }

        awaitCancellation()
    }

    private suspend fun initFeaturedWidgets() {
        widgetsInteractor.getFeaturedWidgets().collect { result ->
            featuredWidgetsState = FeaturedWidgetsState(result.groupBy {
                Pair(it.sizeInfo.containerWidthPx, it.sizeInfo.containerHeightPx)
            }.map { (containerSize, value) ->
                WidgetSizeGroup(
                    previewContainerWidthPx = containerSize.first,
                    previewContainerHeightPx = containerSize.second,
                    widgets = value
                )
            }, result.size)

            featuredWidgetPreviewsState =
                PreviewsState(result.associate { res ->
                    res.id to widgetsInteractor.getWidgetPreview(res.id)
                })
        }
        awaitCancellation()
    }

    private suspend fun initAppIcons() {
        widgetAppIconsInteractor.getAllWidgetAppIcons().collect { result ->
            appIconsState = AppIconsState(icons = result)
        }
        awaitCancellation()
    }

    /**
     * Callback to handle the toggle of a personal widget app; e.g. when user clicks an app to
     * expand or collapse.
     *
     * @param id id of the widget app that was tapped; if its same as last id that was tapped, the
     * [selectedPersonalAppId] is reset to null (aka toggled); if its different, then the provided
     * id is set as the [selectedPersonalAppId]. To clear the [selectedPersonalAppId] invoke this
     * with `null`
     */
    fun onSelectedPersonalAppToggle(id: WidgetAppId?) {
        selectedPersonalAppId = if (id == selectedPersonalAppId) {
            null
        } else {
            id
        }
    }

    /**
     * Callback to handle the toggle of a work widget app; e.g. when user clicks an app to expand or
     * collapse.
     *
     * @param id id of the widget app that was tapped; if its same as last id that was tapped, the
     * [selectedWorkAppId] is reset to null (aka toggled); if its different, then the provided id is
     * set as the [selectedWorkAppId]. To clear the [selectedWorkAppId] invoke this with `null`
     */
    fun onSelectedWorkAppToggle(id: WidgetAppId?) {
        selectedWorkAppId = if (id == selectedWorkAppId) {
            null
        } else {
            id
        }
    }

    /** A factory that should be injected whenever [LandingScreenViewModel] is required. */
    @AssistedFactory
    @WidgetPickerSingleton
    interface Factory {
        fun create(): LandingScreenViewModel
    }
}

/** Represents data that backs the widgets browsing experience in the full catalog. */
@Stable
@Immutable
sealed class BrowseWidgetsState {
    /**
     * Information that is displayed when user taps browse tab.
     *
     * @param personalProfile data about the personal user profile that is used to display the
     * personal tab; when there is no work profile, it is referred to as "browse" tab.
     * @param personalWidgetApps list of apps (with their widgets) belonging to the personal user
     * profile
     * @param workProfile data about the work user profile that is used to display the work tab.
     * @param workWidgetApps list of apps (with their widgets) belonging to the work user profile.
     */
    data class Data(
        val personalProfile: WidgetUserProfile,
        val personalWidgetApps: List<DisplayableWidgetApp>,
        val workProfile: WidgetUserProfile?,
        val workWidgetApps: List<DisplayableWidgetApp>
    ) : BrowseWidgetsState()

    /** No data loaded yet or unavailable for some reason. */
    data object NoData : BrowseWidgetsState()
}

/**
 * Represents widgets data to be shown in the "featured" section.
 * @param sizeGroups groups holding widgets of similar sizes that can be presented in a coherent
 * manner in the featured section.
 * @param widgetsCount total count of widgets in the size groups (pre-computed so UI doesn't need to
 * count).
 */
@Stable
@Immutable
data class FeaturedWidgetsState(
    val sizeGroups: List<WidgetSizeGroup> = emptyList(),
    val widgetsCount: Int = 0
)

/**
 * State class holding preview information about widgets in a specific section (featured, browse,
 * etc)
 * @param previews previews per widget (keyed by its id)
 */
@Stable
@Immutable
data class PreviewsState(
    val previews: Map<WidgetId, WidgetPreview> = emptyMap()
)

/**
 * State class holding app icons shown in a specific section (featured, browse, etc.)
 * @param icons icons per app (keyed by its app id)
 */
@Stable
@Immutable
data class AppIconsState(
    val icons: Map<WidgetAppId, WidgetAppIcon> = emptyMap()
)
