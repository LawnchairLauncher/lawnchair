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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherState
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.withArgCaptor
import com.android.quickstep.RecentsModel
import com.android.quickstep.SystemUiProxy
import com.android.systemui.shared.recents.model.Task
import java.util.ArrayList
import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class SplitSelectStateControllerTest {

    @Mock lateinit var systemUiProxy: SystemUiProxy
    @Mock lateinit var depthController: DepthController
    @Mock lateinit var statsLogManager: StatsLogManager
    @Mock lateinit var stateManager: StateManager<LauncherState>
    @Mock lateinit var handler: Handler
    @Mock lateinit var context: Context
    @Mock lateinit var recentsModel: RecentsModel

    lateinit var splitSelectStateController: SplitSelectStateController

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        splitSelectStateController =
            SplitSelectStateController(
                context,
                handler,
                stateManager,
                depthController,
                statsLogManager,
                systemUiProxy,
                recentsModel
            )
    }

    @Test
    fun activeTasks_noMatchingTasks() {
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
        // #findLastActiveTaskAndRunCallback
        val taskConsumer =
            Consumer<Task> { assertNull("No tasks should have matched", it /*task*/) }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            withArgCaptor<Consumer<ArrayList<GroupTask>>> {
                splitSelectStateController.findLastActiveTaskAndRunCallback(
                    ComponentName("no", "match"),
                    taskConsumer
                )
                verify(recentsModel).getTasks(capture())
            }

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_singleMatchingTask() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
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
        // #findLastActiveTaskAndRunCallback
        val taskConsumer =
            Consumer<Task> {
                assertEquals(
                    "ComponentName package mismatched",
                    it.key.baseIntent.component.packageName,
                    matchingPackage
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it.key.baseIntent.component.className,
                    matchingClass
                )
                assertEquals(it, groupTask1.task1)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            withArgCaptor<Consumer<ArrayList<GroupTask>>> {
                splitSelectStateController.findLastActiveTaskAndRunCallback(
                    ComponentName(matchingPackage, matchingClass),
                    taskConsumer
                )
                verify(recentsModel).getTasks(capture())
            }

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleMatchMostRecentTask() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
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
        // #findLastActiveTaskAndRunCallback
        val taskConsumer =
            Consumer<Task> {
                assertEquals(
                    "ComponentName package mismatched",
                    it.key.baseIntent.component.packageName,
                    matchingPackage
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it.key.baseIntent.component.className,
                    matchingClass
                )
                assertEquals(it, groupTask2.task2)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            withArgCaptor<Consumer<ArrayList<GroupTask>>> {
                splitSelectStateController.findLastActiveTaskAndRunCallback(
                    ComponentName(matchingPackage, matchingClass),
                    taskConsumer
                )
                verify(recentsModel).getTasks(capture())
            }

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

    private fun generateGroupTask(
        task1ComponentName: ComponentName,
        task2ComponentName: ComponentName
    ): GroupTask {
        val task1 = Task()
        var taskInfo = ActivityManager.RunningTaskInfo()
        var intent = Intent()
        intent.component = task1ComponentName
        taskInfo.baseIntent = intent
        task1.key = Task.TaskKey(taskInfo)

        val task2 = Task()
        taskInfo = ActivityManager.RunningTaskInfo()
        intent = Intent()
        intent.component = task2ComponentName
        taskInfo.baseIntent = intent
        task2.key = Task.TaskKey(taskInfo)
        return GroupTask(
            task1,
            task2,
            SplitConfigurationOptions.SplitBounds(Rect(), Rect(), -1, -1)
        )
    }
}
