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

import java.util.function.Consumer

/** Defines callbacks from [BubbleStackView] to its manager. */
interface BubbleStackViewManager {

    /** Notifies that all bubbles animated out. */
    fun onAllBubblesAnimatedOut()

    /** Notifies whether backpress should be intercepted. */
    fun updateWindowFlagsForBackpress(interceptBack: Boolean)

    /**
     * Checks the current expansion state of the notification panel, and invokes [callback] with the
     * result.
     */
    fun checkNotificationPanelExpandedState(callback: Consumer<Boolean>)

    /** Requests to hide the current input method. */
    fun hideCurrentInputMethod()

    companion object {

        @JvmStatic
        fun fromBubbleController(controller: BubbleController) = object : BubbleStackViewManager {
            override fun onAllBubblesAnimatedOut() {
                controller.onAllBubblesAnimatedOut()
            }

            override fun updateWindowFlagsForBackpress(interceptBack: Boolean) {
                controller.updateWindowFlagsForBackpress(interceptBack)
            }

            override fun checkNotificationPanelExpandedState(callback: Consumer<Boolean>) {
                controller.isNotificationPanelExpanded(callback)
            }

            override fun hideCurrentInputMethod() {
                controller.hideCurrentInputMethod()
            }
        }
    }
}
