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
import android.app.WindowConfiguration
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.TransitionInfo
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.activityembedding.ActivityEmbeddingController
import com.android.wm.shell.bubbles.BubbleTransitions
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.keyguard.KeyguardTransitionHandler
import com.android.wm.shell.pip.PipTransitionController
import com.android.wm.shell.splitscreen.StageCoordinator
import com.android.wm.shell.unfold.UnfoldTransitionHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DefaultMixedTransition]
 * Build & Run: atest WMShellUnitTests:DefaultMixedTransitionTest
 */
@SmallTest
class DefaultMixedTransitionTest : ShellTestCase() {
    private val mPlayer = mock<Transitions>()
    private val mMixedHandler = mock<MixedTransitionHandler>()
    private val mPipHandler = mock<PipTransitionController>()
    private val mSplitHandler = mock<StageCoordinator>()
    private val mKeyguardHandler = mock<KeyguardTransitionHandler>()
    private val mUnfoldHandler = mock<UnfoldTransitionHandler>()
    private val mActivityEmbeddingController = mock<ActivityEmbeddingController>()
    private val mDesktopTasksController = mock<DesktopTasksController>()
    private val mBubbleTransitions = mock<BubbleTransitions>()
    private val mMockTransition = mock<IBinder>()

    // Mocks for startAnimation arguments, initialized inline
    private val mStartT = mock<SurfaceControl.Transaction>()
    private val mFinishT = mock<SurfaceControl.Transaction>()
    private val mFinishCallback = mock<Transitions.TransitionFinishCallback>()
    private val mockPipToken = mock<WindowContainerToken>()
    private val mockPipLeash = mock<SurfaceControl>()

    // Other mocks needed for the test logic, initialized inline
    private val mRemoteTransitionHandler = mock<RemoteTransitionHandler>()
    private val mRemoteChange = mock<TransitionInfo.Change>()

    @Before
    fun setUp() {
        whenever(mPlayer.remoteTransitionHandler).thenReturn(mRemoteTransitionHandler)
    }

    @EnableFlags(Flags.FLAG_ENABLE_PIP2)
    @Test
    fun remoteAndPipOrDesktop_remoteAndEnterPip_sendPipChangeToPipHandler() {
        // Mock dependencies to prevent side-effects and isolate logic for PiP and remote only.
        whenever(mSplitHandler.transitionImpliesSplitToFullscreen(any())).thenReturn(false)
        whenever(mDesktopTasksController.isDesktopChange(any(), any())).thenReturn(false)
        whenever(mPlayer.dispatchTransition(any(), any(), any(), any(), any(), any()))
            .thenReturn(mRemoteTransitionHandler)

        val defaultMixedTransition = getDefaultMixedTransition(
            DefaultMixedHandler.MixedTransition.TYPE_OPTIONS_REMOTE_AND_PIP_OR_DESKTOP_CHANGE)

        val info = createRemoteTransitionInfo()
        val pipChange = createPipChange()
        whenever(mPipHandler.isEnteringPip(eq(pipChange), eq(info.type))).thenReturn(true)
        info.addChange(pipChange)

        val handled = defaultMixedTransition.startAnimation(
            mMockTransition, info, mStartT, mFinishT, mFinishCallback)

        // Verify that animation was resolved.
        assertThat(handled).isTrue()

        // Check that PiP handler was called into for the animation with PiP change in its info.
        val infoCaptor = argumentCaptor<TransitionInfo>()
        verify(mPipHandler, times(1)).startAnimation(eq(mMockTransition),
            infoCaptor.capture(), any(), any(), any())

        // Assert that TransitionInfo was separated to contain PiP change only
        // and that transition type is carried over.
        val capturedInfo = infoCaptor.firstValue
        assertThat(capturedInfo.changes).containsExactly(pipChange)
        assertThat(capturedInfo.type).isEqualTo(info.type)
    }

    @EnableFlags(Flags.FLAG_ENABLE_PIP2)
    @Test
    fun remoteAndPipOrDesktop_remoteAndNonEnterPipChange_sendPipChangeToPipHandler() {
        // Mock dependencies to prevent side-effects and isolate logic for PiP and remote only.
        whenever(mSplitHandler.transitionImpliesSplitToFullscreen(any())).thenReturn(false)
        whenever(mDesktopTasksController.isDesktopChange(any(), any())).thenReturn(false)
        whenever(mPlayer.dispatchTransition(any(), any(), any(), any(), any(), any()))
            .thenReturn(mRemoteTransitionHandler)

        val defaultMixedTransition = getDefaultMixedTransition(
            DefaultMixedHandler.MixedTransition.TYPE_OPTIONS_REMOTE_AND_PIP_OR_DESKTOP_CHANGE)

        val info = createRemoteTransitionInfo()
        // Add a PiP change into the transition that is NOT entering PiP.
        val pipChange = createPipChange()
        whenever(mPipHandler.isEnteringPip(eq(pipChange), eq(info.type))).thenReturn(false)
        info.addChange(pipChange)

        val handled = defaultMixedTransition.startAnimation(
            mMockTransition, info, mStartT, mFinishT, mFinishCallback)

        // Verify that animation was resolved.
        assertThat(handled).isTrue()

        // Check that PiP handler was called into for the animation with PiP change in its info.
        val infoCaptor = argumentCaptor<TransitionInfo>()
        verify(mPipHandler, times(1)).startAnimation(eq(mMockTransition),
            infoCaptor.capture(), any(), any(), any())

        // Assert that TransitionInfo was separated to contain PiP change only
        // and that transition type is carried over; this should be done even with non-enter PiP
        // changes.
        val capturedInfo = infoCaptor.firstValue
        assertThat(capturedInfo.changes).containsExactly(pipChange)
        assertThat(capturedInfo.type).isEqualTo(info.type)
    }

    private fun createRemoteTransitionInfo(): TransitionInfo {
        val info = TransitionInfo(WindowManager.TRANSIT_TO_FRONT, 0 /* flags */)
        info.addChange(mRemoteChange)
        return info
    }

    private fun createPipChange(): TransitionInfo.Change {
        val pipChange = TransitionInfo.Change(mockPipToken, mockPipLeash)
        val pipTaskInfo = ActivityManager.RunningTaskInfo()
        pipTaskInfo.configuration.windowConfiguration.windowingMode =
            WindowConfiguration.WINDOWING_MODE_PINNED
        pipChange.taskInfo = pipTaskInfo
        return pipChange
    }

    private fun getDefaultMixedTransition(type: Int): DefaultMixedTransition {
        return DefaultMixedTransition(
            type,
            mMockTransition,
            mPlayer,
            mMixedHandler,
            mPipHandler,
            mSplitHandler,
            mKeyguardHandler,
            mUnfoldHandler,
            mActivityEmbeddingController,
            mDesktopTasksController,
            mBubbleTransitions
        )
    }
}