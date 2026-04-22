/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.shapes

import androidx.annotation.StringRes

data class IconShapeModel(
    val key: String,
    @StringRes val titleId: Int,
    val pathString: String,
    val folderRadiusRatio: Float = 1f,
    val shapeRadius: Float = DEFAULT_ICON_RADIUS,
) {
    companion object {
        /** Default icon radius in dp to use for transient taskbar rounding. */
        const val DEFAULT_ICON_RADIUS = 26f
    }
}
