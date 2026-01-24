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

package com.android.wm.shell.compatui.letterbox

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.compatui.letterbox.lifecycle.FakeLetterboxLifecycleEventFactory
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleController
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEvent
import com.android.wm.shell.compatui.letterbox.lifecycle.toLetterboxLifecycleEvent
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.executeTransitionObserverTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.function.Consumer

/**
 * Tests for [DelegateLetterboxTransitionObserver].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:DelegateLetterboxTransitionObserverTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class DelegateLetterboxTransitionObserverTest : ShellTestCase() {

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `when initialized and flag disabled the observer is not registered`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()
                r.checkObservableIsRegistered(expected = false)
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `when initialized and flag enabled the observer is registered`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()
                r.checkObservableIsRegistered(expected = true)
            }
        }
    }

    @Test
    fun `LetterboxLifecycleController not used with no changes`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()

                inputBuilder {
                    buildTransitionInfo()
                }

                validateOutput {
                    r.checkLifecycleControllerInvoked(times = 0)
                }
            }
        }
    }

    @Test
    fun `LetterboxLifecycleController used with a single change`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()

                inputBuilder {
                    buildTransitionInfo()
                    addChange(createChange())
                }

                validateOutput {
                    r.checkLifecycleControllerInvoked(times = 1)
                }
            }
        }
    }

    @Test
    fun `LetterboxLifecycleController used for each change`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()

                inputBuilder {
                    buildTransitionInfo()
                    addChange(createChange())
                    addChange(createChange())
                    addChange(createChange())
                }

                validateOutput {
                    r.checkLifecycleControllerInvoked(times = 3)
                    r.checkOnLetterboxLifecycleEventFactory { factory ->
                        assert(factory.canHandleInvokeTimes == 3)
                    }
                }
            }
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<LetterboxTransitionObserverRobotTest>) {
        val robot = LetterboxTransitionObserverRobotTest()
        consumer.accept(robot)
    }

    class LetterboxTransitionObserverRobotTest {

        private val executor: ShellExecutor
        private val shellInit: ShellInit
        private val transitions: Transitions
        private val letterboxObserver: DelegateLetterboxTransitionObserver
        private val letterboxLifecycleController: LetterboxLifecycleController
        private var letterboxLifecycleEventFactory: FakeLetterboxLifecycleEventFactory

        val observerFactory: () -> DelegateLetterboxTransitionObserver

        init {
            executor = mock<ShellExecutor>()
            shellInit = ShellInit(executor)
            transitions = mock<Transitions>()
            letterboxLifecycleController = mock<LetterboxLifecycleController>()
            letterboxLifecycleEventFactory = FakeLetterboxLifecycleEventFactory(
                eventToReturnFactory = { c -> c.toLetterboxLifecycleEvent() }
            )
            letterboxObserver =
                DelegateLetterboxTransitionObserver(
                    shellInit,
                    transitions,
                    letterboxLifecycleController,
                    letterboxLifecycleEventFactory
                )
            observerFactory = { letterboxObserver }
        }

        fun invokeShellInit() = shellInit.init()

        fun observer() = letterboxObserver

        fun checkObservableIsRegistered(expected: Boolean) {
            verify(transitions, expected.asMode()).registerObserver(observer())
        }

        fun checkOnLetterboxLifecycleEventFactory(
            consumer: (FakeLetterboxLifecycleEventFactory) -> Unit
        ) {
            consumer(letterboxLifecycleEventFactory)
        }

        fun checkLifecycleControllerInvoked(
            times: Int = 1,
        ) = verify(
            letterboxLifecycleController,
            times(times)
        ).onLetterboxLifecycleEvent(
            any<LetterboxLifecycleEvent>(),
            any<SurfaceControl.Transaction>(),
            any<SurfaceControl.Transaction>()
        )
    }
}
