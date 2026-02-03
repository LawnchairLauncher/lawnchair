/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox.events

import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.suppliers.WindowContainerTransactionSupplier
import com.android.wm.shell.compatui.letterbox.LetterboxEvents.motionEventAt
import com.android.wm.shell.compatui.letterbox.animations.LetterboxAnimationHandler
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_MOVE_LETTERBOX_REACHABILITY
import java.util.function.Consumer
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [ReachabilityGestureListenerFactory].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:ReachabilityGestureListenerFactoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class ReachabilityGestureListenerFactoryTest : ShellTestCase() {

    @Test
    fun `When invoked a ReachabilityGestureListenerFactory is created`() {
        runTestScenario { r ->
            r.invokeCreate()

            r.checkReachabilityGestureListenerCreated()
        }
    }

    @Test
    fun `Right parameters are used for creation`() {
        runTestScenario { r ->
            r.invokeCreate()

            r.checkRightParamsAreUsed()
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<ReachabilityGestureListenerFactoryRobotTest>) {
        val robot = ReachabilityGestureListenerFactoryRobotTest()
        consumer.accept(robot)
    }

    class ReachabilityGestureListenerFactoryRobotTest {

        companion object {
            @JvmStatic
            private val TASK_ID = 1

            @JvmStatic
            private val TOKEN = mock<WindowContainerToken>()
        }

        private val transitions: Transitions
        private val animationHandler: LetterboxAnimationHandler
        private val factory: ReachabilityGestureListenerFactory
        private val wctSupplier: WindowContainerTransactionSupplier
        private val wct: WindowContainerTransaction
        private val letterboxState: LetterboxState
        private lateinit var obtainedResult: Any

        init {
            transitions = mock<Transitions>()
            animationHandler = mock<LetterboxAnimationHandler>()
            wctSupplier = mock<WindowContainerTransactionSupplier>()
            wct = mock<WindowContainerTransaction>()
            doReturn(wct).`when`(wctSupplier).get()
            letterboxState = LetterboxState()
            factory = ReachabilityGestureListenerFactory(
                transitions,
                animationHandler,
                wctSupplier,
                letterboxState
            )
        }

        fun invokeCreate(taskId: Int = TASK_ID, token: WindowContainerToken? = TOKEN) {
            obtainedResult = factory.createReachabilityGestureListener(taskId, token)
        }

        fun checkReachabilityGestureListenerCreated(expected: Boolean = true) {
            assertEquals(expected, obtainedResult is ReachabilityGestureListener)
        }

        fun checkRightParamsAreUsed(taskId: Int = TASK_ID, token: WindowContainerToken? = TOKEN) {
            with(obtainedResult as ReachabilityGestureListener) {
                // Click outside the bounds
                updateActivityBounds(Rect(0, 0, 10, 20))
                onDoubleTap(motionEventAt(50f, 100f))
                // WindowContainerTransactionSupplier is invoked to create a
                // WindowContainerTransaction
                verify(wctSupplier).get()
                // Verify the right params are passed to startAppCompatReachability()
                verify(wct).setReachabilityOffset(
                    token!!,
                    taskId,
                    50,
                    100
                )
                // startTransition() is invoked on Transitions with the right parameters
                verify(transitions).startTransition(
                    TRANSIT_MOVE_LETTERBOX_REACHABILITY,
                    wct,
                    animationHandler
                )
            }
        }
    }
}
