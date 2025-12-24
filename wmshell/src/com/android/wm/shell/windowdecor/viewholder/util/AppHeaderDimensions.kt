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

import android.graphics.Rect
import com.android.wm.shell.windowdecor.common.DrawableInsets

/** Provides the dimensions to use for drawing the app header. */
interface AppHeaderDimensions {
    /** The corner radius to apply to the app chip, maximize and close button's background. */
    val buttonCornerRadius: Int
    /** The max width of the app name. */
    val appNameMaxWidth: Int
    /** The width of the expand menu error image on the app header. */
    val expandMenuErrorImageWidth: Int
    /** The margin added between app name and expand menu error image on the app header. */
    val expandMenuErrorImageMargin: Int
    /** The insets to apply to the app chip's background drawable. */
    val appChipBackgroundInsets: DrawableInsets
    /** The insets to apply to the minimize button's background drawable. */
    val minimizeBackgroundInsets: DrawableInsets
    /** The insets to apply to the maximize button's background drawable. */
    val maximizeBackgroundInsets: DrawableInsets
    /** The insets to apply to the close button's background drawable. */
    val closeBackgroundInsets: DrawableInsets
    /** The width of the window control buttons. */
    val windowControlButtonWidth: Int
    /** The height of the window control buttons. */
    val windowControlButtonHeight: Int
    /** The padding to apply to the window control buttons. */
    val windowControlButtonPadding: Rect
    /** The end margin of the window control buttons. */
    val windowControlButtonMarginEnd: Int
    /** The start margin of the customizable region. */
    val customizableRegionMarginStart: Int
    /** The end margin of the customizable region. */
    val customizableRegionMarginEnd: Int
    /** The empty drag space of the customizable region. */
    val customizableRegionEmptyDragSpace: Int
}
