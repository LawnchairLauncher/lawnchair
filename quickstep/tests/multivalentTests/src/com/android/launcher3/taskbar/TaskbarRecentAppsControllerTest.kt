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

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Rect
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.annotation.UiThreadTest
import com.android.internal.R
import com.android.launcher3.BubbleTextView.RunningAppState
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarRecentAppsController.TaskState
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.quickstep.RecentsModel
import com.android.quickstep.RecentsModel.RecentTasksChangedListener
import com.android.quickstep.TaskIconCache
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SplitTask
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@UiThreadTest
@RunWith(LauncherMultivalentJUnit::class)
@EnableFlags(Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR)
class TaskbarRecentAppsControllerTest : TaskbarBaseTestCase() {

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule
    val disableControllerForCertainTestsWatcher =
        object : TestWatcher() {
            override fun starting(description: Description) {
                // Update canShowRunningAndRecentAppsAtInit before setUp() is called for each test.
                canShowRunningAndRecentAppsAtInit =
                    description.methodName !in
                        listOf("canShowRunningAndRecentAppsAtInitIsFalse_getTasksNeverCalled")
            }
        }

    @Mock private lateinit var mockIconCache: TaskIconCache
    @Mock private lateinit var mockRecentsModel: RecentsModel
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockResources: Resources

    private var taskListChangeId: Int = 1

    private lateinit var recentAppsController: TaskbarRecentAppsController
    private lateinit var myUserHandle: UserHandle
    private val USER_HANDLE_1 = UserHandle.of(1)
    private val USER_HANDLE_2 = UserHandle.of(2)

    private var canShowRunningAndRecentAppsAtInit = true
    private var recentTasksChangedListener: RecentTasksChangedListener? = null

    val recentShownTasks: List<Task>
        get() = recentAppsController.shownTasks.flatMap { it.tasks }

    @Before
    fun setUp() {
        super.setup()
        myUserHandle = Process.myUserHandle()

        // Set desktop mode supported
        whenever(mockContext.getResources()).thenReturn(mockResources)
        whenever(mockResources.getBoolean(R.bool.config_isDesktopModeSupported)).thenReturn(true)

        whenever(mockRecentsModel.iconCache).thenReturn(mockIconCache)
        whenever(mockRecentsModel.unregisterRecentTasksChangedListener(any())).then {
            recentTasksChangedListener = null
            it
        }
        whenever(taskbarDesktopModeController.isLauncherAnimationRunning).thenReturn(false)
        recentAppsController = TaskbarRecentAppsController(mockContext, mockRecentsModel)
        recentAppsController.canShowRunningApps = canShowRunningAndRecentAppsAtInit
        recentAppsController.canShowRecentApps = canShowRunningAndRecentAppsAtInit

        // To ensure the initial getTasks() call is not seen as "loading" for the rest of the test,
        // execute its callback.
        doAnswer {
                val callback: Consumer<ArrayList<GroupTask>> = it.getArgument(1)
                callback.accept(arrayListOf())
                taskListChangeId
            }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentAppsController.init(taskbarControllers, emptyList())
        taskbarControllers.onPostInit()

        recentTasksChangedListener =
            if (canShowRunningAndRecentAppsAtInit) {
                val listenerCaptor = ArgumentCaptor.forClass(RecentTasksChangedListener::class.java)
                verify(mockRecentsModel)
                    .registerRecentTasksChangedListener(listenerCaptor.capture())
                listenerCaptor.value
            } else {
                verify(mockRecentsModel, never()).registerRecentTasksChangedListener(any())
                null
            }

        // Make sure updateHotseatItemInfos() is called after commitRunningAppsToUI()
        whenever(taskbarViewController.commitRunningAppsToUI()).then {
            recentAppsController.updateHotseatItemInfos(
                recentAppsController.shownHotseatItems.toTypedArray()
            )
        }
    }

    // See the TestWatcher rule at the top which sets canShowRunningAndRecentAppsAtInit = false.
    @Test
    fun canShowRunningAndRecentAppsAtInitIsFalse_getTasksNeverCalled() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
            runningTasks = listOf(createTask(1, RUNNING_APP_PACKAGE_1)),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        verify(mockRecentsModel, never()).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun canShowRunningAndRecentAppsIsFalseAfterInit_getTasksOnlyCalledInInit() {
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentAppsController.canShowRunningApps = false
        recentAppsController.canShowRecentApps = false
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
            runningTasks = listOf(createTask(1, RUNNING_APP_PACKAGE_1)),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        // Verify that getTasks() was not called again after the init().
        verify(mockRecentsModel, times(1)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    @EnableFlags(com.android.window.flags2.Flags.FLAG_ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX)
    fun recentTasksChanged_duringGetTasksLoading_dontCallGetTasks() {
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), any<Consumer<List<GroupTask>>>())
        // Override the mock answer for getTasks() so it doesn't call the callback immediately.
        doAnswer { taskListChangeId }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentTasksChangedListener?.onRecentTasksChanged()
        // By not invoking the callback passed to getTasks() we here emulate getTasks() loading.

        recentTasksChangedListener?.onRecentTasksChanged()

        // getTasks() is only called two times overall (init + once more).
        verify(mockRecentsModel, times(2)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    @EnableFlags(com.android.window.flags2.Flags.FLAG_ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX)
    fun recentTasksChanged_duringGetTasksLoading_getTasksCalledWhenLoadingDone() {
        val callbackCaptor = argumentCaptor<Consumer<List<GroupTask>>>()
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), callbackCaptor.capture())
        // Override the mock answer for getTasks() so it doesn't call the callback immediately.
        doAnswer { taskListChangeId }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentTasksChangedListener?.onRecentTasksChanged()
        // By not invoking the callback passed to getTasks() we here emulate getTasks() loading.

        recentTasksChangedListener?.onRecentTasksChanged()
        callbackCaptor.lastValue.accept(emptyList())

        // getTasks() is called again now that the first getTasks() call finished.
        verify(mockRecentsModel, times(3)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    @DisableFlags(com.android.window.flags2.Flags.FLAG_ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX)
    fun recentTasksChanged_duringGetTasksLoading_flagDisabled_callGetTasks() {
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), any<Consumer<List<GroupTask>>>())
        // Override the mock answer for getTasks() so it doesn't call the callback immediately.
        doAnswer { taskListChangeId }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentTasksChangedListener?.onRecentTasksChanged()
        // By not invoking the callback passed to getTasks() we here emulate getTasks() loading.

        recentTasksChangedListener?.onRecentTasksChanged()

        // getTasks() is called once per onRecentTasksChanged() invocation (and once at init)
        verify(mockRecentsModel, times(3)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    @DisableFlags(com.android.window.flags2.Flags.FLAG_ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX)
    fun recentTasksChanged_duringGetTasksLoading_flagDisabled_getTasksNotCalledWhenLoadingDone() {
        val callbackCaptor = argumentCaptor<Consumer<List<GroupTask>>>()
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), callbackCaptor.capture())
        // Override the mock answer for getTasks() so it doesn't call the callback immediately.
        doAnswer { taskListChangeId }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentTasksChangedListener?.onRecentTasksChanged()
        recentTasksChangedListener?.onRecentTasksChanged()
        verify(mockRecentsModel, times(3)).getTasks(any(), any<Consumer<List<GroupTask>>>())

        callbackCaptor.lastValue.accept(emptyList())

        // getTasks() is called once per onRecentTasksChanged() invocation (and once at init)
        verify(mockRecentsModel, times(3)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun getDesktopItemState_nullItemInfo_returnsNotRunning() {
        setInDesktopMode(true)
        val taskState = recentAppsController.getDesktopItemState(/* itemInfo= */ null)
        assertThat(taskState).isEqualTo(TaskState(RunningAppState.NOT_RUNNING))
    }

    @Test
    fun getDesktopItemState_noItemPackage_returnsNotRunning() {
        setInDesktopMode(true)
        val taskState = recentAppsController.getDesktopItemState(ItemInfo())
        assertThat(taskState).isEqualTo(TaskState(RunningAppState.NOT_RUNNING))
    }

    @Test
    fun getDesktopItemState_noMatchingTasks_returnsNotRunning() {
        setInDesktopMode(true)
        val taskState = recentAppsController.getDesktopItemState(createItemInfo("package"))
        assertThat(taskState).isEqualTo(TaskState(RunningAppState.NOT_RUNNING))
    }

    @Test
    fun getDesktopItemState_matchingVisibleTask_returnsVisible() {
        setInDesktopMode(true)
        val visibleTask =
            PerDisplayRunningApps(
                listOf(createTask(id = 1, "visiblePackage", isVisible = true)),
                DEFAULT_DISPLAY,
            )
        updateRecentTasks(runningTasks = listOf(visibleTask), recentTaskPackages = emptyList())

        val taskState = recentAppsController.getDesktopItemState(createItemInfo("visiblePackage"))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.RUNNING, taskId = 1))
    }

    @Test
    fun getDesktopItemState_matchingVisibleTaskOnSecondaryDisplay_returnsVisible() {
        setInDesktopMode(true)
        val visibleTask1 =
            PerDisplayRunningApps(
                listOf(createTask(id = 1, "visiblePackage1", isVisible = false)),
                DEFAULT_DISPLAY,
            )
        val visibleTask2 =
            PerDisplayRunningApps(
                listOf(createTask(id = 2, "visiblePackage2", isVisible = true)),
                DEFAULT_DISPLAY + 1,
            )
        updateRecentTasks(
            runningTasks = listOf(visibleTask1, visibleTask2),
            recentTaskPackages = emptyList(),
        )

        val taskState = recentAppsController.getDesktopItemState(createItemInfo("visiblePackage2"))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.RUNNING, taskId = 2))
    }

    @Test
    fun getDesktopItemState_matchingMinimizedTask_returnsMinimized() {
        setInDesktopMode(true)
        val minimizedTask =
            PerDisplayRunningApps(
                listOf(createTask(id = 1, "minimizedPackage", isVisible = false)),
                DEFAULT_DISPLAY,
            )
        updateRecentTasks(runningTasks = listOf(minimizedTask), recentTaskPackages = emptyList())

        val taskState = recentAppsController.getDesktopItemState(createItemInfo("minimizedPackage"))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.MINIMIZED, taskId = 1))
    }

    @Test
    fun getDesktopItemState_matchingMinimizedTaskOnSecondaryDisplay_returnsVisible() {
        setInDesktopMode(true)
        val visibleTask1 =
            PerDisplayRunningApps(
                listOf(createTask(id = 1, "visiblePackage1", isVisible = false)),
                DEFAULT_DISPLAY,
            )
        val visibleTask2 =
            PerDisplayRunningApps(
                listOf(createTask(id = 2, "visiblePackage2", isVisible = false)),
                DEFAULT_DISPLAY + 1,
            )
        updateRecentTasks(
            runningTasks = listOf(visibleTask1, visibleTask2),
            recentTaskPackages = emptyList(),
        )

        val taskState = recentAppsController.getDesktopItemState(createItemInfo("visiblePackage2"))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.MINIMIZED, taskId = 2))
    }

    @Test
    fun getDesktopItemState_matchingMinimizedAndRunningTask_returnsVisible() {
        setInDesktopMode(true)
        updateRecentTasks(
            runningTasks =
                listOf(
                    PerDisplayRunningApps(
                        listOf(
                            createTask(id = 1, "package", isVisible = false),
                            createTask(id = 2, "package", isVisible = true),
                        ),
                        DEFAULT_DISPLAY,
                    )
                ),
            recentTaskPackages = emptyList(),
        )

        val taskState = recentAppsController.getDesktopItemState(createItemInfo("package"))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.RUNNING, taskId = 2))
    }

    @Test
    fun getDesktopItemState_noMatchingUserId_returnsNotRunning() {
        setInDesktopMode(true)
        updateRecentTasks(
            runningTasks =
                listOf(
                    PerDisplayRunningApps(
                        listOf(
                            createTask(id = 1, "package", isVisible = false, USER_HANDLE_1),
                            createTask(id = 2, "package", isVisible = true, USER_HANDLE_1),
                        ),
                        DEFAULT_DISPLAY,
                    )
                ),
            recentTaskPackages = emptyList(),
        )

        val taskState =
            recentAppsController.getDesktopItemState(createItemInfo("package", USER_HANDLE_2))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.NOT_RUNNING))
    }

    @Test
    fun getRunningAppState_taskNotRunningOrMinimized_returnsNotRunning() {
        setInDesktopMode(true)
        updateRecentTasks(runningTasks = emptyList(), recentTaskPackages = emptyList())

        assertThat(recentAppsController.getRunningAppState(taskId = 1))
            .isEqualTo(RunningAppState.NOT_RUNNING)
    }

    @Test
    fun getRunningAppState_taskNotVisible_returnsMinimized() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, packageName = RUNNING_APP_PACKAGE_1, isVisible = false)
        val task2 = createTask(id = 2, packageName = RUNNING_APP_PACKAGE_1, isVisible = true)
        updateRecentTasks(
            runningTasks = listOf(PerDisplayRunningApps(listOf(task1, task2), DEFAULT_DISPLAY)),
            recentTaskPackages = emptyList(),
        )

        assertThat(recentAppsController.getRunningAppState(taskId = 1))
            .isEqualTo(RunningAppState.MINIMIZED)
    }

    @Test
    fun getRunningAppState_taskNotVisible_returnsMinimizedForSecondaryDisplay() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, packageName = RUNNING_APP_PACKAGE_1, isVisible = false)
        val task2 = createTask(id = 2, packageName = RUNNING_APP_PACKAGE_1, isVisible = true)
        val task3 = createTask(id = 3, packageName = RUNNING_APP_PACKAGE_3, isVisible = false)
        updateRecentTasks(
            runningTasks =
                listOf(
                    PerDisplayRunningApps(listOf(task1, task2), DEFAULT_DISPLAY),
                    PerDisplayRunningApps(listOf(task3), DEFAULT_DISPLAY + 1),
                ),
            recentTaskPackages = emptyList(),
        )

        assertThat(recentAppsController.getRunningAppState(taskId = 3))
            .isEqualTo(RunningAppState.MINIMIZED)
    }

    @Test
    fun getRunningAppState_taskVisible_returnsRunningForSecondaryDisplay() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, packageName = RUNNING_APP_PACKAGE_1, isVisible = false)
        val task2 = createTask(id = 2, packageName = RUNNING_APP_PACKAGE_1, isVisible = true)
        val task3 = createTask(id = 3, packageName = RUNNING_APP_PACKAGE_3, isVisible = true)
        updateRecentTasks(
            runningTasks =
                listOf(
                    PerDisplayRunningApps(listOf(task1, task2), DEFAULT_DISPLAY),
                    PerDisplayRunningApps(listOf(task3), DEFAULT_DISPLAY + 1),
                ),
            recentTaskPackages = emptyList(),
        )

        assertThat(recentAppsController.getRunningAppState(taskId = 3))
            .isEqualTo(RunningAppState.RUNNING)
    }

    @Test
    fun updateHotseatItemInfos_cantShowRunning_inDesktopMode_returnsAllHotseatItems() {
        recentAppsController.canShowRunningApps = false
        setInDesktopMode(true)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = hotseatPackages,
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(hotseatPackages)
    }

    @Test
    fun updateHotseatItemInfos_cantShowRecent_notInDesktopMode_returnsAllHotseatItems() {
        recentAppsController.canShowRecentApps = false
        setInDesktopMode(false)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = hotseatPackages,
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(hotseatPackages)
    }

    @Test
    fun updateHotseatItemInfos_canShowRunning_inDesktopMode_returnsNonPredictedHotseatItems() {
        recentAppsController.canShowRunningApps = true
        setInDesktopMode(true)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        val expectedPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun updateHotseatItemInfos_inDesktopMode_hotseatPackageHasRunningTask_hotseatItemLinksToTask() {
        setInDesktopMode(true)

        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
                runningTasks = listOf(createTask(id = 1, HOTSEAT_PACKAGE_1)),
                recentTaskPackages = emptyList(),
            )

        assertThat(newHotseatItems).hasLength(2)
        assertThat(newHotseatItems[0]).isInstanceOf(TaskItemInfo::class.java)
        assertThat(newHotseatItems[1]).isNotInstanceOf(TaskItemInfo::class.java)
        val hotseatItem1 = newHotseatItems[0] as TaskItemInfo
        assertThat(hotseatItem1.taskId).isEqualTo(1)
    }

    /**
     * Tests that in desktop mode, when two tasks have the same package name and one is in the
     * hotseat, only the hotseat item represents the app, and no duplicate is shown in recent apps.
     */
    @Test
    fun updateHotseatItemInfos_inDesktopMode_twoRunningTasksSamePackage_onlyHotseatCoversTask() {
        setInDesktopMode(true)

        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
                runningTasks =
                    listOf(
                        createTask(id = 1, HOTSEAT_PACKAGE_1),
                        createTask(id = 2, HOTSEAT_PACKAGE_1),
                    ),
                recentTaskPackages = emptyList(),
            )

        // The task is in Hotseat Items
        assertThat(newHotseatItems).hasLength(2)
        assertThat(newHotseatItems[0]).isInstanceOf(TaskItemInfo::class.java)
        assertThat(newHotseatItems[1]).isNotInstanceOf(TaskItemInfo::class.java)
        val hotseatItem1 = newHotseatItems[0] as TaskItemInfo
        assertThat(hotseatItem1.targetPackage).isEqualTo(HOTSEAT_PACKAGE_1)

        // The other task of the same package is not in recentShownTasks
        assertThat(recentShownTasks).isEmpty()
    }

    @Test
    fun updateHotseatItemInfos_canShowRecent_notInDesktopMode_returnsNonPredictedHotseatItems() {
        recentAppsController.canShowRecentApps = true
        setInDesktopMode(false)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        val expectedPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_cantShowRunning_inDesktopMode_shownTasks_returnsEmptyList() {
        recentAppsController.canShowRunningApps = false
        setInDesktopMode(true)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
            runningTasks =
                listOf(
                    createTask(id = 1, RUNNING_APP_PACKAGE_1),
                    createTask(id = 2, RUNNING_APP_PACKAGE_2),
                ),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_cantShowRecent_notInDesktopMode_shownTasks_returnsEmptyList() {
        recentAppsController.canShowRecentApps = false
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_noRecentTasks_shownTasks_returnsEmptyList() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks =
                listOf(
                    createTask(id = 1, RUNNING_APP_PACKAGE_1),
                    createTask(id = 2, RUNNING_APP_PACKAGE_2),
                ),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_noRunningApps_shownTasks_returnsEmptyList() {
        setInDesktopMode(true)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_shownTasks_returnsRunningTasks() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentShownTasks).containsExactlyElementsIn(listOf(task1, task2))
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_getRunningApps_returnsEmptySet() {
        setInDesktopMode(false)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).isEmpty()
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_getRunningApps_returnsAllDesktopTasks() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_getRunningApps_includesHotseat() {
        setInDesktopMode(true)
        val runningTasks =
            listOf(
                createTask(id = 1, HOTSEAT_PACKAGE_1),
                createTask(id = 2, RUNNING_APP_PACKAGE_1),
                createTask(id = 3, RUNNING_APP_PACKAGE_2),
            )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
            runningTasks = runningTasks,
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2, 3))
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_allAppsRunningAndInvisibleAppsMinimized() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task3Minimized = createTask(id = 3, RUNNING_APP_PACKAGE_3, isVisible = false)
        val runningTasks = listOf(task1, task2, task3Minimized)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactly(1, 2, 3)
        assertThat(recentAppsController.minimizedTaskIds).containsExactly(3)
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_samePackage_differentTasks_severalRunningTasks() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1),
            recentTaskPackages = emptyList(),
        )

        assertThat(recentShownTasks).isEqualTo(listOf(task1, task2))
    }

    /**
     * Tests that when multiple instances of the same app are running in desktop mode and the app is
     * not in the hotseat, only one instance is shown in the recent apps section.
     */
    @Test
    fun onRecentTasksChanged_inDesktopMode_multiInstance_noHotseat_shownTasksHasOneInstance() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_1)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )

        // Assert that recentShownTasks contains only one instance of the app
        assertThat(recentShownTasks).hasSize(1)
        assertThat(recentShownTasks[0].key.packageName).isEqualTo(RUNNING_APP_PACKAGE_1)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_shownTasks_maintainsRecency() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3, RECENT_PACKAGE_1),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Most recent packages, minus the currently running one (RECENT_PACKAGE_1).
        assertThat(shownPackages).isEqualTo(listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_addTask_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task3 = createTask(id = 3, RUNNING_APP_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1, task3),
            recentTaskPackages = emptyList(),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        val expectedOrder =
            listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2, RUNNING_APP_PACKAGE_3)
        assertThat(shownPackages).isEqualTo(expectedOrder)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_addTask_shownTasks_maintainsRecency() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_3, RECENT_PACKAGE_2),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3, RECENT_PACKAGE_1),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Most recent packages, minus the currently running one (RECENT_PACKAGE_1).
        assertThat(shownPackages).isEqualTo(listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_removeTask_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task3 = createTask(id = 3, RUNNING_APP_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2, task3),
            recentTaskPackages = emptyList(),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1),
            recentTaskPackages = emptyList(),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        assertThat(shownPackages).isEqualTo(listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2))
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_removeTask_shownTasks_maintainsRecency() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Most recent packages, minus the currently running one (RECENT_PACKAGE_3).
        assertThat(shownPackages).isEqualTo(listOf(RECENT_PACKAGE_2))
    }

    @Test
    fun onRecentTasksChanged_enterDesktopMode_shownTasks_onlyIncludesRunningTasks() {
        setInDesktopMode(false)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = recentTaskPackages,
        )

        setInDesktopMode(true)
        recentTasksChangedListener!!.onRecentTasksChanged()
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        assertThat(shownPackages).containsExactly(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2)
    }

    @Test
    fun onRecentTasksChanged_exitDesktopMode_shownTasks_onlyIncludesRecentTasks() {
        setInDesktopMode(true)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = recentTaskPackages,
        )
        setInDesktopMode(false)
        recentTasksChangedListener!!.onRecentTasksChanged()
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Don't expect RECENT_PACKAGE_3 because it is currently running.
        val expectedPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasRecentTasks_shownTasks_returnsRecentTasks() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // RECENT_PACKAGE_3 is the top task (visible to user) so should be excluded.
        val expectedPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasRecentAndRunningTasks_shownTasks_returnsRecentTaskAndDesktopTile() {
        setInDesktopMode(false)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        val shownPackages = recentAppsController.shownTasks.map { it.packageNames }
        // Only 2 recent tasks shown: Desktop Tile + 1 Recent Task
        val desktopTilePackages = listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1)
        val expectedPackages = listOf(desktopTilePackages, recentTaskPackages)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasRecentAndSplitTasks_shownTasks_returnsRecentTaskAndPair() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_SPLIT_PACKAGES_1, RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        val shownPackages = recentAppsController.shownTasks.map { it.packageNames }
        // Only 2 recent tasks shown: Pair + 1 Recent Task
        val pairPackages = RECENT_SPLIT_PACKAGES_1.split("_")
        val recentTaskPackages = listOf(RECENT_PACKAGE_1)
        val expectedPackages = listOf(pairPackages, recentTaskPackages)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_noActualChangeToRecents_commitRunningAppsToUI_notCalled() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
        // Call onRecentTasksChanged() again with the same tasks, verify it's a no-op.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_noActualChangeToRunning_commitRunningAppsToUI_notCalled() {
        setInDesktopMode(true)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
        // Call onRecentTasksChanged() again with the same tasks, verify it's a no-op.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_onlyMinimizedChanges_commitRunningAppsToUI_isCalled() {
        setInDesktopMode(true)
        val task1Minimized = createTask(id = 1, RUNNING_APP_PACKAGE_1, isVisible = false)
        val task2Visible = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task2Minimized = createTask(id = 2, RUNNING_APP_PACKAGE_2, isVisible = false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1Minimized, task2Visible),
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()

        // Call onRecentTasksChanged() again with a new minimized app, verify we update UI.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1Minimized, task2Minimized),
            recentTaskPackages = emptyList(),
        )

        verify(taskbarViewController, times(2)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_hotseatAppStartsRunning_commitRunningAppsToUI_isCalled() {
        setInDesktopMode(true)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        val originalTasks = listOf(createTask(id = 1, RUNNING_APP_PACKAGE_1))
        val newTasks =
            listOf(createTask(id = 1, RUNNING_APP_PACKAGE_1), createTask(id = 2, HOTSEAT_PACKAGE_1))
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = hotseatPackages,
            runningTasks = originalTasks,
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()

        // Call onRecentTasksChanged() again with a new running app, verify we update UI.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = hotseatPackages,
            runningTasks = newTasks,
            recentTaskPackages = emptyList(),
        )

        verify(taskbarViewController, times(2)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_sameHotseatPackage_differentUser_isInShownTasks() {
        setInDesktopMode(true)
        val hotseatPackageUser = PackageUser(HOTSEAT_PACKAGE_1, USER_HANDLE_2)
        val hotseatPackageUsers = listOf(hotseatPackageUser)
        val runningTask = createTask(id = 1, HOTSEAT_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val runningTasks = listOf(PerDisplayRunningApps(listOf(runningTask), DEFAULT_DISPLAY))
        prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers = hotseatPackageUsers,
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentShownTasks).contains(runningTask)
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_multipleDesktops() {
        setInDesktopMode(true)
        val hotseatPackageUsers = listOf(PackageUser(HOTSEAT_PACKAGE_1, USER_HANDLE_1))
        val defaultDisplayRunningTask =
            createTask(id = 1, HOTSEAT_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val secondaryDisplayRunningTask =
            createTask(id = 2, RUNNING_APP_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val runningTasks =
            listOf(
                PerDisplayRunningApps(listOf(defaultDisplayRunningTask), DEFAULT_DISPLAY),
                PerDisplayRunningApps(listOf(secondaryDisplayRunningTask), DEFAULT_DISPLAY + 1),
            )
        prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers = hotseatPackageUsers,
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentShownTasks).containsExactly(secondaryDisplayRunningTask)
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_multipleDesktops_appsNotInHotseat() {
        setInDesktopMode(true)
        val hotseatPackageUsers = listOf(PackageUser(HOTSEAT_PACKAGE_1, USER_HANDLE_1))
        val defaultDisplayRunningTask =
            createTask(id = 1, RUNNING_APP_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val secondaryDisplayRunningTask =
            createTask(id = 2, RUNNING_APP_PACKAGE_2, localUserHandle = USER_HANDLE_1)
        val runningTasks =
            listOf(
                PerDisplayRunningApps(listOf(defaultDisplayRunningTask), DEFAULT_DISPLAY),
                PerDisplayRunningApps(listOf(secondaryDisplayRunningTask), DEFAULT_DISPLAY + 1),
            )
        prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers = hotseatPackageUsers,
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentShownTasks)
            .containsExactly(defaultDisplayRunningTask, secondaryDisplayRunningTask)
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_multipleDesktops_multipleAppInstances() {
        setInDesktopMode(true)
        val hotseatPackageUsers = listOf(PackageUser(HOTSEAT_PACKAGE_1, USER_HANDLE_1))
        val defaultDisplayRunningTask1 =
            createTask(id = 1, HOTSEAT_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val defaultDisplayRunningTask2 =
            createTask(id = 2, RUNNING_APP_PACKAGE_1, localUserHandle = USER_HANDLE_1)

        val secondaryDisplayRunningTask1 =
            createTask(id = 3, RUNNING_APP_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val secondaryDisplayRunningTask2 =
            createTask(id = 4, HOTSEAT_PACKAGE_1, localUserHandle = USER_HANDLE_1)

        val runningTasks =
            listOf(
                PerDisplayRunningApps(
                    listOf(defaultDisplayRunningTask1, defaultDisplayRunningTask2),
                    DEFAULT_DISPLAY,
                ),
                PerDisplayRunningApps(
                    listOf(secondaryDisplayRunningTask1, secondaryDisplayRunningTask2),
                    DEFAULT_DISPLAY + 1,
                ),
            )
        prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers = hotseatPackageUsers,
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )

        assertThat(recentShownTasks).hasSize(1)
        assertThat(recentShownTasks)
            .containsAnyOf(defaultDisplayRunningTask2, secondaryDisplayRunningTask1)

        assertThat(recentAppsController.runningTaskIds)
            .containsExactlyElementsIn(listOf(1, 2, 3, 4))
    }

    @Test
    fun hasSingleTask_noTargetPackage_returnsFalse() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1),
        )
        assertThat(recentAppsController.hasSingleTask(ItemInfo())).isFalse()
    }

    @Test
    fun hasSingleTask_noRecentTasks_returnsFalse() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = emptyList(),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_1)
        assertThat(recentAppsController.hasSingleTask(itemInfo)).isFalse()
    }

    @Test
    fun hasSingleTask_noMatchingSingleTask_returnsFalse() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_2)
        assertThat(recentAppsController.hasSingleTask(itemInfo)).isFalse()
    }

    @Test
    fun hasSingleTask_matchingSingleTask_returnsTrue() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_1)
        assertThat(recentAppsController.hasSingleTask(itemInfo)).isTrue()
    }

    @Test
    fun hasSingleTask_matchingSingleTaskDifferentUser_returnsFalse() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1),
        )
        // RECENT_PACKAGE_1 is created with myUserHandle in createRecentTasksFromPackageNames
        val itemInfo = createItemInfo(RECENT_PACKAGE_1, USER_HANDLE_1)
        assertThat(recentAppsController.hasSingleTask(itemInfo)).isFalse()
    }

    private fun prepareHotseatAndRunningAndRecentApps(
        hotseatPackages: List<String>,
        runningTasks: List<Task>,
        recentTaskPackages: List<String>,
    ): Array<ItemInfo?> {
        val hotseatPackageUsers = hotseatPackages.map { PackageUser(it, myUserHandle) }
        return prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers,
            listOf(PerDisplayRunningApps(runningTasks, DEFAULT_DISPLAY)),
            recentTaskPackages,
        )
    }

    private fun prepareHotseatAndRunningAndRecentAppsInternal(
        hotseatPackageUsers: List<PackageUser>,
        runningTasks: List<PerDisplayRunningApps>,
        recentTaskPackages: List<String>,
    ): Array<ItemInfo?> {
        val hotseatItems = createHotseatItemsFromPackageUsers(hotseatPackageUsers)
        recentAppsController.updateHotseatItemInfos(hotseatItems.toTypedArray())
        updateRecentTasks(runningTasks, recentTaskPackages)
        return recentAppsController.shownHotseatItems.toTypedArray()
    }

    private fun updateRecentTasks(
        runningTasks: List<PerDisplayRunningApps>,
        recentTaskPackages: List<String>,
    ) {
        val recentTasks = createRecentTasksFromPackageNames(recentTaskPackages)
        val allTasks =
            ArrayList<GroupTask>().apply {
                runningTasks.forEach {
                    add(DesktopTask(deskId = 0, it.displayId, ArrayList(it.tasks)))
                }
                addAll(recentTasks)
            }
        doAnswer {
                val callback: Consumer<ArrayList<GroupTask>> = it.getArgument(1)
                callback.accept(allTasks)
                taskListChangeId
            }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentTasksChangedListener?.onRecentTasksChanged()
    }

    private fun createHotseatItemsFromPackageUsers(
        packageUsers: List<PackageUser>
    ): List<ItemInfo> {
        return packageUsers
            .map {
                createTestAppInfo(packageName = it.packageName, userHandle = it.userHandle).apply {
                    container =
                        if (it.packageName.startsWith("predicted")) {
                            CONTAINER_HOTSEAT_PREDICTION
                        } else {
                            CONTAINER_HOTSEAT
                        }
                }
            }
            .map { it.makeWorkspaceItem(taskbarActivityContext) }
    }

    private fun createTestAppInfo(
        packageName: String = "testPackageName",
        className: String = "testClassName",
        userHandle: UserHandle,
    ) = AppInfo(ComponentName(packageName, className), className /* title */, userHandle, Intent())

    private fun createRecentTasksFromPackageNames(packageNames: List<String>): List<GroupTask> {
        return packageNames.map { packageName ->
            if (packageName.startsWith("split")) {
                val splitPackages = packageName.split("_")
                SplitTask(
                    createTask(100, splitPackages[0]),
                    createTask(101, splitPackages[1]),
                    SplitBounds(
                        /* leftTopBounds = */ Rect(),
                        /* rightBottomBounds = */ Rect(),
                        /* leftTopTaskId = */ 1,
                        /* rightBottomTaskId = */ 2,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50,
                    ),
                )
            } else {
                // Use the number at the end of the test packageName as the id.
                val id = 1000 + packageName[packageName.length - 1].code
                SingleTask(createTask(id, packageName))
            }
        }
    }

    private fun createTask(
        id: Int,
        packageName: String,
        isVisible: Boolean = true,
        localUserHandle: UserHandle? = null,
    ): Task {
        return Task(
                Task.TaskKey(
                    id,
                    WINDOWING_MODE_FREEFORM,
                    Intent().apply { `package` = packageName },
                    ComponentName(packageName, "TestActivity"),
                    localUserHandle?.identifier ?: myUserHandle.identifier,
                    0,
                )
            )
            .apply { this.isVisible = isVisible }
    }

    private fun setInDesktopMode(inDesktopMode: Boolean) {
        whenever(taskbarControllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar())
            .thenReturn(inDesktopMode)
        whenever(taskbarControllers.taskbarDesktopModeController.isInDesktopMode(DEFAULT_DISPLAY))
            .thenReturn(inDesktopMode)
    }

    private fun createItemInfo(
        packageName: String,
        userHandle: UserHandle = myUserHandle,
    ): ItemInfo {
        val appInfo = AppInfo()
        appInfo.intent = Intent().setComponent(ComponentName(packageName, "className"))
        appInfo.user = userHandle
        return WorkspaceItemInfo(appInfo)
    }

    private val GroupTask.packageNames: List<String>
        get() = tasks.map { task -> task.key.packageName }

    private companion object {
        const val HOTSEAT_PACKAGE_1 = "hotseat1"
        const val HOTSEAT_PACKAGE_2 = "hotseat2"
        const val PREDICTED_PACKAGE_1 = "predicted1"
        const val RUNNING_APP_PACKAGE_1 = "running1"
        const val RUNNING_APP_PACKAGE_2 = "running2"
        const val RUNNING_APP_PACKAGE_3 = "running3"
        const val RECENT_PACKAGE_1 = "recent1"
        const val RECENT_PACKAGE_2 = "recent2"
        const val RECENT_PACKAGE_3 = "recent3"
        const val RECENT_SPLIT_PACKAGES_1 = "split1_split2"
    }

    data class PackageUser(val packageName: String, val userHandle: UserHandle)

    data class PerDisplayRunningApps(val tasks: List<Task>, val displayId: Int)
}
