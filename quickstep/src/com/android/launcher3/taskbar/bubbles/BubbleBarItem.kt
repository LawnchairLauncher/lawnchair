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

/** An entity in the bubble bar. */
sealed class BubbleBarItem(open var key: String, open var view: BubbleView)

/** Contains state info about a bubble in the bubble bar as well as presentation information. */
data class BubbleBarBubble(
    var info: BubbleInfo,
    override var view: BubbleView,
    var badge: Bitmap,
    var icon: Bitmap,
    var dotColor: Int,
    var dotPath: Path,
    var appName: String
) : BubbleBarItem(info.key, view)

/** Represents the overflow bubble in the bubble bar. */
data class BubbleBarOverflow(override var view: BubbleView) : BubbleBarItem("Overflow", view)
