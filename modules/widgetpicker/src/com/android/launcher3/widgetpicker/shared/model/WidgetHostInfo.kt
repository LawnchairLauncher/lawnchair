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

package com.android.launcher3.widgetpicker.shared.model

import android.os.UserHandle

/**
 * Data class that holds information about the host needs to display the widget picker.
 *
 * @param title an optional title that should be shown in place of default "Widgets" title.
 * @param description an optional 1-2 line description to be shown below the title. If not set, no
 *   description is shown.
 * @param constraints constraints around which widgets can be shown in the picker.
 * @param showDragShadow indicates whether to show drag shadow for the widgets when dragging them;
 *   can be set to false if host manages drag shadow on its own (e.g. home screen to animate the
 *   shadow with actual content)
 */
data class WidgetHostInfo(
    val title: String? = null,
    val description: String? = null,
    val constraints: List<HostConstraint> = emptyList(),
    val showDragShadow: Boolean = true,
)

/** Various constraints for the widget host. */
sealed class HostConstraint {
    /**
     * A constraint to apply on `widgetCategory` when deciding whether to show a widget in the
     * widget picker.
     *
     * @param categoryInclusionMask mask that includes widgets matching the given widget category
     *   mask e.g. HOME_SCREEN or KEYGUARD; 0 implies no mask
     * @param categoryExclusionMask mask that excludes widgets matching the mask e.g.
     *   NOT_KEYGUARD.inv(); 0 implies no mask
     */
    data class HostCategoryConstraint(
        val categoryInclusionMask: Int,
        val categoryExclusionMask: Int,
    ) : HostConstraint()

    /**
     * A constraint to apply on `user` associated with widgets shown in widget picker.
     *
     * @param userFilters user profiles for which widgets list should be shown as empty. e.g. for
     *   lockscreen widgets, based on an admin setting, the host may filter out work widgets. In
     *   such case, the profile tab shows a generic no widgets available message.
     */
    data class HostUserConstraint(val userFilters: List<UserHandle>) : HostConstraint()

    /** Indicates that the host doesn't support shortcuts. */
    data object NoShortcutsConstraint : HostConstraint()
}
