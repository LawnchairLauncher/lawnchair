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

package com.android.wm.shell.draganddrop.anim

import android.graphics.Rect
import com.android.wm.shell.draganddrop.SplitDragPolicy

/**
 * Contains the animation props to represent a single state of drop targets.
 * When the user is dragging, we'd be going between different HoverAnimProps
 */
data class HoverAnimProps(
    var target: SplitDragPolicy.Target,
    val transX: Float,
    val transY: Float,
    val scaleX: Float,
    val scaleY: Float,
    /**
     * Pass in null to indicate this target cannot be hovered over for this given animation/
     * state
     *
     * TODO: There's some way we can probably use the existing translation/scaling values
     * to take [.target]'s hitRect and scale that so we don't have to take in a separate
     * hoverRect in the CTOR. Have to make sure the pivots match since view's pivot in the
     * center of the view and rect's pivot at 0, 0 if unspecified.
     * The two may also not be correlated, but worth investigating
     *
     */
    var hoverRect: Rect?
) {

    override fun toString(): String {
        return ("targetId: " + target
                + " translationX: " + transX
                + " translationY: " + transY
                + " scaleX: " + scaleX
                + " scaleY: " + scaleY
                + " hoverRect: " + hoverRect)
    }
}