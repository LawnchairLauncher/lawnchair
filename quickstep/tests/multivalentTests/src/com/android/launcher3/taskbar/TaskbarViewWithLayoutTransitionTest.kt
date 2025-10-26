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

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.View
import com.android.launcher3.Flags.FLAG_TASKBAR_OVERFLOW
import com.android.launcher3.R
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarIconType.ALL_APPS
import com.android.launcher3.taskbar.TaskbarIconType.DIVIDER
import com.android.launcher3.taskbar.TaskbarIconType.HOTSEAT
import com.android.launcher3.taskbar.TaskbarIconType.OVERFLOW
import com.android.launcher3.taskbar.TaskbarIconType.RECENT
import com.android.launcher3.taskbar.TaskbarViewTestUtil.assertThat
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createRecents
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.ForceRtl
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.window.flags.Flags.FLAG_ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
@EnableFlags(FLAG_ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION, FLAG_TASKBAR_OVERFLOW)
class TaskbarViewWithLayoutTransitionTest {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    private lateinit var taskbarView: TaskbarView

    private val iconViews: Array<View>
        get() = taskbarView.iconViews

    private val desktopVisibilityController: DesktopVisibilityController
        get() = DesktopVisibilityController.INSTANCE[context]

    private val maxShownRecents: Int
        get() = taskbarView.maxNumIconViews - 2 // Account for All Apps and Divider.

    @Before
    fun obtainView() {
        taskbarView = taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_hotseatItems_hasDividerBetweenHotseatAndAllApps() {
        runOnMainSync { taskbarView.updateItems(createHotseatItems(2), emptyList()) }
        assertThat(taskbarView).hasIconTypes(*HOTSEAT * 2, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_recentsItems_hasDividerBetweenRecentsAndAllApps() {
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(4)) }
        assertThat(taskbarView).hasIconTypes(*RECENT * 4, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_recentsItems_recentsAreReversed() {
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(4)) }
        assertThat(taskbarView).hasRecentsOrder(startIndex = 0, expectedIds = listOf(3, 2, 1, 0))
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_hotseatItemsAndRecents_hasDividerBetweenRecentsAndHotseat() {
        runOnMainSync { taskbarView.updateItems(createHotseatItems(3), createRecents(2)) }
        assertThat(taskbarView).hasIconTypes(*RECENT * 2, DIVIDER, *HOTSEAT * 3, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addHotseatItemWithoutRecents_updatesHotseat() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList())
            taskbarView.updateItems(createHotseatItems(2), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(*HOTSEAT * 2, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addHotseatItemWithRecents_updatesHotseat() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
            taskbarView.updateItems(createHotseatItems(2), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, *HOTSEAT * 2, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeHotseatItem_updatesHotseat() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(2), createRecents(1))
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, HOTSEAT, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addRecentsItem_updatesRecents() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
            taskbarView.updateItems(createHotseatItems(1), createRecents(2))
        }
        assertThat(taskbarView).hasIconTypes(*RECENT * 2, DIVIDER, HOTSEAT, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeRecentsItem_updatesRecents() {
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(2))
            taskbarView.updateItems(createHotseatItems(1), createRecents(1))
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, HOTSEAT, ALL_APPS)
    }

    @Test
    fun testUpdateItems_addRecentsItem_viewAddedOnRight() {
        runOnMainSync {
            taskbarView.updateItems(emptyArray(), createRecents(1))
            val prevIconViews = iconViews

            val newRecents = createRecents(2)
            taskbarView.updateItems(emptyArray(), newRecents)

            assertThat(taskbarView).hasRecentsOrder(startIndex = 2, expectedIds = listOf(0, 1))
            assertThat(iconViews[2]).isSameInstanceAs(prevIconViews[2])
            assertThat(iconViews.last() in prevIconViews).isFalse()
        }
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addRecentsItem_viewAddedOnLeft() {
        runOnMainSync {
            taskbarView.updateItems(emptyArray(), createRecents(1))
            val prevIconViews = iconViews

            val newRecents = createRecents(2)
            taskbarView.updateItems(emptyArray(), newRecents)

            assertThat(taskbarView).hasRecentsOrder(startIndex = 0, expectedIds = listOf(1, 0))
            assertThat(iconViews[1]).isSameInstanceAs(prevIconViews.first())
            assertThat(iconViews.first() in prevIconViews).isFalse()
        }
    }

    @Test
    fun testUpdateItems_removeFirstRecentsItem_correspondingViewRemoved() {
        runOnMainSync {
            val recents = createRecents(2)
            taskbarView.updateItems(emptyArray(), recents)

            val expectedViewToRemove = iconViews[2]
            assertThat(expectedViewToRemove.tag).isEqualTo(recents.first())

            taskbarView.updateItems(emptyArray(), listOf(recents.last()))
            assertThat(expectedViewToRemove in iconViews).isFalse()
        }
    }

    @Test
    fun testUpdateItems_removeLastRecentsItem_correspondingViewRemoved() {
        runOnMainSync {
            val recents = createRecents(2)
            taskbarView.updateItems(emptyArray(), recents)

            val expectedViewToRemove = iconViews[3]
            assertThat(expectedViewToRemove.tag).isEqualTo(recents.last())

            taskbarView.updateItems(emptyArray(), listOf(recents.first()))
            assertThat(expectedViewToRemove in iconViews).isFalse()
        }
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeFirstRecentsItem_correspondingViewRemoved() {
        runOnMainSync {
            val recents = createRecents(2)
            taskbarView.updateItems(emptyArray(), recents)

            val expectedViewToRemove = iconViews[1]
            assertThat(expectedViewToRemove.tag).isEqualTo(recents.first())

            taskbarView.updateItems(emptyArray(), listOf(recents.last()))
            assertThat(expectedViewToRemove in iconViews).isFalse()
        }
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeLastRecentsItem_correspondingViewRemoved() {
        runOnMainSync {
            val recents = createRecents(2)
            taskbarView.updateItems(emptyArray(), recents)

            val expectedViewToRemove = iconViews[0]
            assertThat(expectedViewToRemove.tag).isEqualTo(recents.last())

            taskbarView.updateItems(emptyArray(), listOf(recents.first()))
            assertThat(expectedViewToRemove in iconViews).isFalse()
        }
    }

    @Test
    fun testUpdateItems_desktopMode_hotseatItem_noDivider() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnMainSync { taskbarView.updateItems(createHotseatItems(1), emptyList()) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtlAndDesktopMode_hotseatItem_noDivider() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnMainSync { taskbarView.updateItems(createHotseatItems(1), emptyList()) }
        assertThat(taskbarView).hasIconTypes(HOTSEAT, ALL_APPS)
    }

    @Test
    fun testUpdateItems_desktopMode_recentItem_hasDivider() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(1)) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, RECENT)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtlAndDesktopMode_recentItem_hasDivider() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(1)) }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, ALL_APPS)
    }

    @Test
    fun testUpdateItems_maxRecents_noOverflow() {
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(maxShownRecents)) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, *RECENT * maxShownRecents)
    }

    @Test
    fun testUpdateItems_moreThanMaxRecents_overflowShownBeforeRecents() {
        val recentsSize = maxShownRecents + 2
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(recentsSize)) }

        val expectedNumRecents = RECENT * getExpectedNumRecentsWithOverflow()
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, OVERFLOW, *expectedNumRecents)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_moreThanMaxRecents_overflowShownAfterRecents() {
        val recentsSize = maxShownRecents + 2
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(recentsSize)) }

        val expectedRecents = RECENT * getExpectedNumRecentsWithOverflow()
        assertThat(taskbarView).hasIconTypes(*expectedRecents, OVERFLOW, DIVIDER, ALL_APPS)
    }

    @Test
    fun testUpdateItems_moreThanMaxRecentsWithHotseat_fewerRecentsShown() {
        val hotseatSize = 4
        val recentsSize = maxShownRecents + 2
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(hotseatSize), createRecents(recentsSize))
        }

        val expectedRecents = RECENT * getExpectedNumRecentsWithOverflow(hotseatSize)
        assertThat(taskbarView)
            .hasIconTypes(ALL_APPS, *HOTSEAT * hotseatSize, DIVIDER, OVERFLOW, *expectedRecents)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_moreThanMaxRecentsWithHotseat_fewerRecentsShown() {
        val hotseatSize = 4
        val recentsSize = maxShownRecents + 2
        runOnMainSync {
            taskbarView.updateItems(createHotseatItems(hotseatSize), createRecents(recentsSize))
        }

        val expectedRecents = RECENT * getExpectedNumRecentsWithOverflow(hotseatSize)
        assertThat(taskbarView)
            .hasIconTypes(*expectedRecents, OVERFLOW, DIVIDER, *HOTSEAT * hotseatSize, ALL_APPS)
    }

    @Test
    fun testUpdateItems_moreThanMaxRecents_verifyShownRecentsOrder() {
        val recentsSize = maxShownRecents + 2
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(recentsSize)) }

        val expectedNumRecents = getExpectedNumRecentsWithOverflow()
        assertThat(taskbarView)
            .hasRecentsOrder(
                startIndex = iconViews.size - expectedNumRecents,
                expectedIds = ((recentsSize - expectedNumRecents)..<recentsSize).toList(),
            )
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_moreThanMaxRecents_verifyShownRecentsReversed() {
        val recentsSize = maxShownRecents + 2
        runOnMainSync { taskbarView.updateItems(emptyArray(), createRecents(recentsSize)) }

        val expectedNumRecents = getExpectedNumRecentsWithOverflow()
        assertThat(taskbarView)
            .hasRecentsOrder(
                startIndex = 0,
                expectedIds = ((recentsSize - expectedNumRecents)..<recentsSize).toList().reversed(),
            )
    }

    /** Returns the number of expected recents outside of the overflow based on [hotseatSize]. */
    private fun getExpectedNumRecentsWithOverflow(hotseatSize: Int = 0): Int {
        return maxShownRecents - hotseatSize - 1 // Account for overflow.
    }
}
