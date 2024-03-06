/*
 *  Copyright (C) 2023 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.quickstep.util

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherState
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.quickstep.RecentsModel
import com.android.quickstep.SystemUiProxy
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.common.split.SplitScreenConstants.SNAP_TO_50_50
import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SplitSelectStateControllerTest {

    private val systemUiProxy: SystemUiProxy = mock()
    private val depthController: DepthController = mock()
    private val statsLogManager: StatsLogManager = mock()
    private val stateManager: StateManager<LauncherState> = mock()
    private val handler: Handler = mock()
    private val context: StatefulActivity<*> = mock()
    private val recentsModel: RecentsModel = mock()
    private val pendingIntent: PendingIntent = mock()

    lateinit var splitSelectStateController: SplitSelectStateController

    private val primaryUserHandle = UserHandle(ActivityManager.RunningTaskInfo().userId)
    private val nonPrimaryUserHandle = UserHandle(ActivityManager.RunningTaskInfo().userId + 10)

    private var taskIdCounter = 0
    private fun getUniqueId(): Int {
        return ++taskIdCounter
    }

    @Before
    fun setup() {
        splitSelectStateController =
            SplitSelectStateController(
                context,
                handler,
                stateManager,
                depthController,
                statsLogManager,
                systemUiProxy,
                recentsModel,
                null /*activityBackCallback*/
            )
    }

    @Test
    fun activeTasks_noMatchingTasks() {
        val nonMatchingComponent = ComponentKey(ComponentName("no", "match"), primaryUserHandle)
        val groupTask1 =
            generateGroupTask(
                ComponentName("pomegranate", "juice"),
                ComponentName("pumpkin", "pie")
            )
        val groupTask2 =
            generateGroupTask(
                ComponentName("hotdog", "juice"),
                ComponentName("personal", "computer")
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask1)
        tasks.add(groupTask2)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<List<Task>> { assertNull("No tasks should have matched", it[0] /*task*/) }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<ArrayList<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(nonMatchingComponent),
                        false /* findExactPairMatch */,
                        taskConsumer
                    )
                    verify(recentsModel).getTasks(capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_singleMatchingTask() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingComponent =
            ComponentKey(ComponentName(matchingPackage, matchingClass), primaryUserHandle)
        val groupTask1 =
            generateGroupTask(
                ComponentName(matchingPackage, matchingClass),
                ComponentName("pomegranate", "juice")
            )
        val groupTask2 =
            generateGroupTask(
                ComponentName("pumpkin", "pie"),
                ComponentName("personal", "computer")
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask1)
        tasks.add(groupTask2)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<List<Task>> {
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass
                )
                assertEquals(it[0], groupTask1.task1)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<ArrayList<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingComponent),
                        false /* findExactPairMatch */,
                        taskConsumer
                    )
                    verify(recentsModel).getTasks(capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_skipTaskWithDifferentUser() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val nonPrimaryUserComponent =
            ComponentKey(ComponentName(matchingPackage, matchingClass), nonPrimaryUserHandle)
        val groupTask1 =
            generateGroupTask(
                ComponentName(matchingPackage, matchingClass),
                ComponentName("pomegranate", "juice")
            )
        val groupTask2 =
            generateGroupTask(
                ComponentName("pumpkin", "pie"),
                ComponentName("personal", "computer")
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask1)
        tasks.add(groupTask2)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<List<Task>> { assertNull("No tasks should have matched", it[0] /*task*/) }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<ArrayList<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(nonPrimaryUserComponent),
                        false /* findExactPairMatch */,
                        taskConsumer
                    )
                    verify(recentsModel).getTasks(capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_findTaskAsNonPrimaryUser() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val nonPrimaryUserComponent =
            ComponentKey(ComponentName(matchingPackage, matchingClass), nonPrimaryUserHandle)
        val groupTask1 =
            generateGroupTask(
                ComponentName(matchingPackage, matchingClass),
                nonPrimaryUserHandle,
                ComponentName("pomegranate", "juice"),
                nonPrimaryUserHandle
            )
        val groupTask2 =
            generateGroupTask(
                ComponentName("pumpkin", "pie"),
                ComponentName("personal", "computer")
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask1)
        tasks.add(groupTask2)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<List<Task>> {
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass
                )
                assertEquals("userId mismatched", it[0].key.userId, nonPrimaryUserHandle.identifier)
                assertEquals(it[0], groupTask1.task1)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<ArrayList<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(nonPrimaryUserComponent),
                        false /* findExactPairMatch */,
                        taskConsumer
                    )
                    verify(recentsModel).getTasks(capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleMatchMostRecentTask() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingComponent =
            ComponentKey(ComponentName(matchingPackage, matchingClass), primaryUserHandle)
        val groupTask1 =
            generateGroupTask(
                ComponentName(matchingPackage, matchingClass),
                ComponentName("pumpkin", "pie")
            )
        val groupTask2 =
            generateGroupTask(
                ComponentName("pomegranate", "juice"),
                ComponentName(matchingPackage, matchingClass)
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<List<Task>> {
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass
                )
                assertEquals(it[0], groupTask1.task1)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<ArrayList<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingComponent),
                        false /* findExactPairMatch */,
                        taskConsumer
                    )
                    verify(recentsModel).getTasks(capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleSearchShouldFindTask() {
        val nonMatchingComponent = ComponentKey(ComponentName("no", "match"), primaryUserHandle)
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingComponent =
            ComponentKey(ComponentName(matchingPackage, matchingClass), primaryUserHandle)

        val groupTask1 =
            generateGroupTask(ComponentName("hotdog", "pie"), ComponentName("pumpkin", "pie"))
        val groupTask2 =
            generateGroupTask(
                ComponentName("pomegranate", "juice"),
                ComponentName(matchingPackage, matchingClass)
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<List<Task>> {
                assertEquals("Expected array length 2", 2, it.size)
                assertNull("No tasks should have matched", it[0] /*task*/)
                assertEquals(
                    "ComponentName package mismatched",
                    it[1].key.baseIntent.component?.packageName,
                    matchingPackage
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[1].key.baseIntent.component?.className,
                    matchingClass
                )
                assertEquals(it[1], groupTask2.task2)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<ArrayList<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(nonMatchingComponent, matchingComponent),
                        false /* findExactPairMatch */,
                        taskConsumer
                    )
                    verify(recentsModel).getTasks(capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleSearchShouldNotFindSameTaskTwice() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingComponent =
            ComponentKey(ComponentName(matchingPackage, matchingClass), primaryUserHandle)

        val groupTask1 =
            generateGroupTask(ComponentName("hotdog", "pie"), ComponentName("pumpkin", "pie"))
        val groupTask2 =
            generateGroupTask(
                ComponentName("pomegranate", "juice"),
                ComponentName(matchingPackage, matchingClass)
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<List<Task>> {
                assertEquals("Expected array length 2", 2, it.size)
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass
                )
                assertEquals(it[0], groupTask2.task2)
                assertNull("No tasks should have matched", it[1] /*task*/)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<ArrayList<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingComponent, matchingComponent),
                        false /* findExactPairMatch */,
                        taskConsumer
                    )
                    verify(recentsModel).getTasks(capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleSearchShouldFindDifferentInstancesOfSameTask() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingComponent =
            ComponentKey(ComponentName(matchingPackage, matchingClass), primaryUserHandle)

        val groupTask1 =
            generateGroupTask(
                ComponentName(matchingPackage, matchingClass),
                ComponentName("pumpkin", "pie")
            )
        val groupTask2 =
            generateGroupTask(
                ComponentName("pomegranate", "juice"),
                ComponentName(matchingPackage, matchingClass)
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<List<Task>> {
                assertEquals("Expected array length 2", 2, it.size)
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass
                )
                assertEquals(it[0], groupTask1.task1)
                assertEquals(
                    "ComponentName package mismatched",
                    it[1].key.baseIntent.component?.packageName,
                    matchingPackage
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[1].key.baseIntent.component?.className,
                    matchingClass
                )
                assertEquals(it[1], groupTask2.task2)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<ArrayList<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingComponent, matchingComponent),
                        false /* findExactPairMatch */,
                        taskConsumer
                    )
                    verify(recentsModel).getTasks(capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleSearchShouldFindExactPairMatch() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingComponent =
            ComponentKey(ComponentName(matchingPackage, matchingClass), primaryUserHandle)
        val matchingPackage2 = "pomegranate"
        val matchingClass2 = "juice"
        val matchingComponent2 =
            ComponentKey(ComponentName(matchingPackage2, matchingClass2), primaryUserHandle)

        val groupTask1 =
            generateGroupTask(ComponentName("hotdog", "pie"), ComponentName("pumpkin", "pie"))
        val groupTask2 =
            generateGroupTask(
                ComponentName(matchingPackage2, matchingClass2),
                ComponentName(matchingPackage, matchingClass)
            )
        val groupTask3 =
            generateGroupTask(
                ComponentName("hotdog", "pie"),
                ComponentName(matchingPackage, matchingClass)
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask3)
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<List<Task>> {
                assertEquals("Expected array length 1", 1, it.size)
                assertEquals("Found wrong task", it[0], groupTask2.task1)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<ArrayList<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingComponent2, matchingComponent),
                        true /* findExactPairMatch */,
                        taskConsumer
                    )
                    verify(recentsModel).getTasks(capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun setInitialApp_withTaskId() {
        splitSelectStateController.setInitialTaskSelect(
            null /*intent*/,
            -1 /*stagePosition*/,
            ItemInfo(),
            null /*splitEvent*/,
            10 /*alreadyRunningTask*/
        )
        assertTrue(splitSelectStateController.isSplitSelectActive)
    }

    @Test
    fun setInitialApp_withIntent() {
        splitSelectStateController.setInitialTaskSelect(
            Intent() /*intent*/,
            -1 /*stagePosition*/,
            ItemInfo(),
            null /*splitEvent*/,
            -1 /*alreadyRunningTask*/
        )
        assertTrue(splitSelectStateController.isSplitSelectActive)
    }

    @Test
    fun resetAfterInitial() {
        splitSelectStateController.setInitialTaskSelect(
            Intent() /*intent*/,
            -1 /*stagePosition*/,
            ItemInfo(),
            null /*splitEvent*/,
            -1
        )
        splitSelectStateController.resetState()
        assertFalse(splitSelectStateController.isSplitSelectActive)
    }

    @Test
    fun secondPendingIntentSet() {
        val itemInfo = ItemInfo()
        whenever(pendingIntent.creatorUserHandle).thenReturn(primaryUserHandle)
        splitSelectStateController.setInitialTaskSelect(null, 0, itemInfo, null, 1)
        splitSelectStateController.setSecondTask(pendingIntent)
        assertTrue(splitSelectStateController.isBothSplitAppsConfirmed)
    }

    // Generate GroupTask with default userId.
    private fun generateGroupTask(
        task1ComponentName: ComponentName,
        task2ComponentName: ComponentName
    ): GroupTask {
        val task1 = Task()
        var taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = getUniqueId()
        var intent = Intent()
        intent.component = task1ComponentName
        taskInfo.baseIntent = intent
        task1.key = Task.TaskKey(taskInfo)

        val task2 = Task()
        taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = getUniqueId()
        intent = Intent()
        intent.component = task2ComponentName
        taskInfo.baseIntent = intent
        task2.key = Task.TaskKey(taskInfo)
        return GroupTask(
            task1,
            task2,
            SplitConfigurationOptions.SplitBounds(Rect(), Rect(), -1, -1, SNAP_TO_50_50)
        )
    }

    // Generate GroupTask with custom user handles.
    private fun generateGroupTask(
        task1ComponentName: ComponentName,
        userHandle1: UserHandle,
        task2ComponentName: ComponentName,
        userHandle2: UserHandle
    ): GroupTask {
        val task1 = Task()
        var taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = getUniqueId()
        // Apply custom userHandle1
        taskInfo.userId = userHandle1.identifier
        var intent = Intent()
        intent.component = task1ComponentName
        taskInfo.baseIntent = intent
        task1.key = Task.TaskKey(taskInfo)
        val task2 = Task()
        taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = getUniqueId()
        // Apply custom userHandle2
        taskInfo.userId = userHandle2.identifier
        intent = Intent()
        intent.component = task2ComponentName
        taskInfo.baseIntent = intent
        task2.key = Task.TaskKey(taskInfo)
        return GroupTask(
            task1,
            task2,
            SplitConfigurationOptions.SplitBounds(Rect(), Rect(), -1, -1, SNAP_TO_50_50)
        )
    }
}
