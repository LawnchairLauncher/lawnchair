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

import android.content.res.Resources
import android.graphics.Insets
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.draganddrop.SplitDragPolicy

/**
 * When the user is dragging an icon from Taskbar to add an app into split
 * screen, we have a set of rules by which we draw and move colored drop
 * targets around the screen. The rules are provided through this interface.
 *
 * Each possible screen layout should have an implementation of this interface.
 * E.g.
 * - 50:50 two-app split
 * - 10:45:45 three-app split
 * - single app, no split
 *     = three implementations of this interface.
 */
interface DropTargetAnimSupplier {
    /**
     * Returns a Pair of lists.
     * First list (length n): Where to draw the n colored drop zones.
     * Second list (length n): How to animate the drop zones as user hovers around.
     *
     * Ex: First list => [A, B, C] // 3 views will be created representing these 3 targets
     * Second list => [
     *      [A (scaleX=4), B (translateX=20), C (translateX=20)], // hovering over A
     *      [A (translateX=20), B (scaleX=4), C (translateX=20)], // hovering over B
     *      [A (translateX=20), B (translateX=20), C (scaleX=4)], // hovering over C
     *  ]
     *
     *  All indexes assume 0 to N => left to right when [isLeftRightSplit] is true and top to bottom
     *  when [isLeftRightSplit] is false. Indexing is left to right even in RtL mode.
     *
     *  All lists should have the SAME number of elements, even if no animations are to be run for
     *  a given target while in a hover state.
     *  It's not that we don't trust you, but we _really_ don't trust you, so this will throw an
     *  exception if lengths are different. Don't ruin it for everyone else...
     *  or do. Idk, you're an adult.
     */
    fun getTargets(displayLayout: DisplayLayout, insets: Insets, isLeftRightSplit: Boolean,
                   resources: Resources) :
            Pair<List<SplitDragPolicy.Target>, List<List<HoverAnimProps>>>
}