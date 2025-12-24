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

package com.android.wm.shell.bubbles.util

import android.graphics.Rect
import android.os.Binder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags2.Flags.FLAG_ROOT_TASK_FOR_BUBBLE
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyEnterBubbleTransaction
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyExitBubbleTransaction
import com.android.wm.shell.bubbles.util.BubbleUtils.getEnterBubbleTransaction
import com.android.wm.shell.bubbles.util.BubbleUtils.getExitBubbleTransaction
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Unit tests for [BubbleUtils].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:BubbleUtilsTest
 */
@SmallTest
@RunWith(TestParameterInjector::class)
class BubbleUtilsTest : ShellTestCase() {

    private val binder = Binder()
    private val token = mock<WindowContainerToken> {
        on { asBinder() } doReturn binder
    }
    private val rootTaskToken = mock<WindowContainerToken> {
        on { asBinder() } doReturn binder
    }
    private val bounds = Rect(0, 0, 100, 100)
    private val captionInsetsOwner = Binder()

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @DisableFlags(FLAG_ROOT_TASK_FOR_BUBBLE)
    @Test
    fun testGetEnterBubbleTransaction(@TestParameter isAppBubble: Boolean) {
        val wctWithLaunchNextToBubble =
            getEnterBubbleTransaction(token, null /* rootToken */, bounds, isAppBubble)

        verifyEnterBubbleTransaction(wctWithLaunchNextToBubble, token.asBinder(), isAppBubble)
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @DisableFlags(FLAG_ROOT_TASK_FOR_BUBBLE)
    @Test
    fun testGetEnterBubbleTransaction_reparentToTda(@TestParameter isAppBubble: Boolean) {
        val wctWithLaunchNextToBubble =
            getEnterBubbleTransaction(
                token,
                rootToken = null,
                bounds,
                isAppBubble,
                reparentToTda = true
            )

        verifyEnterBubbleTransaction(
            wctWithLaunchNextToBubble,
            token.asBinder(),
            isAppBubble,
            reparentToTda = true,
        )
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ROOT_TASK_FOR_BUBBLE)
    @Test
    fun testRootBubbleGetEnterBubbleTransaction() {
        val isAppBubble = true
        val wct = getEnterBubbleTransaction(token, rootTaskToken, bounds, isAppBubble)

        verifyEnterBubbleTransaction(
            wct,
            token.asBinder(),
            isAppBubble,
            rootTaskToken = rootTaskToken.asBinder()
        )
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun testGetExitBubbleTransaction() {
        val wct = getExitBubbleTransaction(token, captionInsetsOwner)

        verifyExitBubbleTransaction(wct, token.asBinder(), captionInsetsOwner)
    }
}
