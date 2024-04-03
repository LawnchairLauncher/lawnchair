/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep.util.unfold

import android.os.Handler
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import android.util.Log
import androidx.test.filters.SmallTest
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidTestingRunner::class)
@RunWithLooper
class PreemptiveUnfoldTransitionProgressProviderTest {

    private lateinit var testableLooper: TestableLooper
    private lateinit var source: TransitionProgressListener
    private lateinit var handler: Handler
    private lateinit var oldWtfHandler: Log.TerribleFailureHandler
    private val listener: TransitionProgressListener = mock()
    private val testWtfHandler: Log.TerribleFailureHandler = mock()

    private lateinit var provider: PreemptiveUnfoldTransitionProgressProvider

    @Before
    fun before() {
        testableLooper = TestableLooper.get(this)
        handler = Handler(testableLooper.looper)

        val testSource = createSource()
        source = testSource as TransitionProgressListener

        oldWtfHandler = Log.setWtfHandler(testWtfHandler)

        provider = PreemptiveUnfoldTransitionProgressProvider(testSource, handler)
        provider.init()
        provider.addCallback(listener)
    }

    @After
    fun after() {
        Log.setWtfHandler(oldWtfHandler)
    }

    @Test
    fun preemptiveStartInitialProgressNull_transitionStarts() {
        provider.preemptivelyStartTransition(initialProgress = null)

        verify(listener).onTransitionStarted()
        verify(listener, never()).onTransitionProgress(any())
    }

    @Test
    fun preemptiveStartWithInitialProgress_startsAnimationAndSendsProgress() {
        provider.preemptivelyStartTransition(initialProgress = 0.5f)

        verify(listener).onTransitionStarted()
        verify(listener).onTransitionProgress(0.5f)
    }

    @Test
    fun preemptiveStartAndCancel_finishesAnimation() {
        provider.preemptivelyStartTransition()
        provider.cancelPreemptiveStart()

        inOrder(listener) {
            verify(listener).onTransitionStarted()
            verify(listener).onTransitionFinished()
        }
    }

    @Test
    fun preemptiveStartAndThenSourceStartsTransition_transitionStarts() {
        provider.preemptivelyStartTransition()
        source.onTransitionStarted()

        verify(listener).onTransitionStarted()
    }

    @Test
    fun preemptiveStartAndThenSourceStartsAndFinishesTransition_transitionFinishes() {
        provider.preemptivelyStartTransition()

        source.onTransitionStarted()
        source.onTransitionFinished()

        inOrder(listener) {
            verify(listener).onTransitionStarted()
            verify(listener).onTransitionFinished()
        }
    }

    @Test
    fun preemptiveStartAndThenSourceStartsAnimationAndSendsProgress_sendsProgress() {
        provider.preemptivelyStartTransition()

        source.onTransitionStarted()
        source.onTransitionProgress(0.4f)

        verify(listener).onTransitionProgress(0.4f)
    }

    @Test
    fun preemptiveStartAndThenSourceSendsProgress_sendsProgress() {
        provider.preemptivelyStartTransition()

        source.onTransitionProgress(0.4f)

        verify(listener).onTransitionProgress(0.4f)
    }

    @Test
    fun preemptiveStartAfterTransitionRunning_transitionStarted() {
        source.onTransitionStarted()

        provider.preemptivelyStartTransition()

        verify(listener).onTransitionStarted()
    }

    @Test
    fun preemptiveStartAfterTransitionRunningAndThenFinished_transitionFinishes() {
        source.onTransitionStarted()

        provider.preemptivelyStartTransition()
        source.onTransitionFinished()

        inOrder(listener) {
            verify(listener).onTransitionStarted()
            verify(listener).onTransitionFinished()
        }
    }

    @Test
    fun preemptiveStart_transitionDoesNotFinishAfterTimeout_finishesTransition() {
        provider.preemptivelyStartTransition()

        testableLooper.moveTimeForward(PREEMPTIVE_UNFOLD_TIMEOUT_MS + 1)
        testableLooper.processAllMessages()

        inOrder(listener) {
            verify(listener).onTransitionStarted()
            verify(listener).onTransitionFinished()
        }
    }

    @Test
    fun preemptiveStart_transitionFinishAfterTimeout_logsWtf() {
        provider.preemptivelyStartTransition()

        testableLooper.moveTimeForward(PREEMPTIVE_UNFOLD_TIMEOUT_MS + 1)
        testableLooper.processAllMessages()

        verify(testWtfHandler).onTerribleFailure(any(), any(), any())
    }

    @Test
    fun preemptiveStart_transitionDoesNotFinishBeforeTimeout_doesNotFinishTransition() {
        provider.preemptivelyStartTransition()

        testableLooper.moveTimeForward(PREEMPTIVE_UNFOLD_TIMEOUT_MS - 1)
        testableLooper.processAllMessages()

        verify(listener).onTransitionStarted()
    }

    @Test
    fun preemptiveStart_transitionStarted_timeoutHappened_doesNotFinishTransition() {
        provider.preemptivelyStartTransition()

        source.onTransitionStarted()
        testableLooper.moveTimeForward(PREEMPTIVE_UNFOLD_TIMEOUT_MS + 1)
        testableLooper.processAllMessages()

        verify(listener).onTransitionStarted()
    }

    @Test
    fun noPreemptiveStart_transitionStarted_startsTransition() {
        source.onTransitionStarted()

        verify(listener).onTransitionStarted()
    }

    @Test
    fun noPreemptiveStart_transitionProgress_sendsProgress() {
        source.onTransitionStarted()

        source.onTransitionProgress(0.5f)

        verify(listener).onTransitionProgress(0.5f)
    }

    @Test
    fun noPreemptiveStart_transitionFinishes_finishesTransition() {
        source.onTransitionStarted()
        source.onTransitionProgress(0.5f)

        source.onTransitionFinished()

        inOrder(listener) {
            verify(listener).onTransitionStarted()
            verify(listener).onTransitionFinished()
        }
    }

    private fun createSource(): UnfoldTransitionProgressProvider =
        object : TransitionProgressListener, UnfoldTransitionProgressProvider {

            private val listeners = arrayListOf<TransitionProgressListener>()

            override fun addCallback(listener: TransitionProgressListener) {
                listeners += listener
            }

            override fun removeCallback(listener: TransitionProgressListener) {
                listeners -= listener
            }

            override fun destroy() {}

            override fun onTransitionStarted() =
                listeners.forEach(TransitionProgressListener::onTransitionStarted)

            override fun onTransitionFinishing() =
                listeners.forEach(TransitionProgressListener::onTransitionFinishing)

            override fun onTransitionFinished() =
                listeners.forEach(TransitionProgressListener::onTransitionFinished)

            override fun onTransitionProgress(progress: Float) =
                listeners.forEach { it.onTransitionProgress(progress) }
        }
}
