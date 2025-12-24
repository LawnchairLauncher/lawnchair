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

package com.android.launcher3.widgetpicker.ui.testdata

import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Repository that provides widgets data to UI in screenshot tests. */
class ScreenshotTestWidgetsRepository(private val testData: ScreenshotTestData) :
    WidgetsRepository {
    override fun initialize(options: WidgetsRepository.InitializationOptions) {}

    override fun cleanUp() {}

    override fun observeWidgets(): Flow<List<WidgetApp>> = flowOf(testData.widgetApps())

    override fun observeWidgetApp(widgetAppId: WidgetAppId): Flow<WidgetApp?> =
        flowOf(testData.widgetApps().first { it.id == widgetAppId })

    override suspend fun getWidgetPreview(id: WidgetId): WidgetPreview =
        testData.widgetPreviews()[id] ?: WidgetPreview.PlaceholderWidgetPreview

    override fun getFeaturedWidgets(): Flow<List<PickableWidget>> =
        flowOf(testData.featuredWidgets())

    override suspend fun searchWidgets(query: String): List<WidgetApp> = emptyList()
}
