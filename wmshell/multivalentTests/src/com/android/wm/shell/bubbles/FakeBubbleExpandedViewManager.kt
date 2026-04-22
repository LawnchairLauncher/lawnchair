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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.window.WindowContainerToken
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import java.util.Collections

/** Fake implementation of [BubbleExpandedViewManager] for testing. */
class FakeBubbleExpandedViewManager(var bubbleBar: Boolean = false, var expanded: Boolean = false) :
    BubbleExpandedViewManager {

    override val overflowBubbles: List<Bubble>
        get() = Collections.emptyList()

    override fun setOverflowListener(listener: BubbleData.Listener) {}

    override fun collapseStack() {}

    override fun updateWindowFlagsForBackpress(intercept: Boolean) {}

    override fun promoteBubbleFromOverflow(bubble: Bubble) {}

    override fun removeBubble(key: String, reason: Int) {}

    override fun dismissBubble(bubble: Bubble, reason: Int) {}

    override fun setNoteBubbleTaskId(key: String, taskId: Int) {}

    override fun isStackExpanded(): Boolean {
        return expanded
    }

    override fun isShowingAsBubbleBar(): Boolean {
        return bubbleBar
    }

    override fun hideCurrentInputMethod() {}

    override fun updateBubbleBarLocation(location: BubbleBarLocation, source: Int) {}

    override fun getAppBubbleRootTaskToken(): WindowContainerToken? {
        return null
    }

    override fun shouldBeAppBubble(taskInfo: ActivityManager.RunningTaskInfo): Boolean {
        return false
    }
}
