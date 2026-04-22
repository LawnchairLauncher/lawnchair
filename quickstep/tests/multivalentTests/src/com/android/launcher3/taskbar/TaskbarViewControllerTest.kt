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

package com.android.launcher3.taskbar

import android.view.View
import com.android.launcher3.taskbar.TaskbarViewController.DIVIDER_VIEW_POSITION_OFFSET
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
/**
 * Legend for the comments below:
 * ```
 * A: All Apps Button
 * H: Hotseat item
 * |: Divider
 * R: Recent item
 * ```
 *
 * The comments are formatted in two lines:
 * ```
 * // Items in taskbar, e.g.               A  |  HHHHHH
 * // Index of items relative to Hotseat: -1 -.5 012345
 * ```
 */
class TaskbarViewControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var taskbarViewController: TaskbarViewController

    @Test
    fun testGetPositionInHotseat_allAppsButton_nonRtl() {
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ 6,
                /* child = */ View(context),
                /* isRtl = */ false,
                /* isAllAppsButton = */ true,
                /* isTaskbarDividerView = */ false,
                /* isDividerForRecents = */ false,
                /* recentTaskIndex = */ -1,
            )
        // [>A<] | [HHHHHH]
        //  -1 -.5  012345
        assertThat(position).isEqualTo(-1)
    }

    @Test
    fun testGetPositionInHotseat_allAppsButton_rtl() {
        val numShownHotseatIcons = 6
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ numShownHotseatIcons,
                /* child = */ View(context),
                /* isRtl = */ true,
                /* isAllAppsButton = */ true,
                /* isTaskbarDividerView = */ false,
                /* isDividerForRecents = */ false,
                /* recentTaskIndex = */ -1,
            )
        // [HHHHHH] | [>A<]
        //  012345 5.5  6
        assertThat(position).isEqualTo(numShownHotseatIcons)
    }

    @Test
    fun testGetPositionInHotseat_dividerView_notForRecents_nonRtl() {
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ 6,
                /* child = */ View(context),
                /* isRtl = */ false,
                /* isAllAppsButton = */ false,
                /* isTaskbarDividerView = */ true,
                /* isDividerForRecents = */ false,
                /* recentTaskIndex = */ -1,
            )
        // [A] >|< [HHHHHH]
        // -1  -.5  012345
        assertThat(position).isEqualTo(-DIVIDER_VIEW_POSITION_OFFSET)
    }

    @Test
    fun testGetPositionInHotseat_dividerView_forRecents_nonRtl() {
        val numShownHotseatIcons = 6
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ numShownHotseatIcons,
                /* child = */ View(context),
                /* isRtl = */ false,
                /* isAllAppsButton = */ false,
                /* isTaskbarDividerView = */ true,
                /* isDividerForRecents = */ true,
                /* recentTaskIndex = */ -1,
            )
        // [A] [HHHHHH] >|< [RR]
        // -1   012345  5.5  67
        assertThat(position).isEqualTo(numShownHotseatIcons - DIVIDER_VIEW_POSITION_OFFSET)
    }

    @Test
    fun testGetPositionInHotseat_dividerView_notForRecents_rtl() {
        val numShownHotseatIcons = 6
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ numShownHotseatIcons,
                /* child = */ View(context),
                /* isRtl = */ true,
                /* isAllAppsButton = */ false,
                /* isTaskbarDividerView = */ true,
                /* isDividerForRecents = */ false,
                /* recentTaskIndex = */ -1,
            )
        // [HHHHHH] >|< [A]
        //  012345  5.5  6
        assertThat(position).isEqualTo(numShownHotseatIcons - DIVIDER_VIEW_POSITION_OFFSET)
    }

    @Test
    fun testGetPositionInHotseat_dividerView_forRecents_rtl() {
        val numShownHotseatIcons = 6
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ numShownHotseatIcons,
                /* child = */ View(context),
                /* isRtl = */ true,
                /* isAllAppsButton = */ false,
                /* isTaskbarDividerView = */ true,
                /* isDividerForRecents = */ true,
                /* recentTaskIndex = */ -1,
            )
        // [HHHHHH][A] >|< [RR]
        //  012345  6  6.5  78
        assertThat(position).isEqualTo(numShownHotseatIcons + DIVIDER_VIEW_POSITION_OFFSET)
    }

    @Test
    fun testGetPositionInHotseat_recentTasks_firstRecentIndex_nonRtl() {
        val numShownHotseatIcons = 6
        val recentTaskIndex = 0
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ numShownHotseatIcons,
                /* child = */ View(context),
                /* isRtl = */ false,
                /* isAllAppsButton = */ false,
                /* isTaskbarDividerView = */ false,
                /* isDividerForRecents = */ false,
                /* recentTaskIndex = */ recentTaskIndex,
            )
        // [A][HHHHHH] | [>R<R]
        // -1  012345 5.5  6 7
        assertThat(position).isEqualTo(numShownHotseatIcons + recentTaskIndex)
    }

    @Test
    fun testGetPositionInHotseat_recentTasks_secondRecentIndex_nonRtl() {
        val numShownHotseatIcons = 6
        val recentTaskIndex = 1
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ numShownHotseatIcons,
                /* child = */ View(context),
                /* isRtl = */ false,
                /* isAllAppsButton = */ false,
                /* isTaskbarDividerView = */ false,
                /* isDividerForRecents = */ false,
                /* recentTaskIndex = */ recentTaskIndex,
            )
        // [A][HHHHHH] | [R>R<]
        // -1  012345 5.5 6 7
        assertThat(position).isEqualTo(numShownHotseatIcons + recentTaskIndex)
    }

    @Test
    fun testGetPositionInHotseat_recentTasks_firstRecentIndex_rtl() {
        val numShownHotseatIcons = 6
        val recentTaskIndex = 0
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ numShownHotseatIcons,
                /* child = */ View(context),
                /* isRtl = */ true,
                /* isAllAppsButton = */ false,
                /* isTaskbarDividerView = */ false,
                /* isDividerForRecents = */ false,
                /* recentTaskIndex = */ recentTaskIndex,
            )
        // [HHHHHH][A] | [>R<R]
        //  012345  6 6.5  7 8
        assertThat(position).isEqualTo(numShownHotseatIcons + 1 + recentTaskIndex)
    }

    @Test
    fun testGetPositionInHotseat_recentTasks_secondRecentIndex_rtl() {
        val numShownHotseatIcons = 6
        val recentTaskIndex = 1
        val position =
            taskbarViewController.getPositionInHotseat(
                /* numShownHotseatIcons = */ numShownHotseatIcons,
                /* child = */ View(context),
                /* isRtl = */ true,
                /* isAllAppsButton = */ false,
                /* isTaskbarDividerView = */ false,
                /* isDividerForRecents = */ false,
                /* recentTaskIndex = */ recentTaskIndex,
            )
        // [HHHHHH][A] | [R>R<]
        //  012345  6 6.5 7 8
        assertThat(position).isEqualTo(numShownHotseatIcons + 1 + recentTaskIndex)
    }
}
