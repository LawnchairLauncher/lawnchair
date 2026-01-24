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

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.WindowManager
import android.window.RemoteTransition
import android.window.TransitionFilter
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestSyncExecutor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Test class for [RemoteTransitionHandler].
 *
 * atest WMShellUnitTests:RemoteTransitionHandlerTest
 */
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class RemoteTransitionHandlerTest : ShellTestCase() {

    private val testExecutor: TestSyncExecutor = TestSyncExecutor()

    private val testRemoteTransition = RemoteTransition(TestRemoteTransition())
    private lateinit var handler: RemoteTransitionHandler

    @Before
    fun setUp() {
        handler = RemoteTransitionHandler(testExecutor)
    }

    @Test
    fun handleRequest_noRemoteTransition_returnsNull() {
        val request = TransitionRequestInfo(WindowManager.TRANSIT_OPEN, null, null)

        assertNull(handler.handleRequest(mock(), request))
    }

    @Test
    fun handleRequest_testRemoteTransition_returnsWindowContainerTransaction() {
        val request = TransitionRequestInfo(WindowManager.TRANSIT_OPEN, null, testRemoteTransition)

        assertTrue(handler.handleRequest(mock(), request) is WindowContainerTransaction)
    }

    @Test
    fun startAnimation_noRemoteTransition_returnsFalse() {
        val request = TransitionRequestInfo(WindowManager.TRANSIT_OPEN, null, null)
        handler.handleRequest(mock(), request)

        val isHandled = handler.startAnimation(
            /* transition= */ mock(),
            /* info= */ createTransitionInfo(),
            /* startTransaction= */ mock(),
            /* finishTransaction= */ mock(),
            /* finishCallback= */ {},
        )

        assertFalse(isHandled)
    }

    @Test
    fun startAnimation_remoteTransition_returnsTrue() {
        val request = TransitionRequestInfo(WindowManager.TRANSIT_OPEN, null, testRemoteTransition)
        handler.addFiltered(TransitionFilter(), testRemoteTransition)
        handler.handleRequest(mock(), request)

        val isHandled = handler.startAnimation(
            /* transition= */ testRemoteTransition.remoteTransition.asBinder(),
            /* info= */ createTransitionInfo(),
            /* startTransaction= */ mock(),
            /* finishTransaction= */ mock(),
            /* finishCallback= */ {},
        )

        assertTrue(isHandled)
    }

    private fun createTransitionInfo(
        type: Int = WindowManager.TRANSIT_OPEN,
        changeMode: Int = WindowManager.TRANSIT_CLOSE,
    ): TransitionInfo =
        TransitionInfo(type, /* flags= */ 0).apply {
            addChange(
                TransitionInfo.Change(mock(), mock()).apply {
                    mode = changeMode
                    parent = null
                }
            )
        }
}