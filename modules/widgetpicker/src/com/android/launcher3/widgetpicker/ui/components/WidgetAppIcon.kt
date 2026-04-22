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

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.shared.model.AppIcon
import com.android.launcher3.widgetpicker.shared.model.AppIconBadge
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/** An app icon rendered from the provided [WidgetAppIcon] with an option badge. */
@Composable
fun WidgetAppIcon(widgetAppIcon: WidgetAppIcon, size: AppIconSize) {
    val appIcon = widgetAppIcon.icon
    val badge = widgetAppIcon.badge

    AnimatedContent(targetState = appIcon) { icon ->
        when (icon) {
            AppIcon.PlaceHolderAppIcon -> PlaceholderAppIcon(size)

            is AppIcon.LowResColorIcon -> LowResAppIcon(size, icon)

            is AppIcon.HighResBitmapIcon -> {
                HighResAppIcon(size, icon, badge)
            }
        }
    }
}

@Composable
private fun HighResAppIcon(
    size: AppIconSize,
    icon: AppIcon.HighResBitmapIcon,
    badge: AppIconBadge,
) {
    Box(modifier = Modifier.size(size.iconSize)) {
        Icon(
            bitmap = icon.bitmap.asImageBitmap(),
            modifier = Modifier.fillMaxSize(),
            contentDescription = null,
            tint = Color.Unspecified,
        )
        if (badge is AppIconBadge.DrawableBadge) {
            DrawableAppIconBadge(badge = badge, size = size)
        }
    }
}

@Composable
private fun LowResAppIcon(size: AppIconSize, icon: AppIcon.LowResColorIcon) {
    Box(
        modifier =
            Modifier.size(size.iconSize).background(color = Color(icon.color), shape = CircleShape)
    )
}

@Composable
private fun PlaceholderAppIcon(size: AppIconSize) {
    Box(
        modifier =
            Modifier.size(size.iconSize)
                .background(
                    color = WidgetPickerTheme.colors.placeholderAppIcon.copy(alpha = 0.2f),
                    shape = CircleShape,
                )
    )
}

@Composable
private fun BoxScope.DrawableAppIconBadge(badge: AppIconBadge.DrawableBadge, size: AppIconSize) {
    Icon(
        painter = painterResource(badge.drawableResId),
        modifier =
            Modifier.align(alignment = Alignment.BottomEnd)
                .size(size.badgeSize)
                .background(color = Color.White, shape = CircleShape)
                .shadow(elevation = 0.5.dp, shape = CircleShape, spotColor = Color(0x11000000)),
        contentDescription = null,
        tint = colorResource(badge.tintColor),
    )
}

/** Size in which to display the app icon. */
enum class AppIconSize(val iconSize: Dp, val badgeSize: Dp) {
    /** A large size app icon meant to be displayed in the list header. */
    MEDIUM(iconSize = 48.dp, badgeSize = 24.dp),

    /** A small size app icon meant to be displayed along side the widget title / label. */
    SMALL(iconSize = 24.dp, badgeSize = 12.dp),
}
