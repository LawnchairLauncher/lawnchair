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
import com.android.launcher3.widgetpicker.data.repository.WidgetAppIconsRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * A domain layer object that enables the UI layer to get the apps icons from data layer.
 * - Responsible for merging the necessary information from widgets and app icons repositories to
 * provide the app icons.
 */
@WidgetPickerSingleton
class WidgetAppIconsInteractor @Inject constructor(
    @WidgetPickerRepository
    private val widgetAppIconsRepository: WidgetAppIconsRepository,
    @WidgetPickerRepository
    private val widgetsRepository: WidgetsRepository,
    @WidgetPickerBackground
    private val backgroundContext: CoroutineContext,
) {
    /** Returns a flow of icons for all widget apps that host widgets. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllWidgetAppIcons(): Flow<Map<WidgetAppId, WidgetAppIcon>> =
        widgetsRepository.observeWidgets().flatMapLatest { widgetApps ->
            val flows: List<Flow<Pair<WidgetAppId, WidgetAppIcon>>> = widgetApps.map { widgetApp ->
                widgetAppIconsRepository.getAppIcon(widgetApp.id).map {
                    widgetApp.id to it
                }
            }

            combine(flows) { it.toMap() }
        }.flowOn(backgroundContext)
}
