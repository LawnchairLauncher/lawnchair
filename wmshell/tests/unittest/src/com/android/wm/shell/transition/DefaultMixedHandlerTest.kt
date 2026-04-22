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

import android.app.ActivityManager.RunningTaskInfo
import android.os.Binder
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager.TRANSIT_OPEN
import android.window.IRemoteTransition
import android.window.RemoteTransition
import android.window.TransitionRequestInfo
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.activityembedding.ActivityEmbeddingController
import com.android.wm.shell.bubbles.BubbleTransitions
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.keyguard.KeyguardTransitionHandler
import com.android.wm.shell.pip.PipTransitionController
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.splitscreen.StageCoordinator
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.unfold.UnfoldTransitionHandler
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/**
 * Tests for [DefaultMixedHandler]
 * Build & Run: atest WMShellUnitTests:DefaultMixedHandlerTest
 */
@SmallTest
class DefaultMixedHandlerTest : ShellTestCase() {

    private val transitions = mock<Transitions>()
    private val splitScreenController = mock<SplitScreenController> {
        on { transitionHandler } doReturn mock<StageCoordinator>()
    }
    private val pipTransitionController = mock<PipTransitionController>()
    private val recentsTransitionHandler = mock<RecentsTransitionHandler>()
    private val keyguardTransitionHandler = mock<KeyguardTransitionHandler>()
    private val desktopTasksController = mock<DesktopTasksController>()
    private val unfoldTransitionHandler = mock<UnfoldTransitionHandler>()
    private val activityEmbeddingController = mock<ActivityEmbeddingController>()
    private val bubbleTransitions = mock<BubbleTransitions>()

    private val shellInit: ShellInit = ShellInit(TestShellExecutor())
    private val mixedHandler = DefaultMixedHandler(
        shellInit,
        transitions,
        Optional.of(splitScreenController),
        pipTransitionController,
        Optional.of(recentsTransitionHandler),
        keyguardTransitionHandler,
        Optional.of(desktopTasksController),
        Optional.of(unfoldTransitionHandler),
        Optional.of(activityEmbeddingController),
        bubbleTransitions,
    )

    @Before
    fun setUp() {
        shellInit.init()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnter_noTriggerTask_notHandled() {
        val noTriggerTaskRequest = createTransitionRequestInfo()

        assertThat(mixedHandler.requestHasBubbleEnter(noTriggerTaskRequest)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnter_noPendingEnterTransition_notHandled() {
        val runningTask = createRunningTask()
        val request = createTransitionRequestInfo(runningTask)

        bubbleTransitions.stub {
            on { hasPendingEnterTransition(request) } doReturn false
        }

        assertThat(mixedHandler.requestHasBubbleEnter(request)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnter_notShowingAsBubbleBar() {
        val runningTask = createRunningTask()
        val request = createTransitionRequestInfo(runningTask)

        bubbleTransitions.stub {
            on { hasPendingEnterTransition(request) } doReturn true
            on { isShowingAsBubbleBar } doReturn false
        }

        assertThat(mixedHandler.requestHasBubbleEnter(request)).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnter() {
        val runningTask = createRunningTask()
        val request = createTransitionRequestInfo(runningTask)

        bubbleTransitions.stub {
            on { hasPendingEnterTransition(request) } doReturn true
            on { isShowingAsBubbleBar } doReturn true
        }

        assertThat(mixedHandler.requestHasBubbleEnter(request)).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestBubbleEnterConsumesRemote() {
        val runningTask = createRunningTask()
        val remoteTransition = mock<IRemoteTransition>()
        val request = createTransitionRequestInfo(runningTask, RemoteTransition(remoteTransition))

        bubbleTransitions.stub {
            on { hasPendingEnterTransition(request) } doReturn true
            on { isShowingAsBubbleBar } doReturn true
        }

        mixedHandler.handleRequest(Binder(), request)
        verify(remoteTransition).onTransitionConsumed(any(), eq(false))
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnterFromAppBubbleOrExistingBubble_noTriggerTask_notHandled() {
        val noTriggerTaskRequest = createTransitionRequestInfo()

        assertThat(
            mixedHandler.requestHasBubbleEnterFromAppBubbleOrExistingBubble(noTriggerTaskRequest)
        ).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnterFromAppBubbleOrExistingBubble_notAppBubble_notHandled() {
        val runningTask = createRunningTask(100)
        val request = createTransitionRequestInfo(runningTask)

        assertThat(mixedHandler.requestHasBubbleEnterFromAppBubbleOrExistingBubble(request))
            .isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnterFromAppBubbleOrExistingBubble_notShowingAsBubbleBar() {
        val runningTask = createRunningTask(100)
        val request = createTransitionRequestInfo(runningTask)

        bubbleTransitions.stub {
            on { isShowingAsBubbleBar } doReturn false
            on { shouldBeAppBubble(runningTask) } doReturn true
        }

        assertThat(mixedHandler.requestHasBubbleEnterFromAppBubbleOrExistingBubble(request))
            .isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnterFromAppBubbleOrExistingBubble() {
        val runningTask = createRunningTask(100)
        val request = createTransitionRequestInfo(runningTask)

        bubbleTransitions.stub {
            on { isShowingAsBubbleBar } doReturn true
            on { shouldBeAppBubble(runningTask) } doReturn true
        }

        assertThat(mixedHandler.requestHasBubbleEnterFromAppBubbleOrExistingBubble(request))
            .isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_handleRequest_bubbleEnterFromAppBubble_consumesRemote() {
        val runningTask = createRunningTask(100)
        val remoteTransition = mock<IRemoteTransition>()
        val request = createTransitionRequestInfo(runningTask, RemoteTransition(remoteTransition))

        bubbleTransitions.stub {
            on { isShowingAsBubbleBar } doReturn true
            on { shouldBeAppBubble(runningTask) } doReturn true
        }

        mixedHandler.handleRequest(Binder(), request)

        verify(remoteTransition).onTransitionConsumed(any(), eq(false))
    }

    private fun createTransitionRequestInfo(
        runningTask: RunningTaskInfo? = null,
        remote: RemoteTransition? = null,
    ): TransitionRequestInfo {
        return TransitionRequestInfo(TRANSIT_OPEN, runningTask, remote)
    }

    private fun createRunningTask(taskId: Int = 0): RunningTaskInfo {
        return RunningTaskInfo().apply {
            this.taskId = taskId
        }
    }
}
