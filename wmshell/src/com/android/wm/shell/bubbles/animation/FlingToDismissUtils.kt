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

package com.android.wm.shell.bubbles.animation

/** Utils related to the fling to dismiss animation. */
object FlingToDismissUtils {

    /** The target width surrounding the dismiss target on a small width screen, e.g. phone. */
    private const val FLING_TO_DISMISS_TARGET_WIDTH_SMALL = 3f
    /**
     * The target width surrounding the dismiss target on a medium width screen, e.g. tablet in
     * portrait.
     */
    private const val FLING_TO_DISMISS_TARGET_WIDTH_MEDIUM = 4.5f
    /**
     * The target width surrounding the dismiss target on a large width screen, e.g. tablet in
     * landscape.
     */
    private const val FLING_TO_DISMISS_TARGET_WIDTH_LARGE = 6f

    /** Returns the dismiss target width for the specified [screenWidthPx]. */
    @JvmStatic
    fun getFlingToDismissTargetWidth(screenWidthPx: Int) = when {
        screenWidthPx >= 2000 -> FLING_TO_DISMISS_TARGET_WIDTH_LARGE
        screenWidthPx >= 1500 -> FLING_TO_DISMISS_TARGET_WIDTH_MEDIUM
        else -> FLING_TO_DISMISS_TARGET_WIDTH_SMALL
    }
}
