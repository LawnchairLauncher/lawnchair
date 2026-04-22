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

    // Mapping of visual icon size to icon specs value http://b/235886078
    val iconSize40dp = TaskbarIconSize(44)
    val iconSize44dp = TaskbarIconSize(48)
    val iconSize48dp = TaskbarIconSize(52)
    val iconSize52dp = TaskbarIconSize(57)

    val transientTaskbarIconSizes = arrayOf(iconSize44dp, iconSize48dp, iconSize52dp)

    val defaultPersistentIconSize = iconSize40dp
    val defaultTransientIconSize = iconSize44dp

    val minimumIconSize = iconSize40dp

    val defaultPersistentIconMargin = TaskbarIconMarginSize(6)
    val defaultTransientIconMargin = TaskbarIconMarginSize(12)

    val minimumTaskbarIconTouchSize = TaskbarIconSize(48)

    val transientOrPinnedTaskbarIconPaddingSize = iconSize52dp

    val transientTaskbarIconSizeByGridSize =
        mapOf(
            TransientTaskbarIconSizeKey(6, 5, false) to iconSize52dp,
            TransientTaskbarIconSizeKey(6, 5, true) to iconSize52dp,
            TransientTaskbarIconSizeKey(4, 4, false) to iconSize48dp,
            TransientTaskbarIconSizeKey(4, 4, true) to iconSize52dp,
            TransientTaskbarIconSizeKey(4, 5, false) to iconSize48dp,
            TransientTaskbarIconSizeKey(4, 5, true) to iconSize48dp,
            TransientTaskbarIconSizeKey(5, 5, false) to iconSize44dp,
            TransientTaskbarIconSizeKey(5, 5, true) to iconSize44dp,
        )
}
