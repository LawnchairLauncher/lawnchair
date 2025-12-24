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

package com.android.launcher3.widgetpicker.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/**  Contains theme that launcher applies to the widget picker. */
@Composable
fun LauncherWidgetPickerTheme(content: @Composable () -> Unit) {
    val widgetPickerColors =
        if (isSystemInDarkTheme()) {
            darkWidgetPickerColors()
        } else {
            lightWidgetPickerColors()
        }

    MaterialTheme {
        WidgetPickerTheme(
            colors = widgetPickerColors,
            textStyles = launcherWidgetPickerTextStyles()
        ) {
            content()
        }
    }
}
