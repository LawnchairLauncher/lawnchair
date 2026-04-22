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
package com.android.wm.shell.desktopmode.multidesks

import android.app.ActivityManager
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.DesktopExperienceFlags
import android.window.DisplayAreaInfo
import android.window.TransitionInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.Change
import android.window.WindowContainerTransaction.HierarchyOp
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT
import androidx.core.util.valueIterator
import androidx.test.filters.SmallTest
import com.android.wm.shell.MockToken
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTaskOrganizer.TaskListener
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.LaunchAdjacentController
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.multidesks.RootTaskDesksOrganizer.DeskMinimizationRoot
import com.android.wm.shell.desktopmode.multidesks.RootTaskDesksOrganizer.DeskRoot
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.suspendCoroutine
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [RootTaskDesksOrganizer].
 *
 * Usage: atest WMShellUnitTests:RootTaskDesksOrganizerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class RootTaskDesksOrganizerTest : ShellTestCase() {

    private val testExecutor = TestShellExecutor()
    private val testShellInit = ShellInit(testExecutor)
    private val mockShellCommandHandler = mock<ShellCommandHandler>()
    private val mockShellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val mockTDAOrganizer = mock<RootTaskDisplayAreaOrganizer>()

    private val launchAdjacentController = LaunchAdjacentController(mock())
    private val taskInfoChangedListener = mock<(ActivityManager.RunningTaskInfo) -> Unit>()

    private lateinit var organizer: RootTaskDesksOrganizer

    @Before
    fun setUp() {
        organizer =
            RootTaskDesksOrganizer(
                testShellInit,
                mockShellCommandHandler,
                mockShellTaskOrganizer,
                launchAdjacentController,
                mockTDAOrganizer,
            )
        organizer.setOnDesktopTaskInfoChangedListener(taskInfoChangedListener)

        val tda = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
        whenever(mockTDAOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)).thenReturn(tda)
    }

    @Test fun testCreateDesk_createsDeskAndMinimizationRoots() = runTest { createDeskSuspending() }

    @Test
    fun testCreateDesk_rootExistsForOtherUser_reusesRoot() = runTest {
        val desk = createDeskSuspending(userId = PRIMARY_USER_ID)

        val desk2 = createDeskSuspending(userId = SECONDARY_USER_ID)

        assertThat(desk2.deskRoot.deskId).isEqualTo(desk.deskRoot.deskId)
    }

    @Test
    fun testCreateDesk_rootExistsForOtherUser_pendingDeletion_doesNotReuseRoot() = runTest {
        val desk = createDeskSuspending(userId = PRIMARY_USER_ID)
        organizer.removeDesk(WindowContainerTransaction(), desk.deskRoot.deskId, PRIMARY_USER_ID)

        val desk2 = createDeskSuspending(userId = SECONDARY_USER_ID)

        assertThat(desk2.deskRoot.deskId).isNotEqualTo(desk.deskRoot.deskId)
    }

    @Test
    fun testCreateDesk_rootExistsForOtherUser_inOtherDisplay_doesNotReuseRoot() = runTest {
        val desk = createDeskSuspending(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        val desk2 = createDeskSuspending(userId = SECONDARY_USER_ID, displayId = SECOND_DISPLAY)

        assertThat(desk2.deskRoot.deskId).isNotEqualTo(desk.deskRoot.deskId)
    }

    @Test
    fun testWarmUpDefaultDesk_deskCreated() = runTest {
        warmUpDefaultDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        assertThat(organizer.deskRootsByDeskId.size()).isEqualTo(1)
        val root = organizer.deskRootsByDeskId.valueAt(0)
        assertThat(root.users.isEmpty()).isTrue()
    }

    @Test
    fun testWarmUpDefaultDesk_deskAlreadyExists_noDeskCreated() = runTest {
        createDeskSuspending(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        warmUpDefaultDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        // Only the one that already existed is present.
        assertThat(organizer.deskRootsByDeskId.size()).isEqualTo(1)
    }

    @Test
    fun testWarmUpDefaultDesk_createRequestInProgress_noDeskCreated() = runTest {
        organizer.createDesk(displayId = DEFAULT_DISPLAY, userId = PRIMARY_USER_ID) {}

        warmUpDefaultDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        assertThat(organizer.deskRootsByDeskId.size()).isEqualTo(0)
    }

    @Test
    fun testWarmUpDefaultDesk_deskAlreadyExists_forOtherDisplayOnly_deskCreated() = runTest {
        createDeskSuspending(userId = PRIMARY_USER_ID, displayId = SECOND_DISPLAY)

        warmUpDefaultDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        assertThat(
                organizer.deskRootsByDeskId.valueIterator().asSequence().singleOrNull { root ->
                    root.taskInfo.displayId == DEFAULT_DISPLAY && root.users.isEmpty()
                }
            )
            .isNotNull()
    }

    @Test
    fun testWarmUpDefaultDesk_deskAlreadyExists_removalInProgress_deskCreated() = runTest {
        val desk = createDeskSuspending(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)
        organizer.removeDesk(WindowContainerTransaction(), desk.deskRoot.deskId, PRIMARY_USER_ID)

        warmUpDefaultDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        // A desk different than the one getting removed should exist.
        assertThat(
                organizer.deskRootsByDeskId.valueIterator().asSequence().singleOrNull { root ->
                    root.taskInfo.displayId == DEFAULT_DISPLAY &&
                        root.users.isEmpty() &&
                        root.deskId != desk.deskRoot.deskId
                }
            )
            .isNotNull()
    }

    @Test
    fun testCreateDeskImmediate() = runTest {
        warmUpDefaultDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        val deskId =
            organizer.createDeskImmediate(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        assertThat(deskId).isNotNull()
    }

    @Test
    fun testCreateDeskImmediate_rootExistsForOtherUser_pendingDeletion_doesNotReuseRoot() =
        runTest {
            val desk = createDeskSuspending(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)
            organizer.removeDesk(
                WindowContainerTransaction(),
                desk.deskRoot.deskId,
                PRIMARY_USER_ID,
            )

            val desk2 =
                organizer.createDeskImmediate(
                    userId = SECONDARY_USER_ID,
                    displayId = DEFAULT_DISPLAY,
                )

            assertThat(desk2).isNull()
        }

    @Test
    fun testCreateDeskImmediate_rootExistsForOtherUser_inOtherDisplay_doesNotReuseRoot() = runTest {
        createDeskSuspending(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        val desk2 =
            organizer.createDeskImmediate(userId = SECONDARY_USER_ID, displayId = SECOND_DISPLAY)

        assertThat(desk2).isNull()
    }

    @Test
    fun testCreateDesk_warmUpInProgress_usesWarmedUpDesk() = runTest {
        val displayId = DEFAULT_DISPLAY
        organizer.warmUpDefaultDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        organizer.createDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY) {}

        // Only one desk attempt.
        verify(mockShellTaskOrganizer, times(1))
            .createRootTask(
                any(),
                eq(organizer)
            )
    }

    @Test
    fun testCreateDesk_warmUpInProgress_appliesCallback() = runTest {
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }
        var callbackApplied = false

        organizer.warmUpDefaultDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)
        organizer.createDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY) { _ ->
            callbackApplied = true
        }
        organizer.onTaskAppeared(freeformRoot, SurfaceControl())

        assertThat(callbackApplied).isTrue()
    }

    @Test
    fun testCreateDesk_twice_warmUpInProgress_usesWarmedUpDeskAndCreatesOne() = runTest {
        val displayId = DEFAULT_DISPLAY
        organizer.warmUpDefaultDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)

        organizer.createDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY) {}
        organizer.createDesk(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY) {}

        // One for the warmup/first desk and one for the second desk.
        verify(mockShellTaskOrganizer, times(2))
            .createRootTask(
                any(),
                eq(organizer)
            )
    }

    @Test
    fun testCreateDesk_notAddedAsChildLeash() = runTest {
        val desk = createDeskSuspending()
        assertThat(organizer.childLeashes.contains(desk.deskRoot.taskInfo.taskId)).isFalse()
        assertThat(organizer.childLeashes.contains(desk.minimizationRoot.taskInfo.taskId)).isFalse()
    }

    @Test
    fun testCreateMinimizationRoot_marksHidden() = runTest {
        val desk = createDeskSuspending()

        verify(mockShellTaskOrganizer)
            .applyTransaction(
                argThat { wct ->
                    wct.changes.any { change ->
                        change.key == desk.minimizationRoot.token.asBinder() &&
                            (change.value.changeMask and Change.CHANGE_HIDDEN != 0) &&
                            change.value.hidden
                    }
                }
            )
    }

    @Test
    fun testOnTaskAppeared_withoutRequest_throws() = runTest {
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }

        assertThrows(Exception::class.java) {
            organizer.onTaskAppeared(freeformRoot, SurfaceControl())
        }
    }

    @Test
    fun testOnTaskAppeared_duplicateRoot_throws() = runTest {
        val desk = createDeskSuspending()

        assertThrows(Exception::class.java) {
            organizer.onTaskAppeared(desk.deskRoot.taskInfo, SurfaceControl())
        }
    }

    @Test
    fun testOnTaskAppeared_duplicateMinimizedRoot_throws() = runTest {
        val desk = createDeskSuspending()

        assertThrows(Exception::class.java) {
            organizer.onTaskAppeared(desk.minimizationRoot.taskInfo, SurfaceControl())
        }
    }

    @Test
    fun testOnTaskVanished_removesRoot() = runTest {
        val desk = createDeskSuspending()

        organizer.onTaskVanished(desk.deskRoot.taskInfo)

        assertThat(organizer.deskRootsByDeskId.contains(desk.deskRoot.deskId)).isFalse()
    }

    @Test
    fun testOnTaskVanished_removesMinimizedRoot() = runTest {
        val desk = createDeskSuspending()

        organizer.onTaskVanished(desk.deskRoot.taskInfo)
        organizer.onTaskVanished(desk.minimizationRoot.taskInfo)

        assertThat(organizer.deskMinimizationRootsByDeskId.contains(desk.deskRoot.deskId)).isFalse()
    }

    @Test
    fun testDesktopWindowAppearsInDesk() = runTest {
        val desk = createDeskSuspending()
        val child = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }

        organizer.onTaskAppeared(child, SurfaceControl())

        assertThat(desk.deskRoot.children).contains(child.taskId)
        assertThat(organizer.childLeashes.contains(child.taskId)).isTrue()
    }

    @Test
    fun testDesktopWindowAppearsInDeskMinimizationRoot() = runTest {
        val desk = createDeskSuspending()
        val child = createFreeformTask().apply { parentTaskId = desk.minimizationRoot.rootId }

        organizer.onTaskAppeared(child, SurfaceControl())

        assertThat(desk.minimizationRoot.children).contains(child.taskId)
        assertThat(organizer.childLeashes.contains(child.taskId)).isTrue()
    }

    @Test
    fun testDesktopWindowMovesToMinimizationRoot() = runTest {
        val desk = createDeskSuspending()
        val child = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        organizer.onTaskAppeared(child, SurfaceControl())

        child.parentTaskId = desk.minimizationRoot.rootId
        organizer.onTaskInfoChanged(child)

        assertThat(desk.deskRoot.children).doesNotContain(child.taskId)
        assertThat(desk.minimizationRoot.children).contains(child.taskId)
        assertThat(organizer.childLeashes.contains(child.taskId)).isTrue()
    }

    @Test
    fun testDesktopWindowDisappearsFromDesk() = runTest {
        val desk = createDeskSuspending()
        val child = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }

        organizer.onTaskAppeared(child, SurfaceControl())
        organizer.onTaskVanished(child)

        assertThat(desk.deskRoot.children).doesNotContain(child.taskId)
        assertThat(organizer.childLeashes.contains(child.taskId)).isFalse()
    }

    @Test
    fun testDesktopWindowDisappearsFromDeskMinimizationRoot() = runTest {
        val desk = createDeskSuspending()
        val child = createFreeformTask().apply { parentTaskId = desk.minimizationRoot.rootId }

        organizer.onTaskAppeared(child, SurfaceControl())
        organizer.onTaskVanished(child)

        assertThat(desk.minimizationRoot.children).doesNotContain(child.taskId)
        assertThat(organizer.childLeashes.contains(child.taskId)).isFalse()
    }

    @Test
    fun testRemoveDesk_disablesAsLaunchRoot() = runTest {
        val desk = createDeskSuspending(userId = PRIMARY_USER_ID)
        val wct = WindowContainerTransaction()
        organizer.activateDesk(wct, desk.deskRoot.deskId)
        assertThat(desk.deskRoot.isLaunchRootRequested).isTrue()

        organizer.removeDesk(wct, desk.deskRoot.deskId, userId = PRIMARY_USER_ID)

        assertThat(desk.deskRoot.isLaunchRootRequested).isFalse()
    }

    @Test
    fun testRemoveDesk_removesDeskRoot() = runTest {
        val desk = createDeskSuspending(userId = PRIMARY_USER_ID)

        val wct = WindowContainerTransaction()
        organizer.removeDesk(wct, desk.deskRoot.deskId, userId = PRIMARY_USER_ID)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_ROOT_TASK &&
                        hop.container == desk.deskRoot.token.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun testRemoveDesk_removesMinimizationRoot() = runTest {
        val desk = createDeskSuspending(userId = PRIMARY_USER_ID)

        val wct = WindowContainerTransaction()
        organizer.removeDesk(wct, desk.deskRoot.deskId, userId = PRIMARY_USER_ID)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_ROOT_TASK &&
                        hop.container == desk.minimizationRoot.token.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun testRemoveDesk_rootUsedByOtherUser_keepsDeskRoot() = runTest {
        val primaryUserDesk = createDeskSuspending(userId = PRIMARY_USER_ID)
        val secondaryUserDesk = createDeskSuspending(userId = SECONDARY_USER_ID)
        assertThat(primaryUserDesk).isEqualTo(secondaryUserDesk)

        val wct = WindowContainerTransaction()
        organizer.removeDesk(wct, primaryUserDesk.deskRoot.deskId, userId = PRIMARY_USER_ID)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_ROOT_TASK &&
                        hop.container == primaryUserDesk.deskRoot.token.asBinder()
                }
            )
            .isFalse()
        assertThat(primaryUserDesk.deskRoot.users).containsExactly(SECONDARY_USER_ID)
    }

    @Test
    fun testActivateDesk() = runTest {
        val desk = createDeskSuspending()

        val wct = WindowContainerTransaction()
        organizer.activateDesk(wct, desk.deskRoot.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REORDER &&
                        hop.toTop &&
                        hop.container == desk.deskRoot.taskInfo.token.asBinder()
                }
            )
            .isTrue()
        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT &&
                        hop.container == desk.deskRoot.taskInfo.token.asBinder()
                }
            )
            .isTrue()
        assertThat(
                wct.changes.any { change ->
                    change.key == desk.deskRoot.token.asBinder() &&
                        (change.value.changeMask and Change.CHANGE_IS_TASK_MOVE_ALLOWED != 0) &&
                        change.value.isTaskMoveAllowed
                }
            )
            .isTrue()
    }

    @Test
    fun testActivateDesk_didNotExist_throws() = runTest {
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }

        val wct = WindowContainerTransaction()
        assertThrows(Exception::class.java) { organizer.activateDesk(wct, freeformRoot.taskId) }
    }

    @Test
    fun testActivateDesk_skipReorder() = runTest {
        val desk = createDeskSuspending()

        val wct = WindowContainerTransaction()
        organizer.activateDesk(wct, desk.deskRoot.deskId, skipReorder = true)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REORDER &&
                        hop.toTop &&
                        hop.container == desk.deskRoot.taskInfo.token.asBinder()
                }
            )
            .isFalse()
    }

    @Test
    fun testMoveTaskToDesk() = runTest {
        val desk = createDeskSuspending()

        val desktopTask = createFreeformTask().apply { parentTaskId = -1 }
        val wct = WindowContainerTransaction()
        organizer.moveTaskToDesk(wct, desk.deskRoot.deskId, desktopTask)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.isReparent &&
                        hop.toTop &&
                        hop.container == desktopTask.token.asBinder() &&
                        hop.newParent == desk.deskRoot.taskInfo.token.asBinder()
                }
            )
            .isTrue()
        assertThat(
                wct.changes.any { change ->
                    change.key == desktopTask.token.asBinder() &&
                        change.value.windowingMode == WINDOWING_MODE_UNDEFINED
                }
            )
            .isTrue()
    }

    @Test
    fun testMoveTaskToDesk_didNotExist_throws() = runTest {
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }

        val desktopTask = createFreeformTask().apply { parentTaskId = -1 }
        val wct = WindowContainerTransaction()
        assertThrows(Exception::class.java) {
            organizer.moveTaskToDesk(wct, freeformRoot.taskId, desktopTask)
        }
    }

    @Test
    fun testMoveTaskToDesk_minimized() = runTest {
        val desk = createDeskSuspending()

        val desktopTask = createFreeformTask().apply { parentTaskId = -1 }
        val wct = WindowContainerTransaction()
        organizer.moveTaskToDesk(wct, desk.deskRoot.deskId, desktopTask, minimized = true)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.isReparent &&
                        hop.toTop &&
                        hop.container == desktopTask.token.asBinder() &&
                        // Reparented to minimization root.
                        hop.newParent == desk.minimizationRoot.taskInfo.token.asBinder()
                }
            )
            .isTrue()
        assertThat(
                wct.changes.any { change ->
                    change.key == desktopTask.token.asBinder() &&
                        change.value.windowingMode == WINDOWING_MODE_UNDEFINED
                }
            )
            .isTrue()
    }

    @Test
    fun testGetDeskAtEnd() = runTest {
        val desk = createDeskSuspending()

        val task = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        val endDesk =
            organizer.getDeskAtEnd(
                TransitionInfo.Change(task.token, SurfaceControl()).apply { taskInfo = task }
            )

        assertThat(endDesk).isEqualTo(desk.deskRoot.deskId)
    }

    @Test
    fun testGetDeskAtEnd_inMinimizationRoot() = runTest {
        val desk = createDeskSuspending()

        val task = createFreeformTask().apply { parentTaskId = desk.minimizationRoot.rootId }
        val endDesk =
            organizer.getDeskAtEnd(
                TransitionInfo.Change(task.token, SurfaceControl()).apply { taskInfo = task }
            )

        assertThat(endDesk).isEqualTo(desk.deskRoot.deskId)
    }

    @Test
    fun testIsMinimizedInDeskAtEnd() = runTest {
        val desk = createDeskSuspending()
        val notInDesk = createFreeformTask().apply { parentTaskId = -1 }
        val inDeskNotMinimized = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        val minimized = createFreeformTask().apply { parentTaskId = desk.minimizationRoot.rootId }

        assertThat(organizer.isMinimizedInDeskAtEnd(notInDesk.toChange())).isFalse()
        assertThat(organizer.isMinimizedInDeskAtEnd(inDeskNotMinimized.toChange())).isFalse()
        assertThat(organizer.isMinimizedInDeskAtEnd(minimized.toChange())).isTrue()
    }

    @Test
    fun testIsDeskActiveAtEnd() = runTest {
        val desk = createDeskSuspending()

        val isActive =
            organizer.isDeskActiveAtEnd(
                change =
                    TransitionInfo.Change(desk.deskRoot.token, SurfaceControl()).apply {
                        taskInfo = desk.deskRoot.taskInfo
                        mode = TRANSIT_TO_FRONT
                    },
                deskId = desk.deskRoot.deskId,
            )

        assertThat(isActive).isTrue()
    }

    @Test
    fun deactivateDesk_clearsLaunchRoot() = runTest {
        val wct = WindowContainerTransaction()
        val desk = createDeskSuspending()
        organizer.activateDesk(wct, desk.deskRoot.deskId)

        organizer.deactivateDesk(wct, desk.deskRoot.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT &&
                        hop.container == desk.deskRoot.taskInfo.token.asBinder() &&
                        hop.windowingModes == null &&
                        hop.activityTypes == null
                }
            )
            .isTrue()
    }

    @Test
    fun deactivateDesk_unsetsTaskMoveAllowed() = runTest {
        val wct = WindowContainerTransaction()
        val desk = createDeskSuspending()
        organizer.activateDesk(wct, desk.deskRoot.deskId)

        organizer.deactivateDesk(wct, desk.deskRoot.deskId)

        assertThat(
                wct.changes.any { change ->
                    change.key == desk.deskRoot.token.asBinder() &&
                        (change.value.changeMask and Change.CHANGE_IS_TASK_MOVE_ALLOWED != 0) &&
                        !change.value.isTaskMoveAllowed
                }
            )
            .isTrue()
    }

    @Test
    fun deactivateDesk_reordersRootToBack() = runTest {
        val wct = WindowContainerTransaction()
        val desk = createDeskSuspending()
        organizer.activateDesk(wct, desk.deskRoot.deskId)

        organizer.deactivateDesk(wct, desk.deskRoot.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HIERARCHY_OP_TYPE_REORDER &&
                        !hop.toTop &&
                        hop.container == desk.deskRoot.taskInfo.token.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun deactivateDesk_skipReorder() = runTest {
        val wct = WindowContainerTransaction()
        val desk = createDeskSuspending()
        organizer.activateDesk(wct, desk.deskRoot.deskId)

        organizer.deactivateDesk(wct, desk.deskRoot.deskId, skipReorder = true)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HIERARCHY_OP_TYPE_REORDER &&
                        !hop.toTop &&
                        hop.container == desk.deskRoot.taskInfo.token.asBinder()
                }
            )
            .isFalse()
    }

    @Test
    fun isDeskChange_forDeskId() = runTest {
        val desk = createDeskSuspending()

        assertThat(
                organizer.isDeskChange(
                    TransitionInfo.Change(desk.deskRoot.taskInfo.token, desk.deskRoot.leash).apply {
                        taskInfo = desk.deskRoot.taskInfo
                    },
                    desk.deskRoot.deskId,
                )
            )
            .isTrue()
    }

    @Test
    fun isDeskChange_forDeskId_inMinimizationRoot() = runTest {
        val desk = createDeskSuspending()

        assertThat(
                organizer.isDeskChange(
                    change =
                        TransitionInfo.Change(
                                desk.minimizationRoot.token,
                                desk.minimizationRoot.leash,
                            )
                            .apply { taskInfo = desk.minimizationRoot.taskInfo },
                    deskId = desk.deskRoot.deskId,
                )
            )
            .isTrue()
    }

    @Test
    fun isDeskChange_anyDesk() = runTest {
        val desk = createDeskSuspending()

        assertThat(
                organizer.isDeskChange(
                    change =
                        TransitionInfo.Change(desk.deskRoot.taskInfo.token, desk.deskRoot.leash)
                            .apply { taskInfo = desk.deskRoot.taskInfo }
                )
            )
            .isTrue()
    }

    @Test
    fun isDeskChange_anyDesk_inMinimizationRoot() = runTest {
        val desk = createDeskSuspending()

        assertThat(
                organizer.isDeskChange(
                    change =
                        TransitionInfo.Change(
                                desk.minimizationRoot.taskInfo.token,
                                desk.minimizationRoot.leash,
                            )
                            .apply { taskInfo = desk.minimizationRoot.taskInfo }
                )
            )
            .isTrue()
    }

    @Test
    fun minimizeTask() = runTest {
        val desk = createDeskSuspending()
        val task = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        val wct = WindowContainerTransaction()
        organizer.moveTaskToDesk(wct, desk.deskRoot.deskId, task)
        organizer.onTaskAppeared(task, SurfaceControl())

        organizer.minimizeTask(wct, deskId = desk.deskRoot.deskId, task)

        assertThat(wct.hasMinimizationHops(desk, task.token)).isTrue()
    }

    @Test
    fun minimizeTask_alreadyMinimized_noOp() = runTest {
        val desk = createDeskSuspending()
        val task = createFreeformTask().apply { parentTaskId = desk.minimizationRoot.rootId }
        val wct = WindowContainerTransaction()
        organizer.onTaskAppeared(task, SurfaceControl())

        organizer.minimizeTask(wct, deskId = desk.deskRoot.deskId, task)

        assertThat(wct.isEmpty).isTrue()
    }

    @Test
    fun minimizeTask_inDifferentDesk_noOp() = runTest {
        val desk = createDeskSuspending()
        val otherDesk = createDeskSuspending()
        val task = createFreeformTask().apply { parentTaskId = otherDesk.deskRoot.deskId }
        val wct = WindowContainerTransaction()
        organizer.onTaskAppeared(task, SurfaceControl())

        organizer.minimizeTask(wct, deskId = desk.deskRoot.deskId, task)

        assertThat(wct.isEmpty).isTrue()
    }

    @Test
    fun unminimizeTask() = runTest {
        val desk = createDeskSuspending()
        val task = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        val wct = WindowContainerTransaction()
        organizer.moveTaskToDesk(wct, desk.deskRoot.deskId, task)
        organizer.onTaskAppeared(task, SurfaceControl())
        organizer.minimizeTask(wct, deskId = desk.deskRoot.deskId, task)
        task.parentTaskId = desk.minimizationRoot.rootId
        organizer.onTaskInfoChanged(task)

        wct.clear()
        organizer.unminimizeTask(wct, deskId = desk.deskRoot.deskId, task)

        assertThat(wct.hasUnminimizationHops(desk, task.token)).isTrue()
    }

    @Test
    fun unminimizeTask_alreadyUnminimized_noOp() = runTest {
        val desk = createDeskSuspending()
        val task = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        val wct = WindowContainerTransaction()
        organizer.moveTaskToDesk(wct, desk.deskRoot.deskId, task)
        organizer.onTaskAppeared(task, SurfaceControl())

        wct.clear()
        organizer.unminimizeTask(wct, deskId = desk.deskRoot.deskId, task)

        assertThat(wct.hasUnminimizationHops(desk, task.token)).isFalse()
    }

    @Test
    fun unminimizeTask_notInDesk_noOp() = runTest {
        val desk = createDeskSuspending()
        val task = createFreeformTask()
        val wct = WindowContainerTransaction()

        organizer.unminimizeTask(wct, deskId = desk.deskRoot.deskId, task)

        assertThat(wct.hasUnminimizationHops(desk, task.token)).isFalse()
    }

    @Test
    fun reorderTaskToFront() = runTest {
        val desk = createDeskSuspending()
        val task = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        val wct = WindowContainerTransaction()
        organizer.onTaskAppeared(task, SurfaceControl())

        organizer.reorderTaskToFront(wct, desk.deskRoot.deskId, task)

        assertThat(
                wct.hierarchyOps.singleOrNull { hop ->
                    hop.container == task.token.asBinder() &&
                        hop.type == HIERARCHY_OP_TYPE_REORDER &&
                        hop.toTop &&
                        hop.includingParents()
                }
            )
            .isNotNull()
    }

    @Test
    fun reorderTaskToFront_notInDesk_noOp() = runTest {
        val desk = createDeskSuspending()
        val task = createFreeformTask()
        val wct = WindowContainerTransaction()

        organizer.reorderTaskToFront(wct, desk.deskRoot.deskId, task)

        assertThat(
                wct.hierarchyOps.singleOrNull { hop ->
                    hop.container == task.token.asBinder() &&
                        hop.type == HIERARCHY_OP_TYPE_REORDER &&
                        hop.toTop &&
                        hop.includingParents()
                }
            )
            .isNull()
    }

    @Test
    fun reorderTaskToFront_minimized_unminimizesAndReorders() = runTest {
        val desk = createDeskSuspending()
        val task = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        val wct = WindowContainerTransaction()
        organizer.onTaskAppeared(task, SurfaceControl())
        task.parentTaskId = desk.minimizationRoot.rootId
        organizer.onTaskInfoChanged(task)

        organizer.reorderTaskToFront(wct, desk.deskRoot.deskId, task)

        assertThat(wct.hasUnminimizationHops(desk, task.token)).isTrue()
        assertThat(
                wct.hierarchyOps.singleOrNull { hop ->
                    hop.container == task.token.asBinder() &&
                        hop.type == HIERARCHY_OP_TYPE_REORDER &&
                        hop.toTop &&
                        hop.includingParents()
                }
            )
            .isNotNull()
    }

    @Test
    fun onTaskAppeared_visibleDesk_onlyDesk_disablesLaunchAdjacent() = runTest {
        launchAdjacentController.launchAdjacentEnabled = true

        createDeskSuspending(visible = true)

        assertThat(launchAdjacentController.launchAdjacentEnabled).isFalse()
    }

    @Test
    fun onTaskAppeared_invisibleDesk_onlyDesk_enablesLaunchAdjacent() = runTest {
        launchAdjacentController.launchAdjacentEnabled = false

        createDeskSuspending(visible = false)

        assertThat(launchAdjacentController.launchAdjacentEnabled).isTrue()
    }

    @Test
    fun onTaskAppeared_invisibleDesk_otherVisibleDesk_disablesLaunchAdjacent() = runTest {
        launchAdjacentController.launchAdjacentEnabled = true

        createDeskSuspending(visible = true)
        createDeskSuspending(visible = false)

        assertThat(launchAdjacentController.launchAdjacentEnabled).isFalse()
    }

    @Test
    fun onTaskInfoChanged_deskBecomesVisible_onlyDesk_disablesLaunchAdjacent() = runTest {
        launchAdjacentController.launchAdjacentEnabled = true

        val desk = createDeskSuspending(visible = false)
        desk.deskRoot.taskInfo.isVisible = true
        organizer.onTaskInfoChanged(desk.deskRoot.taskInfo)

        assertThat(launchAdjacentController.launchAdjacentEnabled).isFalse()
    }

    @Test
    fun onTaskInfoChanged_deskBecomesInvisible_onlyDesk_enablesLaunchAdjacent() = runTest {
        launchAdjacentController.launchAdjacentEnabled = false

        val desk = createDeskSuspending(visible = true)
        desk.deskRoot.taskInfo.isVisible = false
        organizer.onTaskInfoChanged(desk.deskRoot.taskInfo)

        assertThat(launchAdjacentController.launchAdjacentEnabled).isTrue()
    }

    @Test
    fun onTaskInfoChanged_deskBecomesInvisible_otherVisibleDesk_disablesLaunchAdjacent() = runTest {
        launchAdjacentController.launchAdjacentEnabled = true

        createDeskSuspending(visible = true)
        val desk = createDeskSuspending(visible = true)
        desk.deskRoot.taskInfo.isVisible = false
        organizer.onTaskInfoChanged(desk.deskRoot.taskInfo)

        assertThat(launchAdjacentController.launchAdjacentEnabled).isFalse()
    }

    @Test
    fun onTaskVanished_visibleDeskDisappears_onlyDesk_enablesLaunchAdjacent() = runTest {
        launchAdjacentController.launchAdjacentEnabled = false

        val desk = createDeskSuspending(visible = true)
        organizer.onTaskVanished(desk.deskRoot.taskInfo)

        assertThat(launchAdjacentController.launchAdjacentEnabled).isTrue()
    }

    @Test
    fun onTaskVanished_visibleDeskDisappears_otherDeskVisible_disablesLaunchAdjacent() = runTest {
        launchAdjacentController.launchAdjacentEnabled = true

        createDeskSuspending(visible = true)
        val desk = createDeskSuspending(visible = true)
        organizer.onTaskVanished(desk.deskRoot.taskInfo)

        assertThat(launchAdjacentController.launchAdjacentEnabled).isFalse()
    }

    @Test
    fun onTaskInfoChanged_taskNotRoot_invokesListener() = runTest {
        createDeskSuspending()
        val task = createFreeformTask().apply { taskId = TEST_CHILD_TASK_ID }

        organizer.onTaskInfoChanged(task)

        verify(taskInfoChangedListener).invoke(task)
    }

    @Test
    fun onTaskInfoChanged_isDeskRoot_doesNotInvokeListener() = runTest {
        val deskRoot = createDeskSuspending().deskRoot

        organizer.onTaskInfoChanged(deskRoot.taskInfo)

        verify(taskInfoChangedListener, never()).invoke(any())
    }

    @Test
    fun onTaskInfoChanged_isMinimizationRoot_doesNotInvokeListener() = runTest {
        val minimizationRoot = createDeskSuspending().minimizationRoot

        organizer.onTaskInfoChanged(minimizationRoot.taskInfo)

        verify(taskInfoChangedListener, never()).invoke(any())
    }

    @Test
    fun moveDeskToDisplay_movesOnTop() = runTest {
        createDeskSuspending(userId = PRIMARY_USER_ID, displayId = DEFAULT_DISPLAY)
        val desk2 = createDeskSuspending(userId = PRIMARY_USER_ID, displayId = SECOND_DISPLAY)
        val wct = WindowContainerTransaction()
        val tda = mockTDAOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!

        organizer.moveDeskToDisplay(wct, desk2.deskRoot.deskId, DEFAULT_DISPLAY, onTop = true)

        assertThat(wct.hasDeskReparentHops(desk2, tda.token, toTop = true)).isTrue()
    }

    private data class DeskRoots(
        val deskRoot: DeskRoot,
        val minimizationRoot: DeskMinimizationRoot,
    )

    private suspend fun createDeskSuspending(
        visible: Boolean = true,
        userId: Int = PRIMARY_USER_ID,
        displayId: Int = DEFAULT_DISPLAY,
    ): DeskRoots {
        val freeformRootTask =
            createFreeformTask().apply {
                parentTaskId = -1
                isVisible = visible
                isVisibleRequested = visible
                this.displayId = displayId
            }
        val minimizationRootTask =
            createFreeformTask().apply {
                parentTaskId = -1
                this.displayId = displayId
            }
        Mockito.reset(mockShellTaskOrganizer)
        whenever(
                mockShellTaskOrganizer.createRootTask(
                    any(),
                    eq(organizer)
                )
            )
            .thenAnswer { invocation ->
                val listener = (invocation.arguments[1] as TaskListener)
                listener.onTaskAppeared(freeformRootTask, SurfaceControl())
            }
            .thenAnswer { invocation ->
                val listener = (invocation.arguments[1] as TaskListener)
                listener.onTaskAppeared(minimizationRootTask, SurfaceControl())
            }
        val deskId = organizer.createDeskSuspending(displayId, userId)
        val deskRoot = assertNotNull(organizer.deskRootsByDeskId.get(deskId))
        val minimizationRoot = assertNotNull(organizer.deskMinimizationRootsByDeskId[deskId])
        return DeskRoots(deskRoot, minimizationRoot)
    }

    private fun warmUpDefaultDesk(userId: Int = PRIMARY_USER_ID, displayId: Int = DEFAULT_DISPLAY) {
        val freeformRootTask =
            createFreeformTask().apply {
                parentTaskId = -1
                this.displayId = displayId
            }
        val minimizationRootTask =
            createFreeformTask().apply {
                parentTaskId = -1
                this.displayId = displayId
            }
        Mockito.reset(mockShellTaskOrganizer)
        whenever(
                mockShellTaskOrganizer.createRootTask(
                    any(),
                    eq(organizer)
                )
            )
            .thenAnswer { invocation ->
                val listener = (invocation.arguments[1] as TaskListener)
                listener.onTaskAppeared(freeformRootTask, SurfaceControl())
            }
            .thenAnswer { invocation ->
                val listener = (invocation.arguments[1] as TaskListener)
                listener.onTaskAppeared(minimizationRootTask, SurfaceControl())
            }
        organizer.warmUpDefaultDesk(displayId, userId)
    }

    private fun WindowContainerTransaction.hasMinimizationHops(
        desk: DeskRoots,
        task: WindowContainerToken,
    ): Boolean =
        hierarchyOps.any { hop ->
            hop.isReparent &&
                hop.container == task.asBinder() &&
                hop.newParent == desk.minimizationRoot.token.asBinder()
        }

    private fun WindowContainerTransaction.hasUnminimizationHops(
        desk: DeskRoots,
        task: WindowContainerToken,
    ): Boolean =
        hierarchyOps.any { hop ->
            hop.isReparent &&
                hop.container == task.asBinder() &&
                hop.newParent == desk.deskRoot.token.asBinder() &&
                hop.toTop
        }

    private fun WindowContainerTransaction.hasDeskReparentHops(
        desk: DeskRoots,
        newParent: WindowContainerToken,
        toTop: Boolean,
    ): Boolean {
        return hierarchyOps.any { hop ->
            hop.isReparent &&
                hop.container == desk.deskRoot.token.asBinder() &&
                hop.newParent == newParent.asBinder() &&
                hop.toTop == toTop
        } &&
            hierarchyOps.any { hop ->
                hop.isReparent &&
                    hop.container == desk.minimizationRoot.token.asBinder() &&
                    hop.newParent == newParent.asBinder() &&
                    !hop.toTop
            }
    }

    private suspend fun DesksOrganizer.createDeskSuspending(displayId: Int, userId: Int): Int =
        suspendCoroutine { cont ->
            createDesk(displayId, userId) { deskId -> cont.resumeWith(Result.success(deskId)) }
        }

    private fun ActivityManager.RunningTaskInfo.toChange() =
        TransitionInfo.Change(token, SurfaceControl()).apply { taskInfo = this@toChange }

    companion object {
        private const val PRIMARY_USER_ID = 10
        private const val SECONDARY_USER_ID = 11
        private const val TEST_CHILD_TASK_ID = 100
        private const val SECOND_DISPLAY = 2
    }
}
