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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/** A visual cue displayed on list headers to indicate its current expand / collapse state. */
@Composable
fun ExpandCollapseIndicator(
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(ExpandCollapseIndicatorDimensions.size)
                .clip(ExpandCollapseIndicatorDimensions.cornerShape)
                .background(WidgetPickerTheme.colors.expandCollapseIndicatorBackground),
    ) {
        Icon(
            modifier = Modifier.size(ExpandCollapseIndicatorDimensions.iconSize),
            tint = WidgetPickerTheme.colors.expandCollapseIndicatorIcon,
            contentDescription = null, // Decorative
            imageVector =
                if (expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
        )
    }
}

private object ExpandCollapseIndicatorDimensions {
    val cornerShape = RoundedCornerShape(50.dp)
    val size = 24.dp
    val iconSize = 16.dp
}
