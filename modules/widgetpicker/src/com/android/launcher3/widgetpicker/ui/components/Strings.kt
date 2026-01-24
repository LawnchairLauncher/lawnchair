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
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.launcher3.widgetpicker.R
import java.util.Locale

/** Helper to build a string representing widgets count */
@Composable
fun widgetsCountString(count: Int): String {
    val icuCountFormat =
        MessageFormat(
            stringResource(R.string.widgets_list_header_widgets_count_label),
            Locale.getDefault(),
        )
    return icuCountFormat.format(mapOf(COUNT_KEY to count))
}

private const val COUNT_KEY = "count"
