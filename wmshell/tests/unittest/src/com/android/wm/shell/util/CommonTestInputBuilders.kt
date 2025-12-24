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
import android.app.TaskInfo
import android.content.ComponentName
import android.graphics.Point
import android.graphics.Rect
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TransitionFlags
import android.view.WindowManager.TransitionType
import android.window.ActivityTransitionInfo
import android.window.AppCompatTransitionInfo
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionInfo.FLAG_NONE
import android.window.TransitionInfo.TransitionMode
import android.window.WindowContainerToken
import org.mockito.kotlin.mock

/**
 * Abstracts any object responsible to create an input builder.
 */
interface TestInputBuilder<T> {
    fun build(): T
}

/**
 * Base class for Test Contexts requiring a [Change] object.
 */
open class BaseChangeTestContext {

    protected lateinit var inputObject: Change

    fun inputChange(builder: ChangeTestInputBuilder.() -> Unit): Change {
        val inputFactoryObj = ChangeTestInputBuilder()
        inputFactoryObj.builder()
        return inputFactoryObj.build().apply {
            inputObject = this
        }
    }
}

/**
 * Base class for Test Contexts requiring a [TaskInfo] object.
 */
open class BaseRunningTaskInfoTestContext {

    protected lateinit var taskInfo: RunningTaskInfo

    fun runningTaskInfo(
        builder: RunningTaskInfoTestInputBuilder.(RunningTaskInfo) -> Unit
    ): RunningTaskInfo {
        val runningTaskInfoObj = RunningTaskInfoTestInputBuilder()
        return RunningTaskInfo().also {
            runningTaskInfoObj.builder(it)
        }.apply {
            taskInfo = this
        }
    }
}

/**
 * [InputBuilder] that helps in the creation of a [Change] object for testing.
 */
class ChangeTestInputBuilder : TestInputBuilder<Change> {

    // TODO(b/419766870): Implement TestInputBuilder for main objects in input on tests.
    private val inputParams = InputParams()
    var endAbsBounds: Rect? = null
    var endRelOffset: Point? = null
    var flags: Int = FLAG_NONE
    @TransitionMode var mode: Int = TRANSIT_NONE
    data class InputParams(
        var token: WindowContainerToken = mock<WindowContainerToken>(),
        var leash: SurfaceControl = mock<SurfaceControl>(),
        var taskInfo: RunningTaskInfo? = null,
        var activityTransitionInfo: ActivityTransitionInfo? = null
    )

    fun token(
        builder: WindowContainerTokenTestInputBuilder.() -> WindowContainerToken
    ): WindowContainerToken {
        val binderObj = WindowContainerTokenTestInputBuilder()
        return binderObj.builder().apply {
            inputParams.token = this
        }
    }

    fun leash(builder: SurfaceControlTestInputBuilder.() -> SurfaceControl): SurfaceControl {
        val binderObj = SurfaceControlTestInputBuilder()
        return binderObj.builder().apply {
            inputParams.leash = this
        }
    }

    fun runningTaskInfo(
        builder: RunningTaskInfoTestInputBuilder.(RunningTaskInfo) -> Unit
    ): RunningTaskInfo {
        val runningTaskInfoObj = RunningTaskInfoTestInputBuilder()
        return RunningTaskInfo().also {
            runningTaskInfoObj.builder(it)
        }.apply {
            inputParams.taskInfo = this
        }
    }

    fun activityTransitionInfo(
        builder: ActivityTransitionInfoTestInputBuilder.() -> Unit
    ): ActivityTransitionInfo {
        val binderObj = ActivityTransitionInfoTestInputBuilder()
        binderObj.builder()
        return binderObj.build().apply {
            inputParams.activityTransitionInfo = this
        }
    }

    override fun build(): Change {
        return Change(
            inputParams.token,
            inputParams.leash
        ).apply {
            mode = this@ChangeTestInputBuilder.mode
            taskInfo = inputParams.taskInfo
            this@ChangeTestInputBuilder.endAbsBounds?.let { bounds ->
                endAbsBounds.set(bounds)
            }
            this@ChangeTestInputBuilder.endRelOffset?.let { offset ->
                endRelOffset.set(offset)
            }
            activityTransitionInfo = inputParams.activityTransitionInfo
            flags = this@ChangeTestInputBuilder.flags
        }
    }
}

// [TestInputBuilder] for a [ActivityTransitionInfo]
class ActivityTransitionInfoTestInputBuilder : TestInputBuilder<ActivityTransitionInfo> {

    var componentName: ComponentName = ComponentName("", "")
    var taskId: Int = -1
    var appCompatTransitionInfo: AppCompatTransitionInfo? = null

    fun componentName(
        builder: ComponentNameTestInputBuilder.() -> Unit
    ): ComponentName {
        val binderObj = ComponentNameTestInputBuilder()
        binderObj.builder()
        return binderObj.build().apply {
            componentName = this
        }
    }

    fun appCompatTransitionInfo(
        builder: AppCompatTransitionInfoTestInputBuilder.() -> Unit
    ): AppCompatTransitionInfo {
        val binderObj = AppCompatTransitionInfoTestInputBuilder()
        binderObj.builder()
        return binderObj.build().apply {
            appCompatTransitionInfo = this
        }
    }

    override fun build(): ActivityTransitionInfo =
        ActivityTransitionInfo(componentName, taskId, appCompatTransitionInfo)
}

// [TestInputBuilder] for a [AppCompatTransitionInfo]
class AppCompatTransitionInfoTestInputBuilder : TestInputBuilder<AppCompatTransitionInfo> {

    var letterboxBounds: Rect = Rect()

    override fun build(): AppCompatTransitionInfo =
        AppCompatTransitionInfo(letterboxBounds)
}

// [TestInputBuilder] for a [ComponentName]
class ComponentNameTestInputBuilder : TestInputBuilder<ComponentName> {

    var packageName: String = ""
    var className: String = ""

    override fun build(): ComponentName = ComponentName(packageName, className)
}

// [TestInputBuilder] for a [TransitionInfo]
class TransitionInfoTestInputBuilder : TestInputBuilder<TransitionInfo> {

    @TransitionType
    var type: Int = TRANSIT_NONE

    @TransitionFlags
    var flags: Int = 0

    private val transitionInfoChanges = mutableListOf<Change>()

    fun addChange(builder: ChangeTestInputBuilder.() -> Unit): Change {
        val inputFactoryObj = ChangeTestInputBuilder()
        inputFactoryObj.builder()
        return inputFactoryObj.build().apply {
            transitionInfoChanges.add(this)
        }
    }

    override fun build(): TransitionInfo = TransitionInfo(type, flags)
        .apply {
            transitionInfoChanges.forEach { c -> addChange(c) }
        }
}

// [TestInputBuilder] for a [WindowContainerToken]
class WindowContainerTokenTestInputBuilder

// [TestInputBuilder] for a [SurfaceControl]
class SurfaceControlTestInputBuilder

// This should create the [RunningTaskInfo] to use in the test.
class RunningTaskInfoTestInputBuilder
