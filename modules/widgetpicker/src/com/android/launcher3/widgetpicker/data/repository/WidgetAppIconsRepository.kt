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

import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import kotlinx.coroutines.flow.Flow

/** A repository that provides app icons for the widget picker. */
interface WidgetAppIconsRepository {
    /**
     * A hook to setup the repository so clients can observe the widgets available on device.
     * This serves as a place to start listening to the backing caches / data sources.
     */
    fun initialize()

    /**
     * Loads and returns app icon (may initially return a low res icon, followed by a high res once
     * available).
     */
    fun getAppIcon(widgetAppId: WidgetAppId): Flow<WidgetAppIcon>

    /** Clean up any external listeners or state (if necessary). */
    fun cleanUp()
}
