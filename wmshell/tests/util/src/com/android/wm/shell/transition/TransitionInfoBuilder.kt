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

package com.android.wm.shell.transition

import android.app.ActivityManager
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.content.ComponentName
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.ActivityTransitionInfo
import android.window.TransitionInfo
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Utility for creating/editing synthetic [TransitionInfo] for tests.
 *
 * @param type the type of the transition. See [WindowManager.TransitionType].
 * @param flags the flags for the transition. See [WindowManager.TransitionFlags].
 * @param asNoOp if true, the root leash will not be added.
 * @param displayId the display ID for the root leash and transition changes.
 */
class TransitionInfoBuilder @JvmOverloads constructor(
    @WindowManager.TransitionType type: Int,
    @WindowManager.TransitionFlags flags: Int = 0,
    asNoOp: Boolean = false,
    private val displayId: Int = DEFAULT_DISPLAY_ID,
) {
    // The underlying TransitionInfo object being built.
    private val info: TransitionInfo = TransitionInfo(type, flags).apply {
        if (asNoOp) {
            return@apply
        }
        // Add a root leash by default, unless asNoOp is true.
        addRootLeash(
            displayId,
            createMockSurface(), /* leash */
            0, /* offsetLeft */
            0, /* offsetTop */
        )
    }

    /** Adds a change to the [TransitionInfo]. */
    @JvmOverloads
    fun addChange(
        @WindowManager.TransitionType mode: Int,
        @TransitionInfo.ChangeFlags flags: Int = TransitionInfo.FLAG_NONE,
    ) = addChange(mode, flags, activityTransitionInfo = null, taskInfo = null)

    /** Adds a change to the [TransitionInfo] for task transition with [flags]. */
    fun addChange(
        @WindowManager.TransitionType mode: Int,
        @TransitionInfo.ChangeFlags flags: Int,
        taskInfo: ActivityManager.RunningTaskInfo?,
    ) = addChange(mode, flags, activityTransitionInfo = null, taskInfo = taskInfo)

    /** Adds a change to the [TransitionInfo] for task transition. */
    fun addChange(
        @WindowManager.TransitionType mode: Int,
        taskInfo: ActivityManager.RunningTaskInfo?,
    ) = addChange(mode, activityTransitionInfo = null, taskInfo = taskInfo)

    /** Adds a change to the [TransitionInfo] for activity transition. */
    fun addChange(
        @WindowManager.TransitionType mode: Int,
        activityTransitionInfo: ActivityTransitionInfo?,
    ) = addChange(mode, activityTransitionInfo = activityTransitionInfo, taskInfo = null)

    /** Adds a change to the [TransitionInfo] for activity transition without task id. */
    fun addChange(@WindowManager.TransitionType mode: Int, activityComponent: ComponentName?) =
        addChange(mode, activityTransitionInfo = activityComponent?.let { component ->
            ActivityTransitionInfo(component, INVALID_TASK_ID)
        })

    /** Add a change to the [TransitionInfo] for task fragment. */
    fun addChange(@WindowManager.TransitionType mode: Int, taskFragmentToken: IBinder?) =
        addChange(
            mode,
            activityTransitionInfo = null,
            taskInfo = null,
            taskFragmentToken = taskFragmentToken,
        )

    /**
     * Adds a change to the [TransitionInfo].
     *
     * @param mode the mode of the change. See [WindowManager.TransitionType].
     * @param flags the flags for this change. See [TransitionInfo.ChangeFlags].
     * @param activityTransitionInfo the activity transition info associated with this change for
     *        activity transition.
     * @param taskInfo the task info associated with this change for task transition.
     * @param taskFragmentToken the task fragment token associated with this change.
     * @return this [TransitionInfoBuilder] instance for chaining.
     */
    private fun addChange(
        @WindowManager.TransitionType mode: Int,
        @TransitionInfo.ChangeFlags flags: Int = TransitionInfo.FLAG_NONE,
        activityTransitionInfo: ActivityTransitionInfo? = null,
        taskInfo: ActivityManager.RunningTaskInfo? = null,
        taskFragmentToken: IBinder? = null,
    ): TransitionInfoBuilder {
        val container = taskInfo?.token
        val leash = createMockSurface()
        val change = TransitionInfo.Change(container, leash).apply {
            setMode(mode)
            setFlags(flags)
            setActivityTransitionInfo(activityTransitionInfo)
            setTaskInfo(taskInfo)
            setTaskFragmentToken(taskFragmentToken)
        }
        return addChange(change)
    }

    /**
     * Adds a pre-configured change to the [TransitionInfo].
     *
     * @param change the TransitionInfo.Change object to add.
     * @return this TransitionInfoBuilder instance for chaining.
     */
    fun addChange(change: TransitionInfo.Change): TransitionInfoBuilder {
        // Set the display ID for the change.
        change.setDisplayId(displayId /* start */, displayId /* end */)
        // Add the change to the internal TransitionInfo object.
        info.addChange(change)
        return this // Return this for fluent builder pattern.
    }

    /**
     * Builds and returns the configured [TransitionInfo] object.
     *
     * @return the constructed [TransitionInfo].
     */
    fun build(): TransitionInfo {
        return info
    }

    companion object {
        // Default display ID for root leashes and changes.
        const val DEFAULT_DISPLAY_ID = 0

        // Create a mock SurfaceControl for testing.
        private fun createMockSurface() = mock<SurfaceControl> {
            on { isValid } doReturn true
            on { toString() } doReturn "TestSurface"
        }
    }
}
