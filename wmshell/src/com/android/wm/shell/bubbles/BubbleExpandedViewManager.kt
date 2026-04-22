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

/** Manager interface for bubble expanded views. */
interface BubbleExpandedViewManager {

    val overflowBubbles: List<Bubble>
    fun setOverflowListener(listener: BubbleData.Listener)
    fun collapseStack()
    fun updateWindowFlagsForBackpress(intercept: Boolean)
    fun promoteBubbleFromOverflow(bubble: Bubble)
    fun removeBubble(key: String, reason: Int)
    fun dismissBubble(bubble: Bubble, reason: Int)
    fun setNoteBubbleTaskId(key: String, taskId: Int)
    fun isStackExpanded(): Boolean
    fun isShowingAsBubbleBar(): Boolean
    fun hideCurrentInputMethod()
    fun updateBubbleBarLocation(
        location: BubbleBarLocation,
        @BubbleBarLocation.UpdateSource source: Int,
    )
    fun getAppBubbleRootTaskToken(): WindowContainerToken?
    fun shouldBeAppBubble(taskInfo: ActivityManager.RunningTaskInfo): Boolean

    companion object {
        /**
         * Convenience function for creating a [BubbleExpandedViewManager] that delegates to the
         * given `controller`.
         */
        @JvmStatic
        fun fromBubbleController(controller: BubbleController): BubbleExpandedViewManager {
            return object : BubbleExpandedViewManager {

                override val overflowBubbles: List<Bubble>
                    get() = controller.overflowBubbles

                override fun setOverflowListener(listener: BubbleData.Listener) {
                    controller.setOverflowListener(listener)
                }

                override fun collapseStack() {
                    controller.collapseStack()
                }

                override fun updateWindowFlagsForBackpress(intercept: Boolean) {
                    controller.updateWindowFlagsForBackpress(intercept)
                }

                override fun promoteBubbleFromOverflow(bubble: Bubble) {
                    controller.promoteBubbleFromOverflow(bubble)
                }

                override fun removeBubble(key: String, reason: Int) {
                    controller.removeBubble(key, reason)
                }

                override fun dismissBubble(bubble: Bubble, reason: Int) {
                    controller.dismissBubble(bubble, reason)
                }

                override fun setNoteBubbleTaskId(key: String, taskId: Int) {
                    controller.setNoteBubbleTaskId(key, taskId)
                }

                override fun isStackExpanded(): Boolean = controller.isStackExpanded

                override fun isShowingAsBubbleBar(): Boolean = controller.isShowingAsBubbleBar

                override fun hideCurrentInputMethod() {
                    controller.hideCurrentInputMethod(null)
                }

                override fun updateBubbleBarLocation(
                    location: BubbleBarLocation,
                    @BubbleBarLocation.UpdateSource source: Int,
                ) {
                    controller.setBubbleBarLocation(location, source)
                }

                override fun getAppBubbleRootTaskToken(): WindowContainerToken? =
                    controller.appBubbleRootTaskInfo?.token

                override fun shouldBeAppBubble(taskInfo: ActivityManager.RunningTaskInfo): Boolean =
                    controller.shouldBeAppBubble(taskInfo)
            }
        }
    }
}
