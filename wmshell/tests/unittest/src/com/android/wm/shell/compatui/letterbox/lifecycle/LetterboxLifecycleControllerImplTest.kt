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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.LetterboxController
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy
import com.android.wm.shell.compatui.letterbox.LetterboxKey
import com.android.wm.shell.compatui.letterbox.asMode
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [LetterboxLifecycleControllerImpl].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:LetterboxLifecycleControllerImplTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxLifecycleControllerImplTest : ShellTestCase() {

    @Test
    fun `this is my test`() {
        runTestScenario { r ->
            r.invokeLifecycleControllerWith(
                r.createLifecycleEvent()
            )
        }
    }

    @Test
    fun `Letterbox is hidden with OPEN Transition but not letterboxed`() {
        runTestScenario { r ->
            r.invokeLifecycleControllerWith(
                r.createLifecycleEvent(
                    type = LetterboxLifecycleEventType.OPEN
                )
            )
            r.verifyUpdateLetterboxSurfaceVisibility(expected = true)
        }
    }

    @Test
    fun `Surface created with OPEN Transition and letterboxed with leash`() {
        runTestScenario { r ->
            r.invokeLifecycleControllerWith(
                r.createLifecycleEvent(
                    type = LetterboxLifecycleEventType.OPEN,
                    letterboxBounds = Rect(500, 0, 800, 1800)
                )
            )
            r.verifyCreateLetterboxSurface(expected = true)
        }
    }

    @Test
    fun `Surface NOT created with OPEN Transition and letterboxed with NO leash`() {
        runTestScenario { r ->
            r.invokeLifecycleControllerWith(
                r.createLifecycleEvent(
                    type = LetterboxLifecycleEventType.OPEN,
                    letterboxBounds = Rect(500, 0, 800, 1800),
                    eventTaskLeash = null
                )
            )
            r.verifyCreateLetterboxSurface(expected = false)
        }
    }

    @Test
    fun `Surface Bounds updated with OPEN Transition and letterboxed`() {
        runTestScenario { r ->
            r.invokeLifecycleControllerWith(
                r.createLifecycleEvent(
                    type = LetterboxLifecycleEventType.OPEN,
                    letterboxBounds = Rect(500, 0, 800, 1800),
                    eventTaskLeash = null
                )
            )
            r.verifyUpdateLetterboxSurfaceBounds(
                expected = true,
                letterboxBounds = Rect(500, 0, 800, 1800)
            )
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<LetterboxLifecycleControllerImplRobotTest>) {
        val robot = LetterboxLifecycleControllerImplRobotTest()
        consumer.accept(robot)
    }

    class LetterboxLifecycleControllerImplRobotTest {

        private val lifecycleController: LetterboxLifecycleControllerImpl
        private val letterboxController: LetterboxController
        private val letterboxModeStrategy: LetterboxControllerStrategy
        private val startTransaction: Transaction
        private val finishTransaction: Transaction
        private val token: WindowContainerToken
        private val taskLeash: SurfaceControl

        companion object {
            @JvmStatic
            private val DISPLAY_ID = 1

            @JvmStatic
            private val TASK_ID = 20

            @JvmStatic
            private val TASK_BOUNDS = Rect(0, 0, 2800, 1400)
        }

        init {
            letterboxController = mock<LetterboxController>()
            letterboxModeStrategy = mock<LetterboxControllerStrategy>()
            startTransaction = mock<Transaction>()
            finishTransaction = mock<Transaction>()
            token = mock<WindowContainerToken>()
            taskLeash = mock<SurfaceControl>()
            lifecycleController = LetterboxLifecycleControllerImpl(
                letterboxController,
                letterboxModeStrategy
            )
        }

        fun createLifecycleEvent(
            type: LetterboxLifecycleEventType = LetterboxLifecycleEventType.NONE,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID,
            taskBounds: Rect = TASK_BOUNDS,
            letterboxBounds: Rect? = null,
            letterboxActivityToken: WindowContainerToken = token,
            eventTaskLeash: SurfaceControl? = taskLeash
        ): LetterboxLifecycleEvent = LetterboxLifecycleEvent(
            type = type,
            displayId = displayId,
            taskId = taskId,
            taskBounds = taskBounds,
            letterboxBounds = letterboxBounds,
            containerToken = letterboxActivityToken,
            taskLeash = eventTaskLeash
        )

        fun invokeLifecycleControllerWith(event: LetterboxLifecycleEvent) {
            lifecycleController.onLetterboxLifecycleEvent(
                event,
                startTransaction,
                finishTransaction
            )
        }

        fun verifyCreateLetterboxSurface(
            expected: Boolean,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID
        ) {
            verify(
                letterboxController,
                expected.asMode()
            ).createLetterboxSurface(
                eq(LetterboxKey(displayId, taskId)),
                eq(startTransaction),
                eq(taskLeash),
                eq(token)
            )
        }

        fun verifyUpdateLetterboxSurfaceVisibility(
            expected: Boolean,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID
        ) {
            verify(
                letterboxController,
                expected.asMode()
            ).updateLetterboxSurfaceVisibility(
                eq(LetterboxKey(displayId, taskId)),
                eq(startTransaction),
                eq(false)
            )
        }

        fun verifyUpdateLetterboxSurfaceBounds(
            expected: Boolean,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID,
            taskBounds: Rect = TASK_BOUNDS,
            letterboxBounds: Rect,

            ) {
            verify(
                letterboxController,
                expected.asMode()
            ).updateLetterboxSurfaceBounds(
                eq(LetterboxKey(displayId, taskId)),
                eq(startTransaction),
                eq(taskBounds),
                eq(letterboxBounds)
            )
        }
    }
}
