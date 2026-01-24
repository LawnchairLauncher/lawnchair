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

package com.android.wm.shell.util

import android.app.ActivityManager.RunningTaskInfo
import android.view.SurfaceControl
import com.android.wm.shell.ShellTaskOrganizer.TaskAppearedListener
import com.android.wm.shell.ShellTaskOrganizer.TaskInfoChangedListener
import com.android.wm.shell.ShellTaskOrganizer.TaskVanishedListener

@DslMarker
annotation class TaskListenerTestTagMarker

// Base class for the TaskListener interfaces Test Context.
open class BaseTaskListenerTestContext<TL> {

    protected lateinit var inputTaskInfo: RunningTaskInfo

    fun runningTaskInfo(
        builder: RunningTaskInfoTestInputBuilder.(RunningTaskInfo) -> Unit
    ): RunningTaskInfo {
        val runningTaskInfoObj = RunningTaskInfoTestInputBuilder()
        return RunningTaskInfo().also {
            runningTaskInfoObj.builder(it)
        }.apply {
            inputTaskInfo = this
        }
    }
}

@TaskListenerTestTagMarker
class TaskAppearedListenerTestContext(
    private val testSubjectFactory: () -> TaskAppearedListener
) : BaseTaskListenerTestContext<TaskAppearedListener>() {

    private var inputLeash: SurfaceControl = SurfaceControl()

    fun leash(builder: SurfaceControlTestInputBuilder.() -> SurfaceControl): SurfaceControl {
        val binderObj = SurfaceControlTestInputBuilder()
        return binderObj.builder().apply {
            inputLeash = this
        }
    }

    fun validateOnTaskAppeared(verifier: () -> Unit) {
        // We execute the test subject using the input
        testSubjectFactory().onTaskAppeared(inputTaskInfo, inputLeash)
        verifier()
    }
}

/**
 * Function to run tests for the different [TaskAppearedListener] implementations.
 */
fun testTaskAppearedListener(
    testSubjectFactory: () -> TaskAppearedListener,
    init: TaskAppearedListenerTestContext.() -> Unit
): TaskAppearedListenerTestContext {
    val testContext = TaskAppearedListenerTestContext(testSubjectFactory)
    testContext.init()
    return testContext
}

@TaskListenerTestTagMarker
class TaskVanishedListenerTestContext(
    private val testSubjectFactory: () -> TaskVanishedListener
) : BaseTaskListenerTestContext<TaskVanishedListener>() {

    fun validateOnTaskVanished(verifier: () -> Unit) {
        // We execute the test subject using the input
        testSubjectFactory().onTaskVanished(inputTaskInfo)
        verifier()
    }
}

/**
 * Function to run tests for the different [TaskVanishedListener] implementations.
 */
fun testTaskVanishedListener(
    testSubjectFactory: () -> TaskVanishedListener,
    init: TaskVanishedListenerTestContext.() -> Unit
): TaskVanishedListenerTestContext {
    val testContext = TaskVanishedListenerTestContext(testSubjectFactory)
    testContext.init()
    return testContext
}

@TaskListenerTestTagMarker
class TaskInfoChangedListenerTestContext(
    private val testSubjectFactory: () -> TaskInfoChangedListener
) : BaseTaskListenerTestContext<TaskInfoChangedListener>() {

    fun validateOnTaskInfoChanged(verifier: () -> Unit) {
        // We execute the test subject using the input
        testSubjectFactory().onTaskInfoChanged(inputTaskInfo)
        verifier()
    }
}

/**
 * Function to run tests for the different [TaskInfoChangedListener] implementations.
 */
fun testTaskInfoChangedListener(
    testSubjectFactory: () -> TaskInfoChangedListener,
    init: TaskInfoChangedListenerTestContext.() -> Unit
): TaskInfoChangedListenerTestContext {
    val testContext = TaskInfoChangedListenerTestContext(testSubjectFactory)
    testContext.init()
    return testContext
}
