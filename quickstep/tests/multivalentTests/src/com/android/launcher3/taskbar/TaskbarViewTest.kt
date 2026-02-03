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

import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.FlagsParameterization.allCombinationsOf
import android.platform.test.flag.junit.SetFlagsRule
import com.android.launcher3.R
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarIconType.ALL_APPS
import com.android.launcher3.taskbar.TaskbarIconType.DIVIDER
import com.android.launcher3.taskbar.TaskbarIconType.HOTSEAT
import com.android.launcher3.taskbar.TaskbarIconType.RECENT
import com.android.launcher3.taskbar.TaskbarViewTestUtil.assertThat
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createRecents
import com.android.launcher3.taskbar.rules.TaskbarDeviceEmulationRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.ForceRtl
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit.Companion.isRunningInRobolectric
import com.android.window.flags2.Flags.FLAG_ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
class TaskbarViewTest(deviceName: String, flags: FlagsParameterization) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0},{1}")
        fun getParams(): List<Array<Any>> {
            val devices =
                if (isRunningInRobolectric) {
                    listOf("pixelFoldable2023", "pixelTablet2023")
                } else {
                    listOf("onDevice") // Unused.
                }
            val flags = allCombinationsOf(FLAG_ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION)
            return devices.flatMap { d -> flags.map { f -> arrayOf(d, f) } } // Cartesian product.
        }
    }

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule(flags)
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val deviceEmulationRule = TaskbarDeviceEmulationRule(context, deviceName)
    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    private lateinit var taskbarView: TaskbarView

    @Before
    fun obtainView() {
        taskbarView = taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
    }

    @Test
    fun testUpdateItems_noItems_hasOnlyAllApps() {
        runOnMainSync { taskbarView.updateItems(emptyArray(), emptyList()) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS)
    }

    @Test
    fun testUpdateItems_hotseatItems_hasDividerBetweenAllAppsAndHotseat() {
        runOnMainSync { taskbarView.updateItems(createHotseatItems(2), emptyList()) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, HOTSEAT, HOTSEAT)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtlWithHotseatItems_hasDividerBetweenHotseatAndAllApps() {
        runOnMainSync { taskbarView.updateItems(createHotseatItems(2), emptyList()) }
        assertThat(taskbarView).hasIconTypes(HOTSEAT, HOTSEAT, DIVIDER, ALL_APPS)
    }

    @Test
    fun testUpdateItems_withNullHotseatItem_filtersNullItem() {
        runOnMainSync {
            taskbarView.updateItems(arrayOf(*createHotseatItems(2), null), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, HOTSEAT, HOTSEAT)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtlWithNullHotseatItem_filtersNullItem() {
        runOnMainSync {
            taskbarView.updateItems(arrayOf(*createHotseatItems(2), null), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(HOTSEAT, HOTSEAT, DIVIDER, ALL_APPS)
    }

    @Test
    fun testUpdateItems_recentsItems_hasDividerBetweenAllAppsAndRecents() {
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(4)) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, *RECENT * 4)
    }

    @Test
    fun testUpdateItems_hotseatItemsAndRecents_hasDividerBetweenHotseatAndRecents() {
        runOnMainSync { taskbarView.updateItems(createHotseatItems(3), createRecents(2)) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, *HOTSEAT * 3, DIVIDER, *RECENT * 2)
    }

    @Test
    fun testUpdateItems_addHotseatItem_updatesHotseat() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
            taskbarView.updateItems(createHotseatItems(2), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, *HOTSEAT * 2, DIVIDER, RECENT)
    }

    @Test
    fun testUpdateItems_removeHotseatItem_updatesHotseat() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(2), createRecents(1))
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, RECENT)
    }

    @Test
    fun testUpdateItems_addRecentsItem_updatesRecents() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
            taskbarView.updateItems(createHotseatItems(1), createRecents(2))
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, *RECENT * 2)
    }

    @Test
    fun testUpdateItems_removeRecentsItem_updatesRecents() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(2))
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, RECENT)
    }

    @Test
    fun testUpdateItem_addHotseatItemAfterRecentsItem_hotseatItemBeforeDivider() {
        runOnMainSync {
            taskbarView.updateItems(emptyArray(), createRecents(1))
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, RECENT)
    }
}
