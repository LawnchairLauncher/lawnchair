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

package com.android.launcher3.widgetpicker.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.launcher3.widgetpicker.shared.model.PickableWidget

/**
 * A group of widgets that are bucketed into a same sized container that can to be arranged in same
 * or consecutive rows within the appwidget grid.
 *
 * For example, if 2x2 and 3x2 widgets are all bucketed to 2x2 cell size's container, all those
 * widgets will be in this group with the [previewContainerHeightPx] and [previewContainerWidthPx]
 * being the pixel sizes of 2x2 span size. Grouping these same height widgets, enables us to
 * visually show the widgets side by side and enable visual coherence in the grid.
 */
@Stable
@Immutable
data class WidgetSizeGroup(
    val previewContainerHeightPx: Int,
    val previewContainerWidthPx: Int,
    val widgets: List<PickableWidget>,
)
