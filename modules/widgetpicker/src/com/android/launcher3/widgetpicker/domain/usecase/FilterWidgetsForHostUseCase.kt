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

package com.android.launcher3.widgetpicker.domain.usecase

import com.android.launcher3.widgetpicker.WidgetPickerHostInfo
import com.android.launcher3.widgetpicker.WidgetPickerSingleton
import com.android.launcher3.widgetpicker.shared.model.HostConstraint
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.shared.model.isAppWidget
import com.android.launcher3.widgetpicker.shared.model.isShortcut
import javax.inject.Inject

/**
 * A usecase that hosts the business logic of filtering widgets based on host constraints and
 * information.
 */
@WidgetPickerSingleton
class FilterWidgetsForHostUseCase
@Inject
constructor(@WidgetPickerHostInfo private val hostInfo: WidgetHostInfo) {
    operator fun invoke(widgets: List<PickableWidget>) =
        widgets.filter { widget ->
            val widgetInfo = widget.widgetInfo

            val eligibleForHost =
                hostInfo.constraints.all { constraint ->
                    when (constraint) {
                        is HostConstraint.NoShortcutsConstraint -> !widgetInfo.isShortcut()

                        is HostConstraint.HostUserConstraint ->
                            !constraint.userFilters.contains(widget.id.userHandle)

                        is HostConstraint.HostCategoryConstraint -> {
                            // category applies only to widgets
                            if (widgetInfo.isAppWidget()) {
                                val widgetCategory = widgetInfo.appWidgetProviderInfo.widgetCategory
                                matchesCategory(constraint.categoryInclusionMask, widgetCategory) &&
                                    matchesCategory(
                                        constraint.categoryExclusionMask,
                                        widgetCategory,
                                    )
                            } else {
                                true
                            }
                        }
                    }
                }

            eligibleForHost
        }

    companion object {
        /**
         * A filter that can be applied on the widgetCategory attribute from appwidget-provider to
         * identify if the widget can be displayed on a specific widget surface.
         * - Negative value (e.g. "category_a.inv() and category_b.inv()" excludes the widgets with
         *   given categories.
         * - Positive value (e.g. "category_a or category_b" includes widgets with those categories.
         * - 0 means no filter.
         */
        private fun matchesCategory(categoryMask: Int, widgetCategory: Int): Boolean {
            return if (categoryMask > 0) { // inclusion filter
                (widgetCategory and categoryMask) != 0
            } else if (categoryMask < 0) { // exclusion filter
                (widgetCategory and categoryMask) == widgetCategory
            } else {
                true // no filter
            }
        }
    }
}
