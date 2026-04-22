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

package com.android.launcher3.widgetpicker

import android.os.UserHandle

/**
 * Possible parameters sent over by the widget host when launching the widget picker activity.
 * @param uiSurface surface string representing the host. "widgets" for home screen
 * @param title an optional title that some surfaces like lockscreen may provide to change the
 * title appearing in widget picker.
 * @param description an optional description that some surfaces like lockscreen may provide to
 * display it below the title in widget picker; by default no description is
 * shown.
 * @param categoryInclusionFilter mask applied to identify if a widget's category matches the
 * host's category requirements and if widget can be included.
 * @param categoryExclusionFilter mask applied to identify if per host requirements the widget
 * should be excluded from the picker.
 * @param filteredUsers users for which widgets list should be shown empty (potentially due to
 * admin / enterprise restrictions); no widgets message is shown instead.
 */
data class WidgetPickerConfig(
    val uiSurface: String = HOMESCREEN_WIDGETS_UI_SURFACE,
    val title: String? = null,
    val description: String? = null,
    val categoryInclusionFilter: Int = 0,
    val categoryExclusionFilter: Int = 0,
    val filteredUsers: List<UserHandle> = listOf()
) {
    /**
     * Indicates if the intent request is for picking home screen widgets.
     * If false, implies its for another surface external to launcher.
     */
    val isForHomeScreen: Boolean
        get() = uiSurface == HOMESCREEN_WIDGETS_UI_SURFACE

    companion object {
        /**
         * Name of the extra (set by widget picker) in the result when the activity is launched by
         * lockscreen with `startActivityForResult`; indicates that activity finished since a widget
         * was being dragged.
         */
        const val EXTRA_IS_PENDING_WIDGET_DRAG = "is_pending_widget_drag"

        const val HOMESCREEN_WIDGETS_UI_SURFACE = "widgets"
    }
}
