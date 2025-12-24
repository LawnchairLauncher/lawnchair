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

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.SparseArray
import android.view.View
import androidx.core.util.size
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.customization.TASKBAR_OVERFLOW_PIN_LIMIT
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.TestUtil.getOnUiThread
import com.android.window.flags.Flags.FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [PinToTaskbarShortcut]. */
@RunWith(LauncherMultivalentJUnit::class)
class PinToTaskbarShortcutTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()

    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    private val taskbarActivityContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private val pinnedInfoList = SparseArray<ItemInfo?>()
    private lateinit var view: View

    @Before
    fun setUp() {
        runOnMainSync { view = View(taskbarActivityContext) }
    }

    @Test
    fun testUnpin_removeItemFromTaskbar() {
        populatePinnedInfoList(0)
        assertThat(pinnedInfoList.size).isEqualTo(1)
        val shortcut = createShortcut(false, pinnedInfoList[0] ?: ItemInfo())
        mockUnpinItem(shortcut)

        runOnMainSync { shortcut.onClick(null) }

        verify(shortcut).unpinItem(anyOrNull(), anyOrNull())
        assertThat(pinnedInfoList.size).isEqualTo(0)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testPin_noOverflow_pinsToEnd() {
        populatePinnedInfoList(0, 1, 2)
        val newItemInfo = createItemInfoToPin()
        val shortcut = createShortcut(true, newItemInfo)
        mockPinItem(shortcut)

        runOnMainSync { shortcut.onClick(null) }

        verify(shortcut).pinItem(anyOrNull(), anyOrNull(), any(), eq(3), any())
        assertThat(pinnedInfoList.size).isEqualTo(4)
        assertThat(pinnedInfoList[3]?.targetComponent).isEqualTo(newItemInfo.componentName)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testPin_noOverflow_compactsAndPinsToEnd() {
        // Create a list of pinned info where the last space has a pinned item.
        populatePinnedInfoList(
            0,
            3,
            taskbarActivityContext.taskbarSpecsEvaluator.numShownHotseatIcons - 1,
        )
        val newItemInfo = createItemInfoToPin()
        val shortcut = createShortcut(true, newItemInfo)
        mockPinItem(shortcut)

        runOnMainSync { shortcut.onClick(null) }

        // The pinned items are compacted and the new item is pinned to the end
        verify(shortcut).pinItem(anyOrNull(), anyOrNull(), any(), eq(3), any())

        assertThat(pinnedInfoList.size).isEqualTo(4)
        assertThat(pinnedInfoList[3]?.targetComponent).isEqualTo(newItemInfo.componentName)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testPin_noOverflow_emptyTaskbar_pinsToIndex0() {
        val newItemInfo = createItemInfoToPin()
        val shortcut = createShortcut(true, newItemInfo)
        mockPinItem(shortcut)

        runOnMainSync { shortcut.onClick(null) }

        verify(shortcut).pinItem(anyOrNull(), anyOrNull(), any(), eq(0), any())
        assertThat(pinnedInfoList.size).isEqualTo(1)
        assertThat(pinnedInfoList[0]?.targetComponent).isEqualTo(newItemInfo.componentName)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testPin_withOverflow_pinsToNextAvailableIndex() {
        populatePinnedInfoList(2, TASKBAR_OVERFLOW_PIN_LIMIT - 1)
        val newItemInfo = createItemInfoToPin()
        val shortcut = createShortcut(true, newItemInfo)
        mockPinItem(shortcut)

        runOnMainSync { shortcut.onClick(null) }

        // The new item should be pinned to index 2, as it's the first available slot.
        verify(shortcut).pinItem(anyOrNull(), anyOrNull(), any(), eq(2), any())
        assertThat(pinnedInfoList.size).isEqualTo(3)
        assertThat(pinnedInfoList[2]?.targetComponent).isEqualTo(newItemInfo.componentName)
    }

    private fun createItemInfoToPin(): AppInfo {
        return AppInfo().apply {
            user = UserHandle.CURRENT
            componentName = ComponentName(taskbarActivityContext, "new.test.class")
            intent = Intent().setComponent(componentName)
        }
    }

    private fun createShortcut(
        isPin: Boolean,
        itemInfo: ItemInfo,
    ): PinToTaskbarShortcut<TaskbarActivityContext> {
        return getOnUiThread {
            spy(PinToTaskbarShortcut(taskbarActivityContext, itemInfo, view, isPin, pinnedInfoList))
        }
    }

    private fun populatePinnedInfoList(vararg positions: Int) {
        for (pos in positions) {
            val info =
                AppInfo().apply {
                    screenId = pos
                    cellX = pos
                    user = UserHandle.CURRENT
                    componentName = ComponentName(taskbarActivityContext, "test.class.$pos")
                    intent = Intent().setComponent(componentName)
                }
            pinnedInfoList.put(pos, info.makeWorkspaceItem(context))
        }
    }

    private fun mockPinItem(shortcut: PinToTaskbarShortcut<TaskbarActivityContext>) {
        doAnswer {
                runOnMainSync {
                    val newInfo = it.getArgument<WorkspaceItemInfo>(1)
                    val cellX = it.getArgument<Int>(3)
                    pinnedInfoList.put(cellX, newInfo)
                }
                null
            }
            .whenever(shortcut)
            .pinItem(anyOrNull(), anyOrNull(), any(), any(), any())
    }

    private fun mockUnpinItem(shortcut: PinToTaskbarShortcut<TaskbarActivityContext>) {
        doAnswer {
                runOnMainSync {
                    val infoToUnpin = it.getArgument<ItemInfo>(1)
                    for (i in 0 until pinnedInfoList.size) {
                        if (
                            pinnedInfoList.valueAt(i)?.targetComponent ==
                                infoToUnpin?.targetComponent
                        ) {
                            pinnedInfoList.removeAt(i)
                            break
                        }
                    }
                }
                null
            }
            .whenever(shortcut)
            .unpinItem(anyOrNull(), anyOrNull())
    }
}
