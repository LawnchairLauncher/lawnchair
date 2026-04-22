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

package com.android.wm.shell.compatui.letterbox

import android.graphics.Rect
import android.view.SurfaceControl
import android.window.WindowContainerToken
import com.android.wm.shell.compatui.letterbox.LetterboxMatchers.asAnyMode
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Robot to test [LetterboxController] implementations.
 */
abstract class LetterboxControllerRobotTest {

    companion object {
        @JvmStatic
        val DISPLAY_ID = 1

        @JvmStatic
        val TASK_ID = 20

        @JvmStatic
        val ANOTHER_TASK_ID = 10

        @JvmStatic
        private val TOKEN = mock<WindowContainerToken>()
    }

    lateinit var letterboxController: LetterboxController
    val transaction: SurfaceControl.Transaction
    val parentLeash: SurfaceControl

    init {
        transaction = getTransactionMock()
        parentLeash = mock<SurfaceControl>()
    }

    fun initController() {
        letterboxController = buildController()
    }

    abstract fun buildController(): LetterboxController

    fun sendCreateSurfaceRequest(
        displayId: Int = DISPLAY_ID,
        taskId: Int = TASK_ID,
        token: WindowContainerToken? = TOKEN
    ) = letterboxController.createLetterboxSurface(
        key = LetterboxKey(displayId, taskId),
        transaction = transaction,
        parentLeash = parentLeash,
        token = token
    )

    fun sendDestroySurfaceRequest(
        displayId: Int = DISPLAY_ID,
        taskId: Int = TASK_ID
    ) = letterboxController.destroyLetterboxSurface(
        key = LetterboxKey(displayId, taskId),
        transaction = transaction
    )

    fun sendUpdateSurfaceVisibilityRequest(
        displayId: Int = DISPLAY_ID,
        taskId: Int = TASK_ID,
        visible: Boolean
    ) = letterboxController.updateLetterboxSurfaceVisibility(
        key = LetterboxKey(displayId, taskId),
        transaction = transaction,
        visible = visible
    )

    fun sendUpdateSurfaceBoundsRequest(
        displayId: Int = DISPLAY_ID,
        taskId: Int = TASK_ID,
        taskBounds: Rect,
        activityBounds: Rect
    ) = letterboxController.updateLetterboxSurfaceBounds(
        key = LetterboxKey(displayId, taskId),
        transaction = transaction,
        taskBounds = taskBounds,
        activityBounds = activityBounds
    )

    fun invokeDump() {
        letterboxController.dump()
    }

    fun checkTransactionRemovedInvoked(times: Int = 1) {
        verify(transaction, times(times)).remove(any())
    }

    fun checkVisibilityUpdated(times: Int = 1, expectedVisibility: Boolean) {
        verify(transaction, times(times)).setVisibility(any(), eq(expectedVisibility))
    }

    fun checkSurfacePositionUpdated(
        times: Int = 1,
        expectedX: Float = -1f,
        expectedY: Float = -1f
    ) {
        verify(transaction, times(times)).setPosition(
            any(),
            expectedX.asAnyMode(),
            expectedY.asAnyMode()
        )
    }

    fun checkSurfaceSizeUpdated(times: Int = 1, expectedWidth: Int = -1, expectedHeight: Int = -1) {
        verify(transaction, times(times)).setWindowCrop(
            any(),
            expectedWidth.asAnyMode(),
            expectedHeight.asAnyMode()
        )
    }

    fun resetTransitionTest() {
        clearInvocations(transaction)
    }
}
