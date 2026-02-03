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

package com.android.launcher3.widgetpicker.domain.interactor

import com.android.launcher3.widgetpicker.WidgetPickerBackground
import com.android.launcher3.widgetpicker.WidgetPickerRepository
import com.android.launcher3.widgetpicker.WidgetPickerSingleton
import com.android.launcher3.widgetpicker.data.repository.WidgetUsersRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.domain.usecase.FilterWidgetsForHostUseCase
import com.android.launcher3.widgetpicker.domain.usecase.GroupWidgetAppsByProfileUseCase
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * A domain layer object that enables the UI layer to interact with the widgets in data layer.
 * - Responsible for applying business rules such as applying host constraints, filtering data for
 *   paused work profile, etc.
 */
@WidgetPickerSingleton
class WidgetsInteractor
@Inject
constructor(
    @WidgetPickerRepository
    private val widgetsRepository: WidgetsRepository,
    @WidgetPickerRepository
    private val widgetUsersRepository: WidgetUsersRepository,
    private val filterWidgetsForHostUseCase: FilterWidgetsForHostUseCase,
    private val getWidgetAppsByProfileUseCase: GroupWidgetAppsByProfileUseCase,
    @WidgetPickerBackground
    private val backgroundContext: CoroutineContext,
) {
    /** Returns the list of widget apps per user profiles. */
    fun getWidgetAppsByProfile(): Flow<Map<WidgetUserProfile, List<WidgetApp>>> =
        combine(
            widgetsRepository.observeWidgets(),
            widgetUsersRepository.observeUserProfiles()
        ) { widgetApps,
            widgetUserProfiles ->
            val filteredWidgets =
                widgetApps
                    .map { it.copy(widgets = filterWidgetsForHostUseCase(it.widgets)) }
                    .filter { it.widgets.isNotEmpty() }
                    .sortedBy { it.title?.toString() ?: "" }
            widgetUserProfiles?.let {
                getWidgetAppsByProfileUseCase(
                    filteredWidgets,
                    widgetUserProfiles,
                    widgetUsersRepository.getWorkProfileUser(),
                )
            } ?: emptyMap()
        }
            .distinctUntilChanged()
            .flowOn(backgroundContext)

    /** Loads and returns the preview for an appwidget. */
    suspend fun getWidgetPreview(widgetId: WidgetId): WidgetPreview =
        widgetsRepository.getWidgetPreview(widgetId)

    /** Returns widgets that can be featured in the widget picker. */
    fun getFeaturedWidgets(): Flow<List<PickableWidget>> =
        combine(
            widgetsRepository.getFeaturedWidgets(),
            widgetUsersRepository.observeUserProfiles(),
        ) { widgets, widgetUserProfiles ->
            val filteredWidgets = filterWidgetsForHostUseCase(widgets)

            val hasPausedWorkProfile =
                widgetUserProfiles?.work?.paused ?: false
            val workProfileUser =
                if (hasPausedWorkProfile) {
                    checkNotNull(widgetUsersRepository.getWorkProfileUser())
                } else null

            return@combine if (hasPausedWorkProfile) {
                filteredWidgets.filter { it.id.userHandle != workProfileUser }
            } else {
                filteredWidgets
            }
        }

    /***
     * Returns the list of widget apps that match the given plain text [query] string entered by the
     * user. The widget's label, description and app's title is expected to be considered for
     * returning matches.
     *
     * Filters out
     * - widgets that don't match the host criteria
     * - when work profile is paused, work profile widgets are also filtered out.
     */
    suspend fun searchWidgetApps(query: String): Flow<List<WidgetApp>> =
        withContext(backgroundContext) {
            val matchedWidgetApps = widgetsRepository.searchWidgets(query).map {
                it.copy(
                    widgets = filterWidgetsForHostUseCase(it.widgets)
                )
            }.filter { it.widgets.isNotEmpty() }

            // business rule: We don't show work profile widgets when profile is paused.
            val workProfileUser = widgetUsersRepository.getWorkProfileUser()
            if (workProfileUser != null) {
                widgetUsersRepository.observeUserProfiles().map { profiles ->
                    val paused = checkNotNull(profiles?.work).paused
                    if (paused) {
                        matchedWidgetApps.filter { it.id.userHandle != workProfileUser }
                    } else {
                        matchedWidgetApps
                    }
                }
            } else {
                flowOf(matchedWidgetApps)
            }
        }
}
