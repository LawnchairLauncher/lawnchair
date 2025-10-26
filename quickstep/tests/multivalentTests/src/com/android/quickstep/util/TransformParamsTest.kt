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

package com.android.quickstep.util

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionInfo.FLAG_NONE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.quickstep.RemoteAnimationTargets
import com.android.quickstep.util.TransformParams.BuilderProxy.NO_OP
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class TransformParamsTest {
    private val surfaceTransaction = mock<SurfaceTransaction>()
    private val transaction = mock<SurfaceControl.Transaction>()
    private val transformParams = TransformParams(::surfaceTransaction)

    private val freeformTaskInfo1 =
        createTaskInfo(taskId = 1, windowingMode = WINDOWING_MODE_FREEFORM)
    private val freeformTaskInfo2 =
        createTaskInfo(taskId = 2, windowingMode = WINDOWING_MODE_FREEFORM)
    private val fullscreenTaskInfo1 =
        createTaskInfo(taskId = 1, windowingMode = WINDOWING_MODE_FULLSCREEN)

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @Before
    fun setUp() {
        whenever(surfaceTransaction.transaction).thenReturn(transaction)
        whenever(surfaceTransaction.forSurface(anyOrNull()))
            .thenReturn(mock<SurfaceTransaction.SurfaceProperties>())
        transformParams.setCornerRadius(CORNER_RADIUS)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    fun createSurfaceParams_freeformTasks_overridesCornerRadius() {
        val transitionInfo = TransitionInfo(TRANSIT_OPEN, FLAG_NONE)
        val leash1 = mock<SurfaceControl>()
        val leash2 = mock<SurfaceControl>()
        transitionInfo.addChange(createChange(freeformTaskInfo1, leash = leash1))
        transitionInfo.addChange(createChange(freeformTaskInfo2, leash = leash2))
        transformParams.setTransitionInfo(transitionInfo)
        transformParams.setTargetSet(createTargetSet(listOf(freeformTaskInfo1, freeformTaskInfo2)))

        transformParams.createSurfaceParams(NO_OP)

        verify(transaction).setCornerRadius(leash1, 0f)
        verify(transaction).setCornerRadius(leash2, 0f)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    fun createSurfaceParams_freeformTasks_overridesCornerRadiusOnlyOnce() {
        val transitionInfo = TransitionInfo(TRANSIT_OPEN, FLAG_NONE)
        val leash1 = mock<SurfaceControl>()
        val leash2 = mock<SurfaceControl>()
        transitionInfo.addChange(createChange(freeformTaskInfo1, leash = leash1))
        transitionInfo.addChange(createChange(freeformTaskInfo2, leash = leash2))
        transformParams.setTransitionInfo(transitionInfo)
        transformParams.setTargetSet(createTargetSet(listOf(freeformTaskInfo1, freeformTaskInfo2)))
        transformParams.createSurfaceParams(NO_OP)

        transformParams.createSurfaceParams(NO_OP)

        verify(transaction).setCornerRadius(leash1, 0f)
        verify(transaction).setCornerRadius(leash2, 0f)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    fun createSurfaceParams_flagDisabled_doesntOverrideCornerRadius() {
        val transitionInfo = TransitionInfo(TRANSIT_OPEN, FLAG_NONE)
        val leash1 = mock<SurfaceControl>()
        val leash2 = mock<SurfaceControl>()
        transitionInfo.addChange(createChange(freeformTaskInfo1, leash = leash1))
        transitionInfo.addChange(createChange(freeformTaskInfo2, leash = leash2))
        transformParams.setTransitionInfo(transitionInfo)
        transformParams.setTargetSet(createTargetSet(listOf(freeformTaskInfo1, freeformTaskInfo2)))

        transformParams.createSurfaceParams(NO_OP)

        verify(transaction, never()).setCornerRadius(leash1, 0f)
        verify(transaction, never()).setCornerRadius(leash2, 0f)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    fun createSurfaceParams_fullscreenTasks_doesntOverrideCornerRadius() {
        val transitionInfo = TransitionInfo(TRANSIT_OPEN, FLAG_NONE)
        val leash = mock<SurfaceControl>()
        transitionInfo.addChange(createChange(fullscreenTaskInfo1, leash = leash))
        transformParams.setTransitionInfo(transitionInfo)
        transformParams.setTargetSet(createTargetSet(listOf(fullscreenTaskInfo1)))

        transformParams.createSurfaceParams(NO_OP)

        verify(transaction, never()).setCornerRadius(leash, 0f)
    }

    private fun createTargetSet(taskInfos: List<RunningTaskInfo>): RemoteAnimationTargets {
        val remoteAnimationTargets = mutableListOf<RemoteAnimationTarget>()
        taskInfos.map { remoteAnimationTargets.add(createRemoteAnimationTarget(it)) }
        return RemoteAnimationTargets(
            remoteAnimationTargets.toTypedArray(),
            /* wallpapers= */ null,
            /* nonApps= */ null,
            /* targetMode= */ TRANSIT_OPEN,
        )
    }

    private fun createRemoteAnimationTarget(taskInfo: RunningTaskInfo): RemoteAnimationTarget {
        val windowConfig = mock<WindowConfiguration>()
        whenever(windowConfig.activityType).thenReturn(ACTIVITY_TYPE_STANDARD)
        return RemoteAnimationTarget(
            taskInfo.taskId,
            /* mode= */ TRANSIT_OPEN,
            /* leash= */ null,
            /* isTranslucent= */ false,
            /* clipRect= */ null,
            /* contentInsets= */ null,
            /* prefixOrderIndex= */ 0,
            /* position= */ null,
            /* localBounds= */ null,
            /* screenSpaceBounds= */ null,
            windowConfig,
            /* isNotInRecents= */ false,
            /* startLeash= */ null,
            /* startBounds= */ null,
            taskInfo,
            /* allowEnterPip= */ false,
        )
    }

    private fun createTaskInfo(taskId: Int, windowingMode: Int): RunningTaskInfo {
        val taskInfo = RunningTaskInfo()
        taskInfo.taskId = taskId
        taskInfo.configuration.windowConfiguration.windowingMode = windowingMode
        return taskInfo
    }

    private fun createChange(taskInfo: RunningTaskInfo, leash: SurfaceControl): Change {
        val taskInfo = createTaskInfo(taskInfo.taskId, taskInfo.windowingMode)
        val change = Change(taskInfo.token, mock<SurfaceControl>())
        change.mode = TRANSIT_OPEN
        change.taskInfo = taskInfo
        change.leash = leash
        return change
    }

    private companion object {
        private const val CORNER_RADIUS = 30f
    }
}
