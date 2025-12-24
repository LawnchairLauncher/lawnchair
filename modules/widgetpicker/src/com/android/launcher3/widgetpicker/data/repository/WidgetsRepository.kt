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

package com.android.launcher3.widgetpicker.data.repository

import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import kotlinx.coroutines.flow.Flow

/** A repository of widgets available on the device from various apps */
interface WidgetsRepository {
    /**
     * A hook to setup the repository so clients can observe the widgets available on device. This
     * serves as a place to start listening to the backing caches / data sources.
     *
     * @param options what aspects to initialize
     */
    fun initialize(options: InitializationOptions = InitializationOptions())

    /** Observe widgets available on the device from different apps. */
    fun observeWidgets(): Flow<List<WidgetApp>>

    /** Observe widgets available on the device from a specific app. */
    fun observeWidgetApp(widgetAppId: WidgetAppId): Flow<WidgetApp?>

    /**
     * Loads a preview for an app widget. Returns a placeholder preview if the widget is not found.
     */
    suspend fun getWidgetPreview(id: WidgetId): WidgetPreview

    /** Get widgets that can be featured in widget picker. */
    fun getFeaturedWidgets(): Flow<List<PickableWidget>>

    /**
     * Search widgets and their apps that match the given plain text [query] string typed by the
     * user. Matches the widget's label, description and app's title (case-insensitive).
     */
    suspend fun searchWidgets(query: String): List<WidgetApp>

    /** Clean up any external listeners or state (if necessary). */
    fun cleanUp()

    /**
     * Options around what kind of widgets to work with in the widgets repository.
     *
     * @param widgetAppId initialize repository only for specific app that hosts widgets
     * @param loadFeaturedWidgets initialize featured widgets as well
     */
    data class InitializationOptions(
        val widgetAppId: WidgetAppId? = null,
        val loadFeaturedWidgets: Boolean = true,
    )
}
