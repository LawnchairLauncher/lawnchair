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

import com.android.launcher3.widgetpicker.data.repository.WidgetAppIconsRepository
import com.android.launcher3.widgetpicker.shared.model.AppIcon
import com.android.launcher3.widgetpicker.shared.model.AppIconBadge
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Repository that provides data to UI in screenshot tests. */
class ScreenshotTestWidgetAppIconsRepository(private val testData: ScreenshotTestData) :
    WidgetAppIconsRepository {
    override fun initialize() {}

    override fun cleanUp() {}

    override fun getAppIcon(widgetAppId: WidgetAppId): Flow<WidgetAppIcon> =
        flowOf(
            testData.widgetAppIcons()[widgetAppId]
                ?: WidgetAppIcon(AppIcon.PlaceHolderAppIcon, AppIconBadge.NoBadge)
        )
}
