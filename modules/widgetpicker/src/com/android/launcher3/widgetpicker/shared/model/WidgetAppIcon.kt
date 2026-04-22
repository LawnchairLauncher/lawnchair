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

import android.graphics.Bitmap
import androidx.annotation.DrawableRes

/**
 * Icon to be displayed for an app that hosts widgets.
 *
 * @property icon an icon representing the app
 * @property badge an icon that can be placed over the app [icon] to highlight additional important
 *   characteristics about the app e.g. if app belongs to a work profile.
 */
data class WidgetAppIcon(val icon: AppIcon, val badge: AppIconBadge)

/** A data type holding information for rendering an icon */
sealed class AppIcon {
    /** An icon that can be rendered as a [Bitmap]. */
    data class HighResBitmapIcon(val bitmap: Bitmap) : AppIcon()

    /**
     * A low res icon that should be rendered by filling the icon slot with the provided [color].
     */
    data class LowResColorIcon(val color: Int) : AppIcon()

    /** Represents a missing app icon. */
    data object PlaceHolderAppIcon : AppIcon()
}

sealed class AppIconBadge {
    /**
     * A badge that should be rendered using the provided drawable [drawableResId] and tinted with
     * provided [tintColor].
     */
    data class DrawableBadge(@DrawableRes val drawableResId: Int, val tintColor: Int) :
        AppIconBadge()

    /** Indicates there is no badge available for an app icon. */
    data object NoBadge : AppIconBadge()
}
