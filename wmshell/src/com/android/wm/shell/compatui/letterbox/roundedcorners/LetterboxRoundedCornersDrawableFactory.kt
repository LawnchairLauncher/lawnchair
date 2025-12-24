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
import com.android.wm.shell.compatui.letterbox.roundedcorners.LetterboxRoundedCornersDrawable.FlipType.FLIP_HORIZONTAL
import com.android.wm.shell.compatui.letterbox.roundedcorners.LetterboxRoundedCornersDrawable.FlipType.FLIP_VERTICAL
import com.android.wm.shell.compatui.letterbox.roundedcorners.RoundedCornersDrawableFactory.Position
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/**
 * [LetterboxRoundedCornersFactory] implementation returning rounded corners [Drawable]s using SVG
 * format.
 */
@WMSingleton
class LetterboxRoundedCornersDrawableFactory @Inject constructor() :
    RoundedCornersDrawableFactory<LetterboxRoundedCornersDrawable> {
    override fun getRoundedCornerDrawable(
        color: Color,
        position: Position,
        radius: Float,
    ): LetterboxRoundedCornersDrawable {
        val corners = LetterboxRoundedCornersDrawable(color, radius)
        return when (position) {
            Position.TOP_LEFT -> corners
            Position.TOP_RIGHT -> corners.flip(FLIP_HORIZONTAL)
            Position.BOTTOM_LEFT -> corners.flip(FLIP_VERTICAL)
            Position.BOTTOM_RIGHT -> corners.flip(FLIP_HORIZONTAL).flip(FLIP_VERTICAL)
        }
    }
}
