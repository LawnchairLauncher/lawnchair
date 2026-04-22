/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.recents

import android.app.ActivityManager
import android.app.TaskInfo
import android.graphics.Rect
import android.os.Parcel
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.window.IWindowContainerToken
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.shared.GroupedTaskInfo
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_DESK
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_FULLSCREEN
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_SPLIT
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

/**
 * Tests for [GroupedTaskInfo]
 * Build & Run: atest WMShellUnitTests:GroupedTaskInfoTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class GroupedTaskInfoTest : ShellTestCase() {

    @Test
    fun testSingleTask_hasCorrectType() {
        assertThat(singleTaskGroupInfo().isBaseType(TYPE_FULLSCREEN)).isTrue()
    }

    @Test
    fun testSingleTask_task1Set_task2Null() {
        val group = singleTaskGroupInfo()
        assertThat(group.taskInfo1?.taskId).isEqualTo(1)
        assertThat(group.taskInfo2).isNull()
    }

    @Test
    fun testSingleTask_taskInfoList_hasOneTask() {
        val list = singleTaskGroupInfo().taskInfoList
        assertThat(list).hasSize(1)
        assertThat(list[0].taskId).isEqualTo(1)
    }

    @Test
    fun testSplitTasks_hasCorrectType() {
        assertThat(splitTasksGroupInfo().isBaseType(TYPE_SPLIT)).isTrue()
    }

    @Test
    fun testSplitTasks_task1Set_task2Set_boundsSet() {
        val group = splitTasksGroupInfo()
        assertThat(group.taskInfo1?.taskId).isEqualTo(1)
        assertThat(group.taskInfo2?.taskId).isEqualTo(2)
        assertThat(group.splitBounds).isNotNull()
    }

    @Test
    fun testSplitTasks_taskInfoList_hasTwoTasks() {
        val list = splitTasksGroupInfo().taskInfoList
        assertThat(list).hasSize(2)
        assertThat(list[0].taskId).isEqualTo(1)
        assertThat(list[1].taskId).isEqualTo(2)
    }

    @Test
    fun testDeskTasks_hasCorrectType() {
        assertThat(deskTasksGroupInfo(deskId = 0, freeformTaskIds = arrayOf(1)).isBaseType(TYPE_DESK))
            .isTrue()
    }

    @Test
    fun testCreateDeskTasks_hasCorrectNumberOfTasks() {
        val list = deskTasksGroupInfo(deskId = 0, freeformTaskIds = arrayOf(1, 2, 3)).taskInfoList
        assertThat(list).hasSize(3)
        assertThat(list[0].taskId).isEqualTo(1)
        assertThat(list[1].taskId).isEqualTo(2)
        assertThat(list[2].taskId).isEqualTo(3)
    }

    @Test
    fun testCreateDeskTasks_nonExistentMinimizedTaskId_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            deskTasksGroupInfo(deskId = 0,
                freeformTaskIds = arrayOf(1, 2, 3),
                minimizedTaskIds = arrayOf(1, 4)
            )
        }
    }

    @Test
    fun testMixedWithFullscreenBase_hasCorrectType() {
        assertThat(mixedTaskGroupInfoWithFullscreenBase().isBaseType(TYPE_FULLSCREEN)).isTrue()
    }

    @Test
    fun testMixedWithSplitBase_hasCorrectType() {
        assertThat(mixedTaskGroupInfoWithSplitBase().isBaseType(TYPE_SPLIT)).isTrue()
    }

    @Test
    fun testMixedWithDeskBase_hasCorrectType() {
        assertThat(mixedTaskGroupInfoWithDeskBase().isBaseType(TYPE_DESK)).isTrue()
    }

    @Test
    fun testMixed_disallowEmptyMixed() {
        assertThrows(IllegalArgumentException::class.java) {
            GroupedTaskInfo.forMixed(listOf())
        }
    }

    @Test
    fun testMixed_disallowNestedMixed() {
        assertThrows(IllegalArgumentException::class.java) {
            GroupedTaskInfo.forMixed(listOf(
                GroupedTaskInfo.forMixed(listOf(singleTaskGroupInfo()))))
        }
    }

    @Test
    fun testMixed_disallowNonMixedAccessors() {
        val mixed = mixedTaskGroupInfoWithFullscreenBase()
        assertThrows(IllegalStateException::class.java) {
            mixed.taskInfo1
        }
        assertThrows(IllegalStateException::class.java) {
            mixed.taskInfo2
        }
        assertThrows(IllegalStateException::class.java) {
            mixed.splitBounds
        }
        assertThrows(IllegalStateException::class.java) {
            mixed.minimizedTaskIds
        }
    }

    @Test
    fun testParcelling_singleTask() {
        val taskInfo = singleTaskGroupInfo()
        val parcel = Parcel.obtain()
        taskInfo.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        // Read the object back from the parcel
        val taskInfoFromParcel: GroupedTaskInfo =
            GroupedTaskInfo.CREATOR.createFromParcel(parcel)
        assertThat(taskInfoFromParcel.isBaseType(TYPE_FULLSCREEN)).isTrue()
        assertThat(taskInfoFromParcel.taskInfo1?.taskId).isEqualTo(1)
        assertThat(taskInfoFromParcel.taskInfo2).isNull()
    }

    @Test
    fun testParcelling_splitTasks() {
        val taskInfo = splitTasksGroupInfo()
        val parcel = Parcel.obtain()
        taskInfo.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        // Read the object back from the parcel
        val taskInfoFromParcel: GroupedTaskInfo =
            GroupedTaskInfo.CREATOR.createFromParcel(parcel)
        assertThat(taskInfoFromParcel.isBaseType(TYPE_SPLIT)).isTrue()
        assertThat(taskInfoFromParcel.taskInfo1?.taskId).isEqualTo(1)
        assertThat(taskInfoFromParcel.taskInfo2?.taskId).isEqualTo(2)
        assertThat(taskInfoFromParcel.splitBounds?.snapPosition).isEqualTo(SNAP_TO_2_50_50)
    }

    @Test
    fun testParcelling_DeskTasks() {
        val taskInfo = deskTasksGroupInfo(deskId = 50, freeformTaskIds = arrayOf(1, 2, 3))
        val parcel = Parcel.obtain()
        taskInfo.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        // Read the object back from the parcel
        val taskInfoFromParcel: GroupedTaskInfo =
            GroupedTaskInfo.CREATOR.createFromParcel(parcel)
        assertThat(taskInfoFromParcel.deskId).isEqualTo(taskInfo.deskId)
        assertThat(taskInfoFromParcel.isBaseType(TYPE_DESK)).isTrue()
        assertThat(taskInfoFromParcel.taskInfoList).hasSize(3)
        // Only compare task ids
        val taskIdComparator = Correspondence.transforming<TaskInfo, Int>(
            { it.taskId }, "has taskId of"
        )
        assertThat(taskInfoFromParcel.taskInfoList).comparingElementsUsing(taskIdComparator)
            .containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun testParcelling_DeskTasks_minimizedTasks() {
        val taskInfo = deskTasksGroupInfo(deskId = 0,
            freeformTaskIds = arrayOf(1, 2, 3), minimizedTaskIds = arrayOf(2))

        val parcel = Parcel.obtain()
        taskInfo.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        // Read the object back from the parcel
        val taskInfoFromParcel: GroupedTaskInfo =
            GroupedTaskInfo.CREATOR.createFromParcel(parcel)
        assertThat(taskInfoFromParcel.isBaseType(TYPE_DESK)).isTrue()
        assertThat(taskInfoFromParcel.minimizedTaskIds).isEqualTo(arrayOf(2).toIntArray())
    }

    @Test
    fun testParcelling_mixedTasks() {
        val taskInfo = GroupedTaskInfo.forMixed(listOf(
                deskTasksGroupInfo(deskId = 0, freeformTaskIds = arrayOf(4, 5, 6),
                    minimizedTaskIds = arrayOf(5)),
                splitTasksGroupInfo(firstId = 2, secondId = 3),
                singleTaskGroupInfo(id = 1)))

        val parcel = Parcel.obtain()
        taskInfo.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        // Read the object back from the parcel
        val taskInfoFromParcel: GroupedTaskInfo =
            GroupedTaskInfo.CREATOR.createFromParcel(parcel)
        assertThat(taskInfoFromParcel.isBaseType(TYPE_DESK)).isTrue()
        assertThat(taskInfoFromParcel.baseGroupedTask.minimizedTaskIds).isEqualTo(
            arrayOf(5).toIntArray())
        for (i in 1..6) {
            assertThat(taskInfoFromParcel.containsTask(i)).isTrue()
        }
        assertThat(taskInfoFromParcel.taskInfoList).hasSize(taskInfo.taskInfoList.size)
    }

    @Test
    fun testTaskProperties_singleTasks() {
        val task1 = createTaskInfo(id = 1234)

        val taskInfo = GroupedTaskInfo.forFullscreenTasks(task1)

        assertThat(taskInfo.getTaskById(1234)).isEqualTo(task1)
        assertThat(taskInfo.containsTask(1234)).isTrue()
        assertThat(taskInfo.taskInfoList).isEqualTo(listOf(task1))
    }

    @Test
    fun testTaskProperties_splitTasks() {
        val task1 = createTaskInfo(id = 1)
        val task2 = createTaskInfo(id = 2)
        val splitBounds = SplitBounds(Rect(), Rect(), 1, 2, SNAP_TO_2_50_50)

        val taskInfo = GroupedTaskInfo.forSplitTasks(task1, task2, splitBounds)

        assertThat(taskInfo.getTaskById(1)).isEqualTo(task1)
        assertThat(taskInfo.getTaskById(2)).isEqualTo(task2)
        assertThat(taskInfo.containsTask(1)).isTrue()
        assertThat(taskInfo.containsTask(2)).isTrue()
        assertThat(taskInfo.taskInfoList).isEqualTo(listOf(task1, task2))
    }

    @Test
    fun testTaskProperties_DeskTasks() {
        val task1 = createTaskInfo(id = 1)
        val task2 = createTaskInfo(id = 2)

        val taskInfo = GroupedTaskInfo.forDeskTasks(
            /* deskId = */ 500, DEFAULT_DISPLAY, listOf(task1, task2), setOf()
        )

        assertThat(taskInfo.deskId).isEqualTo(500)
        assertThat(taskInfo.getTaskById(1)).isEqualTo(task1)
        assertThat(taskInfo.getTaskById(2)).isEqualTo(task2)
        assertThat(taskInfo.containsTask(1)).isTrue()
        assertThat(taskInfo.containsTask(2)).isTrue()
        assertThat(taskInfo.taskInfoList).isEqualTo(listOf(task1, task2))
    }

    @Test
    fun testTaskProperties_mixedTasks() {
        val task1 = createTaskInfo(id = 1)
        val task2 = createTaskInfo(id = 2)
        val task3 = createTaskInfo(id = 3)
        val splitBounds = SplitBounds(Rect(), Rect(), 1, 2, SNAP_TO_2_50_50)

        val splitTasks = GroupedTaskInfo.forSplitTasks(task1, task2, splitBounds)
        val fullscreenTasks = GroupedTaskInfo.forFullscreenTasks(task3)
        val mixedTasks = GroupedTaskInfo.forMixed(listOf(splitTasks, fullscreenTasks))

        assertThat(mixedTasks.getTaskById(1)).isEqualTo(task1)
        assertThat(mixedTasks.getTaskById(2)).isEqualTo(task2)
        assertThat(mixedTasks.getTaskById(3)).isEqualTo(task3)
        assertThat(mixedTasks.containsTask(1)).isTrue()
        assertThat(mixedTasks.containsTask(2)).isTrue()
        assertThat(mixedTasks.containsTask(3)).isTrue()
        assertThat(mixedTasks.taskInfoList).isEqualTo(listOf(task1, task2, task3))
    }

    private fun createTaskInfo(id: Int) = ActivityManager.RecentTaskInfo().apply {
        taskId = id
        token = WindowContainerToken(mock(IWindowContainerToken::class.java))
    }

    private fun singleTaskGroupInfo(id: Int = 1): GroupedTaskInfo {
        val task = createTaskInfo(id)
        return GroupedTaskInfo.forFullscreenTasks(task)
    }

    private fun splitTasksGroupInfo(firstId: Int = 1, secondId: Int = 2): GroupedTaskInfo {
        val task1 = createTaskInfo(firstId)
        val task2 = createTaskInfo(secondId)
        val splitBounds = SplitBounds(Rect(), Rect(), 1, 2, SNAP_TO_2_50_50)
        return GroupedTaskInfo.forSplitTasks(task1, task2, splitBounds)
    }

    private fun deskTasksGroupInfo(
        deskId: Int,
        freeformTaskIds: Array<Int>,
        minimizedTaskIds: Array<Int> = emptyArray()
    ): GroupedTaskInfo {
        return GroupedTaskInfo.forDeskTasks(
            deskId,
            DEFAULT_DISPLAY,
            freeformTaskIds.map { createTaskInfo(it) }.toList(),
            minimizedTaskIds.toSet())
    }

    private fun mixedTaskGroupInfoWithFullscreenBase(): GroupedTaskInfo {
        return GroupedTaskInfo.forMixed(listOf(
            singleTaskGroupInfo(id = 1),
            singleTaskGroupInfo(id = 2)))
    }

    private fun mixedTaskGroupInfoWithSplitBase(): GroupedTaskInfo {
        return GroupedTaskInfo.forMixed(listOf(
            splitTasksGroupInfo(firstId = 2, secondId = 3),
            singleTaskGroupInfo(id = 1)))
    }

    private fun mixedTaskGroupInfoWithDeskBase(): GroupedTaskInfo {
        return GroupedTaskInfo.forMixed(listOf(
            deskTasksGroupInfo(deskId = 0, freeformTaskIds = arrayOf(2, 3, 4)),
            singleTaskGroupInfo(id = 1)))
    }
}
