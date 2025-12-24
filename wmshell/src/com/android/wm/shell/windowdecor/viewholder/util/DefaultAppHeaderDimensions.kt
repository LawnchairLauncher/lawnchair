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
package com.android.wm.shell.windowdecor.viewholder.util

import android.annotation.DimenRes
import android.content.res.Resources
import android.graphics.Rect
import android.window.DesktopModeFlags
import com.android.wm.shell.R
import com.android.wm.shell.windowdecor.common.DrawableInsets

/** The default dimensions of the App Header. */
class DefaultAppHeaderDimensions(private val resources: Resources) : AppHeaderDimensions {
    override val buttonCornerRadius: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_header_buttons_ripple_radius)

    override val appNameMaxWidth: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_header_app_name_max_width)

    override val expandMenuErrorImageWidth: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_header_expand_menu_error_image_width)

    override val expandMenuErrorImageMargin: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_header_expand_menu_error_image_margin)

    override val appChipBackgroundInsets: DrawableInsets =
        DrawableInsets(
            vertical =
                getDimensionPixelSize(R.dimen.desktop_mode_header_app_chip_ripple_inset_vertical)
        )

    override val minimizeBackgroundInsets: DrawableInsets =
        DrawableInsets(
            vertical =
                getDimensionPixelSize(R.dimen.desktop_mode_header_minimize_ripple_inset_vertical),
            horizontal =
                getDimensionPixelSize(R.dimen.desktop_mode_header_minimize_ripple_inset_horizontal),
        )

    override val maximizeBackgroundInsets: DrawableInsets =
        DrawableInsets(
            vertical =
                getDimensionPixelSize(R.dimen.desktop_mode_header_maximize_ripple_inset_vertical),
            horizontal =
                getDimensionPixelSize(R.dimen.desktop_mode_header_maximize_ripple_inset_horizontal),
        )

    override val closeBackgroundInsets: DrawableInsets =
        DrawableInsets(
            vertical =
                getDimensionPixelSize(R.dimen.desktop_mode_header_close_ripple_inset_vertical),
            horizontal =
                getDimensionPixelSize(R.dimen.desktop_mode_header_close_ripple_inset_horizontal),
        )

    override val windowControlButtonWidth: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_header_window_control_button_width)

    override val windowControlButtonHeight: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_header_window_control_button_height)

    override val windowControlButtonPadding: Rect =
        Rect(
            getDimensionPixelSize(
                R.dimen.desktop_mode_header_window_control_button_padding_horizontal
            ),
            getDimensionPixelSize(
                R.dimen.desktop_mode_header_window_control_button_padding_vertical
            ),
            getDimensionPixelSize(
                R.dimen.desktop_mode_header_window_control_button_padding_horizontal
            ),
            getDimensionPixelSize(
                R.dimen.desktop_mode_header_window_control_button_padding_vertical
            ),
        )

    override val windowControlButtonMarginEnd: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_header_window_control_button_padding_end)

    override val customizableRegionMarginStart: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_customizable_caption_margin_start)

    override val customizableRegionMarginEnd: Int
        get() {
            val numOfButtons = if (DesktopModeFlags.ENABLE_MINIMIZE_BUTTON.isTrue) 3 else 2
            return (windowControlButtonWidth + windowControlButtonMarginEnd) * numOfButtons +
                customizableRegionEmptyDragSpace
        }

    override val customizableRegionEmptyDragSpace: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_customizable_caption_drag_only_width)

    private fun getDimensionPixelSize(@DimenRes res: Int) = resources.getDimensionPixelSize(res)
}
