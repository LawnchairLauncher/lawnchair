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

package com.android.launcher3.widgetpicker.ui.components

import android.icu.text.MessageFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.launcher3.widgetpicker.R
import java.util.Locale

/** Helper to build a string representing widgets count */
@Composable
fun widgetsCountString(widgets: Int, shortcuts: Int): String {
    @Composable
    fun icuString(@StringRes resId: Int, count: Int): String {
        val icuStringFormat = MessageFormat(stringResource(resId), Locale.getDefault())
        return icuStringFormat.format(mapOf(COUNT_KEY to count))
    }

    return when {
        shortcuts > 0 && widgets > 0 ->
            stringResource(
                R.string.widgets_list_header_widgets_and_shortcuts_count_label,
                icuString(R.string.widgets_list_header_widgets_count_label, widgets),
                icuString(R.string.widgets_list_header_shortcuts_count_label, shortcuts),
            )

        shortcuts == 0 -> icuString(R.string.widgets_list_header_widgets_count_label, widgets)
        else -> icuString(R.string.widgets_list_header_shortcuts_count_label, shortcuts)
    }
}

private const val COUNT_KEY = "count"
