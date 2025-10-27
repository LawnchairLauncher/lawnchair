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

import android.animation.AnimatorTestRule
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR
import com.android.launcher3.Flags.FLAG_TASKBAR_OVERFLOW
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.taskbar.rules.DisplayControllerModule
import com.android.launcher3.taskbar.rules.MockedRecentsModelHelper
import com.android.launcher3.taskbar.rules.MockedRecentsModelTestRule
import com.android.launcher3.taskbar.rules.SandboxParams
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarSandboxComponent
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.util.TestUtil.getOnUiThread
import com.android.quickstep.RecentsModel
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.util.DesktopTask
import com.android.systemui.shared.recents.model.Task
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.android.wm.shell.desktopmode.IDesktopTaskListener
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelTablet2023"])
@EnableFlags(
    FLAG_TASKBAR_OVERFLOW,
    FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,
    FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    FLAG_ENABLE_BUBBLE_BAR,
)
@DisableFlags(FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR)
class TaskbarOverflowTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    val mockRecentsModelHelper: MockedRecentsModelHelper = MockedRecentsModelHelper()

    @get:Rule(order = 1)
    val context =
        TaskbarWindowSandboxContext.create(
            SandboxParams(
                {
                    spy(SystemUiProxy(ApplicationProvider.getApplicationContext())) { proxy ->
                        doAnswer { desktopTaskListener = it.getArgument(0) }
                            .whenever(proxy)
                            .setDesktopTaskListener(anyOrNull())
                    }
                },
                DaggerTaskbarOverflowComponent.builder()
                    .bindRecentsModel(mockRecentsModelHelper.mockRecentsModel),
            )
        )

    @get:Rule(order = 2) val recentsModel = MockedRecentsModelTestRule(mockRecentsModelHelper)

    @get:Rule(order = 3) val taskbarModeRule = TaskbarModeRule(context)

    @get:Rule(order = 4) val animatorTestRule = AnimatorTestRule(this)

    @get:Rule(order = 5)
    val taskbarUnitTestRule = TaskbarUnitTestRule(this, context, this::onControllersInitialized)

    @InjectController lateinit var taskbarViewController: TaskbarViewController
    @InjectController lateinit var recentAppsController: TaskbarRecentAppsController
    @InjectController lateinit var bubbleBarViewController: BubbleBarViewController
    @InjectController lateinit var bubbleStashController: BubbleStashController
    @InjectController lateinit var keyboardQuickSwitchController: KeyboardQuickSwitchController

    private var desktopTaskListener: IDesktopTaskListener? = null

    private var currentControllerInitCallback: () -> Unit = {}
        set(value) {
            runOnMainSync { value.invoke() }
            field = value
        }

    private fun onControllersInitialized() {
        runOnMainSync {
            if (!recentAppsController.canShowRunningApps) {
                recentAppsController.onDestroy()
                recentAppsController.canShowRunningApps = true
                recentAppsController.init(taskbarUnitTestRule.activityContext.controllers)
            }

            currentControllerInitCallback.invoke()
        }
    }

    @Before
    fun ensureRunningAppsShowing() {
        runOnMainSync { recentsModel.resolvePendingTaskRequests() }
    }

    @Test
    @TaskbarMode(PINNED)
    fun testTaskbarWithMaxNumIcons_pinned() {
        addRunningAppsAndVerifyOverflowState(0)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testTaskbarWithMaxNumIcons_transient() {
        addRunningAppsAndVerifyOverflowState(0)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOverflownTaskbar_pinned() {
        addRunningAppsAndVerifyOverflowState(5)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testOverflownTaskbar_transient() {
        addRunningAppsAndVerifyOverflowState(5)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOverflownTaskbarWithNoSpaceForRecentApps_pinned() {
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)

        // Create two "recent" desktop tasks, and then add enough hotseat items so the taskbar
        // reaches max number of items with hotseat item icons, all apps and divider icons only.
        // I.e. so all desktop tasks are in taskbar overflow.
        createDesktopTask(2)
        runOnMainSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.updateItems(
                createHotseatItems(maxNumberOfTaskbarIcons - initialIconCount),
                recentAppsController.shownTasks,
            )
        }

        // Verify that taskbar overflow view is shown (eventhough it exceeds max taskbar icons).
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumberOfTaskbarIcons + 1)
        assertThat(taskbarOverflowIconIndex).isEqualTo(maxNumberOfTaskbarIcons)
        assertThat(overflowItems).containsExactlyElementsIn(0..1)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOverflownTaskbarWithNoSpaceForRecentApps_singleRecent_pinned() {
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)

        // Create a "recent" desktop task, and then add enough hotseat items so the taskbar
        // reaches max number of items with hotseat item icons, all apps and divider icons only.
        // I.e. so the single desktop tasks is in taskbar overflow.
        createDesktopTask(1)
        runOnMainSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            val hotseatItems = createHotseatItems(maxNumberOfTaskbarIcons - initialIconCount)

            taskbarView.updateItems(
                recentAppsController.updateHotseatItemInfos(hotseatItems as Array<ItemInfo?>),
                recentAppsController.shownTasks,
            )
        }

        // Verify that recent task is shown (eventhough it exceeds max taskbar icons), and that
        // the taskbar overflow view is not added for the single recent app.
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumberOfTaskbarIcons + 1)
        assertThat(taskbarOverflowIconIndex).isEqualTo(-1)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testBubbleBarReducesTaskbarMaxNumIcons_pinned() {
        var initialMaxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(initialMaxNumIconViews).isGreaterThan(0)

        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)
        assertThat(maxNumIconViews).isLessThan(initialMaxNumIconViews)

        assertThat(taskbarIconsCentered).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testBubbleBarReducesTaskbarMaxNumIcons_transient() {
        var initialMaxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(initialMaxNumIconViews).isGreaterThan(0)

        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)
        assertThat(maxNumIconViews).isLessThan(initialMaxNumIconViews)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin)
            .isAtLeast(
                navButtonEndSpacing +
                    bubbleBarViewController.collapsedWidthWithMaxVisibleBubbles.toInt()
            )
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testBubbleBarReducesTaskbarMaxNumIcons_transientBubbleInitiallyStashed() {
        var initialMaxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(initialMaxNumIconViews).isGreaterThan(0)
        currentControllerInitCallback = {
            bubbleStashController.stashBubbleBarImmediate()
            bubbleBarViewController.setHiddenForBubbles(false)
        }

        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)
        assertThat(maxNumIconViews).isLessThan(initialMaxNumIconViews)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin)
            .isAtLeast(
                navButtonEndSpacing +
                    bubbleBarViewController.collapsedWidthWithMaxVisibleBubbles.toInt()
            )
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testStashingBubbleBarMaintainsMaxNumIcons_transient() {
        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val initialNumIcons = currentNumberOfTaskbarIcons
        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)

        runOnMainSync { bubbleStashController.stashBubbleBarImmediate() }
        assertThat(maxNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialNumIcons.coerceAtLeast(2))
    }

    @Test
    @TaskbarMode(PINNED)
    fun testHidingBubbleBarIncreasesMaxNumIcons_pinned() {
        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val initialNumIcons = currentNumberOfTaskbarIcons
        val initialMaxNumIconViews = addRunningAppsAndVerifyOverflowState(5)

        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(true) }
        runOnMainSync { animatorTestRule.advanceTimeBy(150) }

        val maxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(maxNumIconViews).isGreaterThan(initialMaxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialNumIcons.coerceAtLeast(2))

        assertThat(taskbarIconsCentered).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testHidingBubbleBarIncreasesMaxNumIcons_transient() {
        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val initialNumIcons = currentNumberOfTaskbarIcons
        val initialMaxNumIconViews = addRunningAppsAndVerifyOverflowState(5)

        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(true) }
        runOnMainSync { animatorTestRule.advanceTimeBy(150) }

        val maxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(maxNumIconViews).isGreaterThan(initialMaxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialNumIcons.coerceAtLeast(2))

        assertThat(taskbarIconsCentered).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testPressingOverflowButtonOpensKeyboardQuickSwitch() {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)

        val targetOverflowSize = 5
        val createdTasks = maxNumIconViews - initialIconCount + targetOverflowSize
        createDesktopTask(createdTasks)

        assertThat(taskbarOverflowIconIndex).isEqualTo(initialIconCount)
        tapOverflowIcon()
        // Keyboard quick switch view is shown only after list of recent task is asynchronously
        // retrieved from the recents model.
        runOnMainSync { recentsModel.resolvePendingTaskRequests() }

        assertThat(getOnUiThread { keyboardQuickSwitchController.isShownFromTaskbar }).isTrue()
        assertThat(getOnUiThread { keyboardQuickSwitchController.shownTaskIds() })
            .containsExactlyElementsIn(0..<createdTasks)

        tapOverflowIcon()
        assertThat(keyboardQuickSwitchController.isShown).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testHotseatItemTasksNotShownInRecents() {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)
        val hotseatItems = createHotseatItems(1)

        val targetOverflowSize = 5
        val createdTasks = maxNumIconViews - initialIconCount + targetOverflowSize
        createDesktopTaskWithTasksFromPackages(
            listOf("fake") +
                listOf(hotseatItems[0]?.targetPackage ?: "") +
                List(createdTasks - 2) { "fake" }
        )

        runOnMainSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.updateItems(
                recentAppsController.updateHotseatItemInfos(hotseatItems as Array<ItemInfo?>),
                recentAppsController.shownTasks,
            )
        }

        assertThat(maxNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialIconCount + hotseatItems.size)
        assertThat(overflowItems)
            .containsExactlyElementsIn(listOf(0) + (2..targetOverflowSize + 1).toList())
    }

    @Test
    @TaskbarMode(PINNED)
    fun testHotseatItemTasksNotShownInKQS() {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)
        val hotseatItems = createHotseatItems(1)

        val targetOverflowSize = 5
        val createdTasks = maxNumIconViews - initialIconCount + targetOverflowSize
        createDesktopTaskWithTasksFromPackages(
            listOf("fake") +
                listOf(hotseatItems[0]?.targetPackage ?: "") +
                List(createdTasks - 2) { "fake" }
        )

        runOnMainSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.updateItems(
                recentAppsController.updateHotseatItemInfos(hotseatItems as Array<ItemInfo?>),
                recentAppsController.shownTasks,
            )
        }

        tapOverflowIcon()
        // Keyboard quick switch view is shown only after list of recent task is asynchronously
        // retrieved from the recents model.
        runOnMainSync { recentsModel.resolvePendingTaskRequests() }

        assertThat(getOnUiThread { keyboardQuickSwitchController.isShownFromTaskbar }).isTrue()
        assertThat(getOnUiThread { keyboardQuickSwitchController.shownTaskIds() })
            .containsExactlyElementsIn(listOf(0) + (2..<createdTasks).toList())
    }

    private fun createDesktopTask(tasksToAdd: Int) {
        createDesktopTaskWithTasksFromPackages((0..<tasksToAdd).map { "fake" })
    }

    private fun createDesktopTaskWithTasksFromPackages(packages: List<String>) {
        val tasks =
            packages.mapIndexed({ index, p ->
                Task(
                    Task.TaskKey(
                        index,
                        0,
                        Intent().apply { `package` = p },
                        ComponentName(p, ""),
                        Process.myUserHandle().identifier,
                        2000,
                    )
                )
            })

        recentsModel.updateRecentTasks(listOf(DesktopTask(deskId = 0, DEFAULT_DISPLAY, tasks)))
        for (task in 1..tasks.size) {
            desktopTaskListener?.onTasksVisibilityChanged(
                context.virtualDisplay.display.displayId,
                task,
            )
        }
        runOnMainSync { recentsModel.resolvePendingTaskRequests() }
    }

    private val navButtonEndSpacing: Int
        get() {
            return taskbarUnitTestRule.activityContext.resources.getDimensionPixelSize(
                taskbarUnitTestRule.activityContext.deviceProfile.inv.inlineNavButtonsEndSpacing
            )
        }

    private val taskbarOverflowIconIndex: Int
        get() {
            return getOnUiThread {
                taskbarViewController.iconViews.indexOfFirst { it is TaskbarOverflowView }
            }
        }

    private val maxNumberOfTaskbarIcons: Int
        get() = getOnUiThread { taskbarViewController.maxNumIconViews }

    private val currentNumberOfTaskbarIcons: Int
        get() = getOnUiThread { taskbarViewController.iconViews.size }

    private val taskbarIconsCentered: Boolean
        get() {
            return getOnUiThread {
                val iconLayoutBounds =
                    taskbarViewController.transientTaskbarIconLayoutBoundsInParent
                val availableWidth = taskbarUnitTestRule.activityContext.deviceProfile.widthPx
                iconLayoutBounds.left - (availableWidth - iconLayoutBounds.right) < 2
            }
        }

    private val taskbarEndMargin: Int
        get() {
            return getOnUiThread {
                taskbarUnitTestRule.activityContext.deviceProfile.widthPx -
                    taskbarViewController.transientTaskbarIconLayoutBoundsInParent.right
            }
        }

    private val overflowItems: List<Int>
        get() {
            return getOnUiThread {
                val overflowIcon =
                    taskbarViewController.iconViews.firstOrNull { it is TaskbarOverflowView }

                if (overflowIcon is TaskbarOverflowView) {
                    overflowIcon.itemIds
                } else {
                    emptyList()
                }
            }
        }

    private fun tapOverflowIcon() {
        runOnMainSync {
            val overflowIcon =
                taskbarViewController.iconViews.firstOrNull { it is TaskbarOverflowView }
            assertThat(overflowIcon?.callOnClick()).isTrue()
        }
    }

    /**
     * Adds enough running apps for taskbar to enter overflow of `targetOverflowSize`, and verifies
     * * max number of icons in the taskbar remains unchanged
     * * number of icons in the taskbar is at most max number of icons
     * * whether the taskbar overflow icon is shown, and its position in taskbar.
     *
     * Returns max number of icons.
     */
    private fun addRunningAppsAndVerifyOverflowState(targetOverflowSize: Int): Int {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(maxNumIconViews).isGreaterThan(0)
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)
        assertThat(initialIconCount).isLessThan(maxNumIconViews)

        createDesktopTask(maxNumIconViews - initialIconCount + targetOverflowSize)

        assertThat(maxNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex)
            .isEqualTo(if (targetOverflowSize > 0) initialIconCount else -1)
        if (targetOverflowSize > 0) {
            assertThat(overflowItems).containsExactlyElementsIn(0..targetOverflowSize)
        }
        return maxNumIconViews
    }
}

/** TaskbarOverflowComponent used to bind the RecentsModel. */
@LauncherAppSingleton
@Component(
    modules = [AllModulesForTest::class, FakePrefsModule::class, DisplayControllerModule::class]
)
interface TaskbarOverflowComponent : TaskbarSandboxComponent {

    @Component.Builder
    interface Builder : TaskbarSandboxComponent.Builder {
        @BindsInstance fun bindRecentsModel(model: RecentsModel): Builder

        override fun build(): TaskbarOverflowComponent
    }
}
