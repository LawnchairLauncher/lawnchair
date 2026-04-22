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

package com.android.wm.shell.common.pip

import android.util.Size
import java.io.PrintWriter

interface SizeSpecSource {
    /** Returns max size allowed for the PIP window  */
    fun getMaxSize(aspectRatio: Float): Size

    /** Returns default size for the PIP window  */
    fun getDefaultSize(aspectRatio: Float): Size

    /** Returns min size allowed for the PIP window  */
    fun getMinSize(aspectRatio: Float): Size

    /** Returns the adjusted size based on current size and target aspect ratio  */
    fun getSizeForAspectRatio(size: Size, aspectRatio: Float): Size

    /** Overrides the minimum pip size requested by the app */
    fun setOverrideMinSize(overrideMinSize: Size?)

    /** Returns the minimum pip size requested by the app */
    fun getOverrideMinSize(): Size?

    /** Returns the minimum edge size of the override minimum size, or 0 if not set.  */
    fun getOverrideMinEdgeSize(): Int {
        val overrideMinSize = getOverrideMinSize() ?: return 0
        return Math.min(overrideMinSize.width, overrideMinSize.height)
    }

    fun onConfigurationChanged() {}

    /** Dumps the internal state of the size spec */
    fun dump(pw: PrintWriter, prefix: String) {}
}