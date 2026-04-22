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

package com.android.wm.shell.windowdecor.extension

import android.annotation.DimenRes
import android.content.res.Resources

/** Loads dimensions in pixels or returns default value if resource id equals [ID_NULL]. */
fun Resources.getDimensionPixelSize(@DimenRes resourceId: Int, defaultValue: Int): Int {
    if (resourceId == Resources.ID_NULL) return defaultValue
    return getDimensionPixelSize(resourceId)
}

/** Loads dimension value or returns default value if resource id equals [ID_NULL]. */
fun Resources.getDimension(@DimenRes resourceId: Int, defaultValue: Float): Float {
    if (resourceId == Resources.ID_NULL) return defaultValue
    return getDimension(resourceId)
}
