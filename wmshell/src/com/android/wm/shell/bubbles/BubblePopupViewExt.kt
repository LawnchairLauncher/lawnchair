/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.bubbles

import com.android.wm.shell.R
import com.android.wm.shell.shared.bubbles.BubblePopupDrawable
import com.android.wm.shell.shared.bubbles.BubblePopupView

/**
 * A convenience method to setup the [BubblePopupView] with the correct config using local resources
 */
fun BubblePopupView.setup() {
    val attrs =
        context.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.dialogCornerRadius
            )
        )

    val res = context.resources
    val config =
        BubblePopupDrawable.Config(
            color = context.getColor(com.android.internal.R.color.materialColorSurfaceContainer),
            cornerRadius = attrs.getDimension(0, 0f),
            contentPadding = res.getDimensionPixelSize(R.dimen.bubble_popup_padding),
            arrowWidth = res.getDimension(R.dimen.bubble_popup_arrow_width),
            arrowHeight = res.getDimension(R.dimen.bubble_popup_arrow_height),
            arrowRadius = res.getDimension(R.dimen.bubble_popup_arrow_corner_radius)
        )
    attrs.recycle()
    setupBackground(config)
}
