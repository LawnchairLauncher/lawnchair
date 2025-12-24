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

import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Tests for [NoOpTransitionHandler]
 *
 * Build/Install/Run:
 *   atest WMShellUnitTests:NoOpTransitionHandlerTest
 */
class NoOpTransitionHandlerTest : ShellTestCase() {

    @Test
    fun handleRequest_alwaysReturnsWct() {
        assertThat(NoOpTransitionHandler().handleRequest(mock(), mock())).isNotNull()
    }

    @Test
    fun startAnimation_callsFinishCallbackImmediately() {
        val finishCalled = arrayOf(false)
        val finishCb = Transitions.TransitionFinishCallback { finishCalled[0] = true }
        NoOpTransitionHandler().startAnimation(mock(), mock(), mock(), mock(), finishCb)
        assertThat(finishCalled[0]).isTrue()
    }
}
