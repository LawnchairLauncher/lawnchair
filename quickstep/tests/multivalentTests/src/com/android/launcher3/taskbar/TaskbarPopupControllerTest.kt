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

package com.android.launcher3.taskbar

import android.platform.test.annotations.DisableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR
import com.android.launcher3.Flags.FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU
import com.android.launcher3.R
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatWorkspaceItem
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createRecents
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.quickstep.util.GroupTask
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
@DisableFlags(FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR, FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU)
class TaskbarPopupControllerTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()

    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var popupController: TaskbarPopupController

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private lateinit var taskbarView: TaskbarView
    private lateinit var hotseatIcon: BubbleTextView
    private lateinit var recentTaskIcon: BubbleTextView

    @Before
    fun setup() {
        taskbarContext.controllers.uiController.init(taskbarContext.controllers)
        runOnMainSync { taskbarView = taskbarContext.dragLayer.findViewById(R.id.taskbar_view) }

        val hotseatItems = arrayOf(createHotseatWorkspaceItem())
        val recentItems = createRecents(2)
        runOnMainSync {
            taskbarView.updateItems(hotseatItems, recentItems)
            hotseatIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            recentTaskIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is GroupTask
                }
        }
    }

    @Test
    fun showForIcon_hotseatItem() {
        assertThat(hasPopupMenu()).isFalse()
        runOnMainSync { popupController.showForIcon(hotseatIcon) }
        assertThat(hasPopupMenu()).isTrue()
    }

    @Test
    fun showForIcon_recentTask() {
        assertThat(hasPopupMenu()).isFalse()
        runOnMainSync { popupController.showForIcon(recentTaskIcon) }
        assertThat(hasPopupMenu()).isTrue()
    }

    private fun hasPopupMenu(): Boolean {
        return AbstractFloatingView.hasOpenView(
            taskbarContext,
            AbstractFloatingView.TYPE_ACTION_POPUP,
        )
    }
}
