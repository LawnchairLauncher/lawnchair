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

package com.android.launcher3.widgetpicker.repository

import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** A fake implementation of [WidgetsRepository] for testing */
class FakeWidgetsRepository : WidgetsRepository {
    private val _widgetApps = MutableStateFlow<List<WidgetApp>>(emptyList())
    private val _widgetPreviews = MutableStateFlow<Map<WidgetId, WidgetPreview>>(emptyMap())
    private var _featuredWidgetIds = MutableStateFlow(emptySet<WidgetId>())

    fun seedWidgets(widgetApps: List<WidgetApp>) {
        _widgetApps.update { widgetApps }
    }

    fun seedWidgetPreviews(map: Map<WidgetId, WidgetPreview>) {
        _widgetPreviews.update { map }
    }

    fun seedFeaturedWidgets(widgetIds: Set<WidgetId>) {
        _featuredWidgetIds.update { widgetIds }
    }

    override fun initialize() {}

    override fun observeWidgets(): Flow<List<WidgetApp>> = _widgetApps

    override suspend fun getWidgetPreview(id: WidgetId): WidgetPreview =
        _widgetPreviews.value[id] ?: WidgetPreview.PlaceholderWidgetPreview

    override fun getFeaturedWidgets(): Flow<List<PickableWidget>> =
        combine(_featuredWidgetIds, _widgetApps) { widgetIds, widgetApps ->
            widgetApps.flatMap { it.widgets }.filter { widgetIds.contains(it.id) }
        }

    override suspend fun searchWidgets(input: String): List<WidgetApp> = _widgetApps.value.map {
        it.copy(widgets = it.widgets.filter { widget ->
            val description = widget.description?.toString() ?: ""
            widget.label.contains(input) || description.contains(input)
        })
    }.filter { it.widgets.isNotEmpty() }

    override fun cleanUp() {
        _widgetApps.update { emptyList() }
        _widgetPreviews.update { emptyMap() }
        _featuredWidgetIds.update { emptySet() }
    }
}
