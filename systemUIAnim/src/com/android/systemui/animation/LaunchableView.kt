/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.animation

import android.view.View

/** A view that can expand/launch into an app or a dialog. */
interface LaunchableView {
    /**
     * Set whether this view should block/postpone all calls to [View.setVisibility]. This ensures
     * that this view:
     * - remains invisible during the launch animation given that it is ghosted and already drawn
     *   somewhere else.
     * - remains invisible as long as a dialog expanded from it is shown.
     * - restores its expected visibility once the dialog expanded from it is dismissed.
     *
     * When `setShouldBlockVisibilityChanges(false)` is called, then visibility of the View should
     * be restored to its expected value, i.e. it should have the visibility of the last call to
     * `View.setVisibility()` that was made after `setShouldBlockVisibilityChanges(true)`, if any,
     * or the original view visibility otherwise.
     *
     * Note that calls to [View.setTransitionVisibility] shouldn't be blocked.
     *
     * @param block whether we should block/postpone all calls to `setVisibility`.
     */
    fun setShouldBlockVisibilityChanges(block: Boolean)
}

/** A delegate that can be used by views to make the implementation of [LaunchableView] easier. */
class LaunchableViewDelegate(
    private val view: View,

    /**
     * The lambda that should set the actual visibility of [view], usually by calling
     * super.setVisibility(visibility).
     */
    private val superSetVisibility: (Int) -> Unit,
) : LaunchableView {
    private var blockVisibilityChanges = false
    private var lastVisibility = view.visibility

    /** Call this when [LaunchableView.setShouldBlockVisibilityChanges] is called. */
    override fun setShouldBlockVisibilityChanges(block: Boolean) {
        if (block == blockVisibilityChanges) {
            return
        }

        blockVisibilityChanges = block
        if (block) {
            // Save the current visibility for later.
            lastVisibility = view.visibility
        } else {
            // Restore the visibility. To avoid accessibility issues, we change the visibility twice
            // which makes sure that we trigger a visibility flag change (see b/204944038#comment17
            // for more info).
            if (lastVisibility == View.VISIBLE) {
                superSetVisibility(View.INVISIBLE)
                superSetVisibility(View.VISIBLE)
            } else {
                superSetVisibility(View.VISIBLE)
                superSetVisibility(lastVisibility)
            }
        }
    }

    /** Call this when [View.setVisibility] is called. */
    fun setVisibility(visibility: Int) {
        if (blockVisibilityChanges) {
            lastVisibility = visibility
            return
        }

        superSetVisibility(visibility)
    }
}
