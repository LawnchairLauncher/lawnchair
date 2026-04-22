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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/**
 * A tab (suitable for displaying in a horizontal toolbar) that shows a leading icon along with its
 * label.
 *
 * The icon is shown only when tab is selected (it animates per the provided transition spec.)
 */
@Composable
fun LeadingIconToolbarTab(
    leadingIcon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconEnterTransition: EnterTransition = LeadingIconToolbarTabDefaults.iconEnterTransition,
    iconExitTransition: ExitTransition = LeadingIconToolbarTabDefaults.iconExitTransition,
) {
    val backgroundColor =
        if (selected) {
            WidgetPickerTheme.colors.toolbarTabSelectedBackground
        } else {
            WidgetPickerTheme.colors.toolbarTabUnSelectedBackground
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .selectable(
                    selected = selected,
                    onClick = onClick
                )
                .background(color = backgroundColor)
                .minimumInteractiveComponentSize()
                .padding(horizontal = LeadingIconToolbarTabDefaults.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        this@Row.AnimatedVisibility(
            visible = selected,
            enter = iconEnterTransition,
            exit = iconExitTransition,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null, // decorative
                modifier = Modifier.padding(end = LeadingIconToolbarTabDefaults.contentSpacing),
                tint = WidgetPickerTheme.colors.toolbarTabContent,
            )
        }
        Text(
            text = label,
            style = WidgetPickerTheme.typography.toolbarTabLabel,
            color = WidgetPickerTheme.colors.toolbarTabContent,
        )
    }
}

/** Holds default values used by the [LeadingIconToolbarTab]. */
object LeadingIconToolbarTabDefaults {
    val horizontalPadding = 16.dp
    val contentSpacing = 8.dp

    val iconEnterTransition: EnterTransition = fadeIn() + expandIn(expandFrom = Alignment.Center)
    val iconExitTransition: ExitTransition = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
}
