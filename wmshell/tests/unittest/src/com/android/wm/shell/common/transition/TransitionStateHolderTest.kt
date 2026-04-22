/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.common.transition

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.recents.RecentsTransitionStateListener
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_ANIMATING
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED
import com.android.wm.shell.sysui.ShellInit
import kotlin.test.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.never

/**
 * Test class for {@link TransitionStateHolder}
 *
 * Usage: atest WMShellUnitTests:TransitionStateHolderTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class TransitionStateHolderTest {

    lateinit var recentTransitionHandler: RecentsTransitionHandler
    lateinit var shellInit: ShellInit

    @Before
    fun before() {
        recentTransitionHandler = mock(RecentsTransitionHandler::class.java)
        shellInit = ShellInit(TestShellExecutor())
    }

    @Test
    fun `No TransitionStateHolder listeners before initialization`() {
        TransitionStateHolder(shellInit, recentTransitionHandler)
        verify(recentTransitionHandler, never()).addTransitionStateListener(any())
    }

    @Test
    fun `When TransitionStateHolder initialized a listener has been registered `() {
        TransitionStateHolder(shellInit, recentTransitionHandler)
        shellInit.init()
        assertNotNull(recentsTransitionStateListener)
    }

    @Test
    fun `When TransitionStateHolder is created  no recent animation running`() {
        val holder = TransitionStateHolder(shellInit, recentTransitionHandler)
        shellInit.init()
        assertFalse(holder.isRecentsTransitionRunning())
    }

    @Test
    fun `Recent animation running updates after callback value`() {
        val holder = TransitionStateHolder(shellInit, recentTransitionHandler)
        shellInit.init()

        assertFalse(holder.isRecentsTransitionRunning())

        recentsTransitionStateListener.onTransitionStateChanged(TRANSITION_STATE_NOT_RUNNING)
        assertFalse(holder.isRecentsTransitionRunning())

        recentsTransitionStateListener.onTransitionStateChanged(TRANSITION_STATE_REQUESTED)
        assertTrue(holder.isRecentsTransitionRunning())

        recentsTransitionStateListener.onTransitionStateChanged(TRANSITION_STATE_ANIMATING)
        assertTrue(holder.isRecentsTransitionRunning())
    }

    private val recentsTransitionStateListener: RecentsTransitionStateListener
        get() = ArgumentCaptor.forClass(RecentsTransitionStateListener::class.java).run {
            verify(recentTransitionHandler).addTransitionStateListener(capture())
            value
        }
}
