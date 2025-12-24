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

package com.android.wm.shell.bubbles.util

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.graphics.Rect
import android.os.Binder
import android.view.WindowInsets
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.splitscreen.SplitScreenController
import dagger.Lazy
import java.util.Optional

object BubbleUtils {

    /**
     * Returns a [WindowContainerTransaction] that includes the necessary operations of entering or
     * exiting Bubble.
     */
    private fun getBubbleTransaction(
        token: WindowContainerToken,
        rootToken: WindowContainerToken?,
        bounds: Rect,
        toBubble: Boolean,
        isAppBubble: Boolean,
        reparentToTda: Boolean,
        captionInsetsOwner: Binder?,
    ): WindowContainerTransaction {
        val wct = WindowContainerTransaction()
        if (BubbleAnythingFlagHelper.enableRootTaskForBubble() && isAppBubble) {
            if (toBubble && rootToken != null) {
                wct.reparent(token, rootToken, true /* onTop */)
                wct.setBounds(rootToken, bounds)
                wct.setAlwaysOnTop(rootToken, true /* alwaysOnTop */)
            } else {
                wct.reparent(token, null, true /* onTop */)
            }
        } else {
            if (reparentToTda) {
                // Reparenting must happen before setAlwaysOnTop() below since WCT operations are
                // applied in order and always-on-top for nested tasks is not supported
                wct.reparent(token, null, true)
            }
            wct.setWindowingMode(
                token,
                if (toBubble)
                    WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
                else
                    WindowConfiguration.WINDOWING_MODE_UNDEFINED,
            )
            wct.setInterceptBackPressedOnTaskRoot(token, toBubble)
            wct.setTaskForceExcludedFromRecents(token, toBubble /* forceExcluded */)
            wct.setDisablePip(token, toBubble /* disablePip */)
            if (!isAppBubble || !BubbleAnythingFlagHelper.enableRootTaskForBubble()) {
                wct.setAlwaysOnTop(token, toBubble /* alwaysOnTop */)
            }
            if (!toBubble || isAppBubble) {
                // We only set launch next to Bubble for App Bubble, since new Task opened from Chat
                // Bubble should be launched in fullscreen.
                // Always reset everything when exit bubble.
                wct.setLaunchNextToBubble(token, toBubble /* launchNextToBubble */)
            }
            if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
                wct.setDisableLaunchAdjacent(token, toBubble /* disableLaunchAdjacent */)
            }
        }
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            if (!toBubble) {
                // Clear bounds if moving out of Bubble.
                wct.setBounds(token, Rect())
            }
        }
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            if (!toBubble && captionInsetsOwner != null) {
                wct.removeInsetsSource(
                    token, captionInsetsOwner, 0 /* index */, WindowInsets.Type.captionBar()
                )
            }
        }
        return wct
    }

    /**
     * Returns a [WindowContainerTransaction] that includes the necessary operations of entering
     * Bubble.
     *
     * @param isAppBubble App Bubble has some different UX from Chat Bubble.
     * @param reparentToTda Whether to reparent the task to the ancestor TaskDisplayArea (for if
     *                      this task is a child of another root task)
     */
    @JvmOverloads
    @JvmStatic
    fun getEnterBubbleTransaction(
        token: WindowContainerToken,
        rootToken: WindowContainerToken?,
        bounds: Rect,
        isAppBubble: Boolean,
        reparentToTda: Boolean = false,
    ): WindowContainerTransaction {
        return getBubbleTransaction(
            token,
            rootToken,
            bounds,
            toBubble = true,
            isAppBubble,
            reparentToTda,
            captionInsetsOwner = null,
        )
    }

    /**
     * Returns a [WindowContainerTransaction] that includes the necessary operations of exiting
     * Bubble.
     */
    @JvmStatic
    fun getExitBubbleTransaction(
        token: WindowContainerToken,
        captionInsetsOwner: Binder?,
    ): WindowContainerTransaction {
        return getBubbleTransaction(
            token,
            rootToken = null,
            bounds = Rect(),
            toBubble = false,
            // Everything will be reset, so doesn't matter for exit.
            isAppBubble = true,
            reparentToTda = false,
            captionInsetsOwner,
        )
    }

    /** Returns true if the task is valid for Bubble. */
    @JvmStatic
    fun ActivityManager.RunningTaskInfo?.isValidToBubble(): Boolean {
        return this?.supportsMultiWindow == true
    }

    /** Determines if a bubble task is moving to fullscreen based on its windowing mode. */
    @JvmStatic
    fun ActivityManager.RunningTaskInfo?.isBubbleToFullscreen(): Boolean {
        return BubbleAnythingFlagHelper.enableCreateAnyBubble()
                && this?.windowingMode == WINDOWING_MODE_FULLSCREEN
    }

    /** Determines if a bubble task is moving to split-screen based on its parent task. */
    @JvmStatic
    fun ActivityManager.RunningTaskInfo?.isBubbleToSplit(
        splitScreenController: Lazy<Optional<SplitScreenController>>,
    ): Boolean {
        return this?.hasParentTask() == true && splitScreenController.get()
            .map { it.isTaskRootOrStageRoot(parentTaskId) }
            .orElse(false)
    }
}
