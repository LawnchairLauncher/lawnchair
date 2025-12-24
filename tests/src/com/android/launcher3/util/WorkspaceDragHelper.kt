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

package com.android.launcher3.util

import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.accessibility.DragAndDropAccessibilityDelegate
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.util.Wait.atMost
import java.util.function.IntFunction

/** Utility class to help with workspace interaction */
class WorkspaceDragHelper(private val launcherRule: LauncherActivityScenarioRule<Launcher>) {

    fun flingForward() = snapToPage { panelCount: Int -> panelCount }

    fun flingBackward() = snapToPage { panelCount: Int -> -panelCount }

    fun getWorkspaceAppIcon(className: String) = ItemOperator { info, _ ->
        info?.container == CONTAINER_DESKTOP && info.className() == className
    }

    fun getHotseatAppIcon(className: String) = ItemOperator { info, _ ->
        info?.container == CONTAINER_HOTSEAT && info.className() == className
    }

    fun dragIcon(icon: ItemOperator, pageDelta: Int) {
        launcherRule.executeOnLauncher { launcher ->
            val view = launcher.workspace.mapOverItems(icon)!!
            launcher.accessibilityDelegate.performAccessibilityAction(view, R.id.action_move, null)
        }
        UiDevice.getInstance(getInstrumentation()).waitForIdle()
        val targetPage = snapToPage { pageDelta }

        launcherRule.executeOnLauncher { launcher ->
            val layout = launcher.workspace.getPageAt(targetPage) as CellLayout
            val dragDelegate =
                ViewCompat.getAccessibilityDelegate(layout) as DragAndDropAccessibilityDelegate

            val virtualViews = mutableListOf<Int>()
            dragDelegate.getVisibleVirtualViews(virtualViews)
            // Find an empty spot and drop the icon there
            val virtualViewId =
                virtualViews.find { id ->
                    !layout.isOccupied(id % layout.countX, id / layout.countX)
                }!!
            dragDelegate.onPerformActionForVirtualView(
                virtualViewId,
                AccessibilityNodeInfoCompat.ACTION_CLICK,
                null,
            )
        }

        atMost(
            "Launcher didn't return to normal",
            { launcherRule.getFromLauncher { l: Launcher -> l.isInState(LauncherState.NORMAL) }!! },
        )
        UiDevice.getInstance(getInstrumentation()).waitForIdle()
    }

    private fun snapToPage(panelCountToDelta: IntFunction<Int>): Int {
        var targetPage: Int = -1
        launcherRule.executeOnLauncher { launcher ->
            val workspace = launcher.workspace
            targetPage = workspace.currentPage + panelCountToDelta.apply(workspace.panelCount)
            workspace.snapToPageImmediately(targetPage)
        }
        atMost(
            "Launcher didn't scroll",
            {
                launcherRule.getFromLauncher { l ->
                    l.workspace.visiblePageIndices.contains(targetPage)
                }!!
            },
        )
        UiDevice.getInstance(getInstrumentation()).waitForIdle()
        return targetPage
    }

    companion object {

        @JvmStatic fun ItemInfo?.className() = this?.targetComponent?.className
    }
}
