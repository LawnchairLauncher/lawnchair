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
import android.graphics.Rect
import android.os.Parcel
import android.testing.AndroidTestingRunner
import android.window.IWindowContainerToken
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.split.SplitScreenConstants.SNAP_TO_50_50
import com.android.wm.shell.util.GroupedRecentTaskInfo
import com.android.wm.shell.util.GroupedRecentTaskInfo.CREATOR
import com.android.wm.shell.util.GroupedRecentTaskInfo.TYPE_FREEFORM
import com.android.wm.shell.util.GroupedRecentTaskInfo.TYPE_SINGLE
import com.android.wm.shell.util.GroupedRecentTaskInfo.TYPE_SPLIT
import com.android.wm.shell.util.SplitBounds
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

/**
 * Tests for [GroupedRecentTaskInfo]
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class GroupedRecentTaskInfoTest : ShellTestCase() {

    @Test
    fun testSingleTask_hasCorrectType() {
        assertThat(singleTaskGroupInfo().type).isEqualTo(TYPE_SINGLE)
    }

    @Test
    fun testSingleTask_task1Set_task2Null() {
        val group = singleTaskGroupInfo()
        assertThat(group.taskInfo1.taskId).isEqualTo(1)
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
        assertThat(splitTasksGroupInfo().type).isEqualTo(TYPE_SPLIT)
    }

    @Test
    fun testSplitTasks_task1Set_task2Set_boundsSet() {
        val group = splitTasksGroupInfo()
        assertThat(group.taskInfo1.taskId).isEqualTo(1)
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
    fun testFreeformTasks_hasCorrectType() {
        assertThat(freeformTasksGroupInfo().type).isEqualTo(TYPE_FREEFORM)
    }

    @Test
    fun testSplitTasks_taskInfoList_hasThreeTasks() {
        val list = freeformTasksGroupInfo().taskInfoList
        assertThat(list).hasSize(3)
        assertThat(list[0].taskId).isEqualTo(1)
        assertThat(list[1].taskId).isEqualTo(2)
        assertThat(list[2].taskId).isEqualTo(3)
    }

    @Test
    fun testParcelling_singleTask() {
        val recentTaskInfo = singleTaskGroupInfo()
        val parcel = Parcel.obtain()
        recentTaskInfo.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        // Read the object back from the parcel
        val recentTaskInfoParcel = CREATOR.createFromParcel(parcel)
        assertThat(recentTaskInfoParcel.type).isEqualTo(TYPE_SINGLE)
        assertThat(recentTaskInfoParcel.taskInfo1.taskId).isEqualTo(1)
        assertThat(recentTaskInfoParcel.taskInfo2).isNull()
    }

    @Test
    fun testParcelling_splitTasks() {
        val recentTaskInfo = splitTasksGroupInfo()
        val parcel = Parcel.obtain()
        recentTaskInfo.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        // Read the object back from the parcel
        val recentTaskInfoParcel = CREATOR.createFromParcel(parcel)
        assertThat(recentTaskInfoParcel.type).isEqualTo(TYPE_SPLIT)
        assertThat(recentTaskInfoParcel.taskInfo1.taskId).isEqualTo(1)
        assertThat(recentTaskInfoParcel.taskInfo2).isNotNull()
        assertThat(recentTaskInfoParcel.taskInfo2!!.taskId).isEqualTo(2)
        assertThat(recentTaskInfoParcel.splitBounds).isNotNull()
        assertThat(recentTaskInfoParcel.splitBounds!!.snapPosition).isEqualTo(SNAP_TO_50_50)
    }

    @Test
    fun testParcelling_freeformTasks() {
        val recentTaskInfo = freeformTasksGroupInfo()
        val parcel = Parcel.obtain()
        recentTaskInfo.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        // Read the object back from the parcel
        val recentTaskInfoParcel = CREATOR.createFromParcel(parcel)
        assertThat(recentTaskInfoParcel.type).isEqualTo(TYPE_FREEFORM)
        assertThat(recentTaskInfoParcel.taskInfoList).hasSize(3)
        // Only compare task ids
        val taskIdComparator = Correspondence.transforming<ActivityManager.RecentTaskInfo, Int>(
            { it?.taskId }, "has taskId of"
        )
        assertThat(recentTaskInfoParcel.taskInfoList).comparingElementsUsing(taskIdComparator)
            .containsExactly(1, 2, 3)
    }

    private fun createTaskInfo(id: Int) = ActivityManager.RecentTaskInfo().apply {
        taskId = id
        token = WindowContainerToken(mock(IWindowContainerToken::class.java))
    }

    private fun singleTaskGroupInfo(): GroupedRecentTaskInfo {
        val task = createTaskInfo(id = 1)
        return GroupedRecentTaskInfo.forSingleTask(task)
    }

    private fun splitTasksGroupInfo(): GroupedRecentTaskInfo {
        val task1 = createTaskInfo(id = 1)
        val task2 = createTaskInfo(id = 2)
        val splitBounds = SplitBounds(Rect(), Rect(), 1, 2, SNAP_TO_50_50)
        return GroupedRecentTaskInfo.forSplitTasks(task1, task2, splitBounds)
    }

    private fun freeformTasksGroupInfo(): GroupedRecentTaskInfo {
        val task1 = createTaskInfo(id = 1)
        val task2 = createTaskInfo(id = 2)
        val task3 = createTaskInfo(id = 3)
        return GroupedRecentTaskInfo.forFreeformTasks(task1, task2, task3)
    }
}
