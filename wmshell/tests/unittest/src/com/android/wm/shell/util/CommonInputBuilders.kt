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
import android.graphics.Point
import android.graphics.Rect
import android.view.SurfaceControl
import android.window.ActivityTransitionInfo
import android.window.TransitionInfo.Change
import android.window.WindowContainerToken
import org.mockito.kotlin.mock

/**
 * Abstracts any object responsible to create an input builder.
 */
interface TestInputBuilder<T> {
    fun build(): T
}

/**
 * [InputBuilder] that helps in the creation of a [Change] object for testing.
 */
class ChangeTestInputBuilder : TestInputBuilder<Change> {

    // TODO(b/419766870): Implement TestInputBuilder for main objects in input on tests.
    private val inputParams = InputParams()
    var endAbsBounds: Rect? = null
    var endRelOffset: Point? = null
    var activityTransitionInfo: ActivityTransitionInfo? = null

    data class InputParams(
        var token: WindowContainerToken = mock<WindowContainerToken>(),
        var leash: SurfaceControl = mock<SurfaceControl>(),
        var taskInfo: RunningTaskInfo? = null
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

    override fun build(): Change {
        return Change(
            inputParams.token,
            inputParams.leash
        ).apply {
            taskInfo = inputParams.taskInfo
            this@ChangeTestInputBuilder.endAbsBounds?.let {
                this@apply.endAbsBounds.set(endAbsBounds)
            }
            this@ChangeTestInputBuilder.endRelOffset?.let {
                this@apply.endRelOffset.set(endRelOffset)
            }
            activityTransitionInfo = this@ChangeTestInputBuilder.activityTransitionInfo
        }
    }
}

// [TestInputBuilder] for a [WindowContainerToken]
class WindowContainerTokenTestInputBuilder

// [TestInputBuilder] for a [SurfaceControl]
class SurfaceControlTestInputBuilder

// This should create the [RunningTaskInfo] to use in the test.
class RunningTaskInfoTestInputBuilder
