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

package com.android.launcher3.taskbar.customization

/** Taskbar Icon Specs */
object TaskbarIconSpecs {

    val iconSize40dp = TaskbarIconSize(40)
    val iconSize44dp = TaskbarIconSize(44)
    val iconSize48dp = TaskbarIconSize(48)
    val iconSize52dp = TaskbarIconSize(52)

    val transientTaskbarIconSizes = arrayOf(iconSize44dp, iconSize48dp, iconSize52dp)

    val defaultPersistentIconSize = iconSize40dp
    val defaultTransientIconSize = iconSize44dp

    // defined as row, columns
    val transientTaskbarIconSizeByGridSize =
        mapOf(
            Pair(6, 5) to iconSize52dp,
            Pair(4, 5) to iconSize48dp,
            Pair(5, 4) to iconSize48dp,
            Pair(4, 4) to iconSize48dp,
            Pair(5, 6) to iconSize44dp,
        )
}
