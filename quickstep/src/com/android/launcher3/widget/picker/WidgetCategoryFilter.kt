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

package com.android.launcher3.widget.picker

/**
 * A filter that can be applied on the widgetCategory attribute from appwidget-provider to identify
 * if the widget can be displayed on a specific widget surface.
 * - Negative value (e.g. "category_a.inv() and category_b.inv()" excludes the widgets with given
 *   categories.
 * - Positive value (e.g. "category_a or category_b" includes widgets with those categories.
 * - 0 means no filter.
 */
class WidgetCategoryFilter(val categoryMask: Int) {
    /** Applies the [categoryMask] to return if the [widgetCategory] matches. */
    fun matches(widgetCategory: Int): Boolean {
        return if (categoryMask > 0) { // inclusion filter
            (widgetCategory and categoryMask) != 0
        } else if (categoryMask < 0) { // exclusion filter
            (widgetCategory and categoryMask) == widgetCategory
        } else {
            true // no filter
        }
    }
}
