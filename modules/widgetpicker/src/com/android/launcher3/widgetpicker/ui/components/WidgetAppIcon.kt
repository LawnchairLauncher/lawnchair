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

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import com.android.launcher3.widgetpicker.shared.model.AppIcon
import com.android.launcher3.widgetpicker.shared.model.AppIconBadge
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.ui.components.WidgetAppIconDimensions.BADGE_SCALE
import com.android.launcher3.widgetpicker.ui.components.WidgetAppIconDimensions.ShadowAmbientColor
import com.android.launcher3.widgetpicker.ui.components.WidgetAppIconDimensions.ShadowElevation
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlin.math.roundToInt

/**
 * An app icon rendered from the provided [WidgetAppIcon] with an option badge.
 *
 * @param widgetAppIcon holds the icon and badge
 * @param size [AppIconSize] at which to render the icon
 * @param iconShape shape to apply when source icon in [widgetAppIcon] is full bleed
 */
@Composable
fun WidgetAppIcon(
    widgetAppIcon: WidgetAppIcon,
    size: AppIconSize,
    iconShape: Shape = WidgetAppIconShape,
) {
    val appIcon = widgetAppIcon.icon
    val badge = widgetAppIcon.badge

    when (appIcon) {
        AppIcon.PlaceHolderAppIcon -> PlaceholderAppIcon(size, iconShape)

        is AppIcon.LowResColorIcon -> LowResAppIcon(size, appIcon, iconShape)

        is AppIcon.HighResBitmapIcon -> {
            HighResAppIcon(size, appIcon, badge, iconShape)
        }
    }
}

@Composable
private fun HighResAppIcon(
    size: AppIconSize,
    icon: AppIcon.HighResBitmapIcon,
    badge: AppIconBadge,
    iconShape: Shape,
) {
    val clipModifier =
        if (icon.isFullBleed) {
            Modifier.shadow(
                    elevation = ShadowElevation,
                    shape = iconShape,
                    ambientColor = ShadowAmbientColor,
                )
                .clip(iconShape)
        } else {
            Modifier
        }

    Box(modifier = Modifier.size(size.iconSize)) {
        Icon(
            bitmap = icon.bitmap.asImageBitmap(),
            modifier = Modifier.fillMaxSize().then(clipModifier).fadeInWhenVisible("WidgetAppIcon"),
            contentDescription = null,
            tint = Color.Unspecified,
        )
        if (badge is AppIconBadge.DrawableBadge) {
            DrawableAppIconBadge(badge = badge, size = size)
        }
    }
}

@Composable
private fun LowResAppIcon(size: AppIconSize, icon: AppIcon.LowResColorIcon, iconShape: Shape) {
    Box(
        modifier =
            Modifier.size(size.iconSize).background(color = Color(icon.color), shape = iconShape)
    )
}

@Composable
private fun PlaceholderAppIcon(size: AppIconSize, iconShape: Shape) {
    Box(
        modifier =
            Modifier.size(size.iconSize)
                .background(
                    color = WidgetPickerTheme.colors.placeholderAppIcon.copy(alpha = 0.2f),
                    shape = iconShape,
                )
    )
}

@Composable
private fun BoxScope.DrawableAppIconBadge(
    badge: AppIconBadge.DrawableBadge,
    size: AppIconSize,
    badgeShape: Shape = CircleShape,
) {
    val density = LocalDensity.current
    val scaleOffset =
        remember(size, density) {
            with(density) {
                val iconSizePx = size.iconSize.roundToPx()
                val badgeSizePx = size.badgeSize.roundToPx()
                IntOffset(
                    x = badgeSizePx - (BADGE_SCALE * iconSizePx).roundToInt(),
                    y = badgeSizePx - (BADGE_SCALE * iconSizePx).roundToInt(),
                )
            }
        }

    Icon(
        painter = painterResource(badge.drawableResId),
        modifier =
            Modifier.offset { scaleOffset }
                .align(alignment = Alignment.BottomEnd)
                .size(size.badgeSize)
                .shadow(
                    elevation = ShadowElevation,
                    shape = badgeShape,
                    ambientColor = ShadowAmbientColor,
                )
                .background(color = Color.White, shape = badgeShape)
                .fadeInWhenVisible("WidgetAppIcon"),
        contentDescription = null,
        tint = colorResource(badge.tintColor),
    )
}

private val WidgetAppIconShape = IconShape()

private class IconShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val scaledPath = Path()
        val matrix = Matrix()
        matrix.setRectToRect(
            RectF(0f, 0f, DEFAULT_PATH_SIZE, DEFAULT_PATH_SIZE),
            RectF(0f, 0f, size.width, size.height),
            Matrix.ScaleToFit.CENTER,
        )
        IconMask.transform(matrix, scaledPath)

        return Outline.Generic(scaledPath.asComposePath())
    }

    companion object {
        private const val DEFAULT_PATH_SIZE = 100f
        private val IconMask: Path =
            AdaptiveIconDrawable(
                    /*backgroundDrawable=*/ android.graphics.Color.WHITE.toDrawable(),
                    /*foregroundDrawable=*/ null,
                )
                .apply { setBounds(0, 0, DEFAULT_PATH_SIZE.toInt(), DEFAULT_PATH_SIZE.toInt()) }
                .iconMask
    }
}

/** Size in which to display the app icon. */
enum class AppIconSize(val iconSize: Dp, val badgeSize: Dp) {
    /** A large size app icon meant to be displayed in the list header. */
    MEDIUM(iconSize = 48.dp, badgeSize = 24.dp),

    /** A small size app icon meant to be displayed along side the widget title / label. */
    SMALL(iconSize = 24.dp, badgeSize = 12.dp),
}

private object WidgetAppIconDimensions {
    // same as scaled used in launcher
    const val BADGE_SCALE = 0.444f

    val ShadowElevation = 1.dp
    val ShadowAmbientColor = Color.Gray
}
