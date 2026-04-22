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

package com.android.launcher3.widgetpicker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Custom theme providing tokens necessary for the widget picker UI.
 *
 * Provide this wrapping the compose view that hosts the widget picker.
 * Note: The app theme should be at top level prior to applying this theme.
 */
@Composable
fun WidgetPickerTheme(
    colors: WidgetPickerColors = defaultWidgetPickerColors(),
    textStyles: WidgetPickerTextStyles = defaultWidgetPickerTextStyles(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalWidgetPickerColors provides colors,
        LocalWidgetPickerTextStyles provides textStyles,
    ) {
        content()
    }
}

/**
 * Contains functions to access the current theme values provided at the call site's position in the
 * hierarchy.
 */
object WidgetPickerTheme {
    /** Retrieves the current [WidgetPickerColors] at the call site's position in the hierarchy. */
    val colors: WidgetPickerColors
        @Composable @ReadOnlyComposable get() = LocalWidgetPickerColors.current

    /**
     * Retrieves the current [WidgetPickerTextStyles] at the call site's position in the
     * hierarchy.
     */
    val typography: WidgetPickerTextStyles
        @Composable @ReadOnlyComposable get() = LocalWidgetPickerTextStyles.current
}
