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
package com.android.launcher3.taskbar.bubbles

import android.graphics.Bitmap
import android.graphics.Path
import com.android.wm.shell.common.bubbles.BubbleInfo

/** Contains state info about a bubble in the bubble bar as well as presentation information. */
data class BubbleBarBubble(
    val info: BubbleInfo,
    val view: BubbleView,
    val badge: Bitmap,
    val icon: Bitmap,
    val dotColor: Int,
    val dotPath: Path,
    val appName: String
) {

    val key: String = info.key
}
