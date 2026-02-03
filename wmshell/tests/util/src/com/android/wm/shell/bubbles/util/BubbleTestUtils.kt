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

import android.app.WindowConfiguration
import android.graphics.Rect
import android.os.IBinder
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.Change.CHANGE_LAUNCH_NEXT_TO_BUBBLE
import android.window.WindowContainerTransaction.HierarchyOp
import com.google.common.truth.Truth.assertThat

object BubbleTestUtils {

    /** Verifies the [WindowContainerTransaction] to enter Bubble. */
    @JvmOverloads
    @JvmStatic
    fun verifyEnterBubbleTransaction(
        wct: WindowContainerTransaction,
        taskToken: IBinder,
        isAppBubble: Boolean,
        reparentToTda: Boolean = false,
    ) {
        // Verify hierarchy ops

        val alwaysOnTopOp: HierarchyOp

        if (reparentToTda) {
            assertThat(wct.hierarchyOps.size).isEqualTo(2)
            val reparentOp = wct.hierarchyOps[0]
            assertThat(reparentOp.container).isEqualTo(taskToken)
            assertThat(reparentOp.isReparent).isTrue()
            assertThat(reparentOp.newParent).isNull()
            assertThat(reparentOp.toTop).isTrue()

            alwaysOnTopOp = wct.hierarchyOps[1]
        } else {
            assertThat(wct.hierarchyOps.size).isEqualTo(1)
            alwaysOnTopOp = wct.hierarchyOps[0]
        }
        assertThat(alwaysOnTopOp.container).isEqualTo(taskToken)
        assertThat(alwaysOnTopOp.isAlwaysOnTop).isTrue()

        // Verify Change

        assertThat(wct.changes.size).isEqualTo(1)
        assertThat(wct.changes[taskToken]).isNotNull()
        val change = wct.changes[taskToken]!!
        assertThat(change.windowingMode).isEqualTo(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW)
        if (isAppBubble) {
            assertThat(change.launchNextToBubble).isTrue()
        } else {
            assertThat(change.changeMask and CHANGE_LAUNCH_NEXT_TO_BUBBLE).isEqualTo(0)
        }
        assertThat(change.forceExcludedFromRecents).isTrue()
        assertThat(change.disablePip).isTrue()
        assertThat(change.disableLaunchAdjacent).isTrue()
        assertThat(change.interceptBackPressed).isTrue()
    }

    /** Verifies the [WindowContainerTransaction] to exit Bubble. */
    @JvmStatic
    fun verifyExitBubbleTransaction(
        wct: WindowContainerTransaction, taskToken: IBinder, captionInsetsOwner: IBinder?
    ) {
        // Verify hierarchy ops

        // If there is a caption insets owner set, then that will add an hierarchy op after the
        // alwaysOnTop hierarchy op to remove the insets source.
        if (captionInsetsOwner != null) {
            assertThat(wct.hierarchyOps.size).isEqualTo(2)

            val removeInsetsHierarchyOp = wct.hierarchyOps[1]
            assertThat(removeInsetsHierarchyOp.container).isEqualTo(taskToken)
            assertThat(removeInsetsHierarchyOp.insetsFrameProvider).isNotNull()
            assertThat(removeInsetsHierarchyOp.caller).isEqualTo(captionInsetsOwner)
        } else {
            assertThat(wct.hierarchyOps.size).isEqualTo(1)
        }

        val alwaysOnTopHierarchyOp = wct.hierarchyOps[0]
        assertThat(alwaysOnTopHierarchyOp.container).isEqualTo(taskToken)
        assertThat(alwaysOnTopHierarchyOp.isAlwaysOnTop).isFalse()

        // Verify Change

        assertThat(wct.changes.size).isEqualTo(1)
        assertThat(wct.changes[taskToken]).isNotNull()
        val change = wct.changes[taskToken]!!
        assertThat(change.windowingMode).isEqualTo(WindowConfiguration.WINDOWING_MODE_UNDEFINED)
        assertThat(change.launchNextToBubble).isFalse()
        assertThat(change.forceExcludedFromRecents).isFalse()
        assertThat(change.disablePip).isFalse()
        assertThat(change.disableLaunchAdjacent).isFalse()
        assertThat(change.configuration.windowConfiguration.bounds).isEqualTo(Rect())
        assertThat(change.interceptBackPressed).isFalse()
    }
}