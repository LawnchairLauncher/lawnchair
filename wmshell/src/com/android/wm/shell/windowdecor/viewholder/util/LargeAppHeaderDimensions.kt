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
import com.android.wm.shell.R
import com.android.wm.shell.windowdecor.common.DrawableInsets

/**
 * The dimensions of the App Header in its larger form, adapted to the minimum a11y requirements.
 */
class LargeAppHeaderDimensions
private constructor(
    private val resources: Resources,
    private val defaultAppHeaderDimensions: DefaultAppHeaderDimensions,
) : AppHeaderDimensions by defaultAppHeaderDimensions {

    constructor(resources: Resources) : this(resources, DefaultAppHeaderDimensions(resources))

    override val appChipBackgroundInsets: DrawableInsets =
        DrawableInsets(
            vertical =
                getDimensionPixelSize(
                    R.dimen.desktop_mode_header_app_chip_ripple_inset_vertical_large
                )
        )

    override val minimizeBackgroundInsets: DrawableInsets =
        DrawableInsets(
            insets = getDimensionPixelSize(R.dimen.desktop_mode_header_minimize_ripple_inset_large)
        )

    override val maximizeBackgroundInsets: DrawableInsets =
        DrawableInsets(
            insets = getDimensionPixelSize(R.dimen.desktop_mode_header_maximize_ripple_inset_large)
        )

    override val closeBackgroundInsets: DrawableInsets =
        DrawableInsets(
            insets = getDimensionPixelSize(R.dimen.desktop_mode_header_close_ripple_inset_large)
        )

    override val windowControlButtonWidth: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_header_window_control_button_width_large)

    override val windowControlButtonHeight: Int =
        getDimensionPixelSize(R.dimen.desktop_mode_header_window_control_button_height_large)

    override val windowControlButtonPadding: Rect =
        Rect(
            getDimensionPixelSize(R.dimen.desktop_mode_header_window_control_button_padding_large),
            getDimensionPixelSize(R.dimen.desktop_mode_header_window_control_button_padding_large),
            getDimensionPixelSize(R.dimen.desktop_mode_header_window_control_button_padding_large),
            getDimensionPixelSize(R.dimen.desktop_mode_header_window_control_button_padding_large),
        )

    private fun getDimensionPixelSize(@DimenRes res: Int) = resources.getDimensionPixelSize(res)
}
