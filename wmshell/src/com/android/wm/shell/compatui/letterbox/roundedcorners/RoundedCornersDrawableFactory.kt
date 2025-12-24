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

package com.android.wm.shell.compatui.letterbox.roundedcorners

import android.graphics.Color
import android.graphics.drawable.Drawable

/** Abstraction for the object responsible of the creation of the rounded corners Drawables. */
interface RoundedCornersDrawableFactory<T : Drawable> {

    enum class Position {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT,
    }

    /**
     * @param color The color of the rounded corner background.
     * @param position The position of the rounded corner.
     * @param radius The radius of the rounded corner.
     * @return The [Drawable] for the rounded corner in a given [position]
     */
    fun getRoundedCornerDrawable(color: Color, position: Position, radius: Float = 0f): T
}
