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

package com.android.launcher3.compose.core.widgetpicker

import com.android.launcher3.widgetpicker.WidgetPickerActivity
import com.android.launcher3.widgetpicker.WidgetPickerConfig
import javax.annotation.Nonnull
import javax.inject.Inject

/**
 * A wrapper for widget picker activity that is responsible for displaying the compose based
 * widget picker in [WidgetPickerActivity] when compose is enabled via build flag.
 */
interface WidgetPickerComposeWrapper {
    fun showAllWidgets(
        activity: WidgetPickerActivity,
        @Nonnull
        widgetPickerConfig: WidgetPickerConfig
    )
}

/**
 * A No-op [WidgetPickerComposeWrapper] that doesn't include widget picker in dagger graph that
 * don't involve widget picker e.g. launcher preview OR when compose is disabled via build flag.
 */
class NoOpWidgetPickerComposeWrapper @Inject constructor() : WidgetPickerComposeWrapper {
    override fun showAllWidgets(
        activity: WidgetPickerActivity,
        @Nonnull
        widgetPickerConfig: WidgetPickerConfig
    ) {
        error("Widget picker with compose is not supported")
    }
}
