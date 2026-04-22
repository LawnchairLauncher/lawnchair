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

package com.android.wm.shell.desktopmode

import android.os.Binder
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidTestingRunner::class)
class OverviewToDesktopTransitionObserverTest : ShellTestCase() {

    @Mock private lateinit var transitions: Transitions

    @Mock private lateinit var moveToDesktopCallback: IMoveToDesktopCallback

    private val testExecutor = mock<ShellExecutor>()
    private lateinit var shellInit: ShellInit
    private lateinit var transitionObserver: OverviewToDesktopTransitionObserver
    private val token = Binder()

    @Before
    fun setup() {
        shellInit = spy(ShellInit(testExecutor))
        transitionObserver = OverviewToDesktopTransitionObserver(transitions, shellInit)
    }

    @Test
    fun moveToDesktop_onTransitionEnd_invokesCallback() {
        transitionObserver.addPendingOverviewTransition(token, moveToDesktopCallback)

        transitionObserver.onTransitionFinished(token, false)

        verify(moveToDesktopCallback).onTaskMovedToDesktop()
    }
}
