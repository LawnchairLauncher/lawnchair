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

import android.graphics.Point
import android.graphics.Rect
import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import com.android.wm.shell.transition.Transitions
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test class for {@link DragToDisplayTransitionHandler}
 *
 * Usage: atest WMShellUnitTests:DragToDisplayTransitionHandlerTest
 */
class DragToDisplayTransitionHandlerTest {
    private lateinit var handler: DragToDisplayTransitionHandler
    private val mockTransition: IBinder = mock()
    private val mockRequestInfo: TransitionRequestInfo = mock()
    private val mockTransitionInfo: TransitionInfo = mock()
    private val mockStartTransaction: SurfaceControl.Transaction = mock()
    private val mockFinishTransaction: SurfaceControl.Transaction = mock()
    private val mockFinishCallback: Transitions.TransitionFinishCallback = mock()

    @Before
    fun setUp() {
        handler = DragToDisplayTransitionHandler()
        whenever(mockStartTransaction.setWindowCrop(any(), any(), any()))
            .thenReturn(mockStartTransaction)
        whenever(mockFinishTransaction.setWindowCrop(any(), any(), any()))
            .thenReturn(mockFinishTransaction)
    }

    @Test
    fun handleRequest_anyRequest_returnsNull() {
        val result = handler.handleRequest(mockTransition, mockRequestInfo)
        assert(result == null)
    }

    @Test
    fun startAnimation_verifyTransformationsApplied() {
        val mockChange1 = mock<TransitionInfo.Change>()
        val leash1 = mock<SurfaceControl>()
        val endBounds1 = Rect(0, 0, 50, 50)
        val endPosition1 = Point(5, 5)

        whenever(mockChange1.leash).doReturn(leash1)
        whenever(mockChange1.endAbsBounds).doReturn(endBounds1)
        whenever(mockChange1.endRelOffset).doReturn(endPosition1)

        val mockChange2 = mock<TransitionInfo.Change>()
        val leash2 = mock<SurfaceControl>()
        val endBounds2 = Rect(100, 100, 200, 150)
        val endPosition2 = Point(15, 25)

        whenever(mockChange2.leash).doReturn(leash2)
        whenever(mockChange2.endAbsBounds).doReturn(endBounds2)
        whenever(mockChange2.endRelOffset).doReturn(endPosition2)

        whenever(mockTransitionInfo.changes).doReturn(listOf(mockChange1, mockChange2))

        handler.startAnimation(
            mockTransition,
            mockTransitionInfo,
            mockStartTransaction,
            mockFinishTransaction,
            mockFinishCallback,
        )

        verify(mockStartTransaction).setWindowCrop(leash1, endBounds1.width(), endBounds1.height())
        verify(mockStartTransaction)
            .setPosition(leash1, endPosition1.x.toFloat(), endPosition1.y.toFloat())
        verify(mockStartTransaction).setWindowCrop(leash2, endBounds2.width(), endBounds2.height())
        verify(mockStartTransaction)
            .setPosition(leash2, endPosition2.x.toFloat(), endPosition2.y.toFloat())
        verify(mockStartTransaction).apply()
        verify(mockFinishCallback).onTransitionFinished(null)
    }
}
