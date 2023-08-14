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
import android.os.Trace
import android.util.Log
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener

/**
 * Transition progress provider wrapper that can preemptively start the transition on demand
 * without relying on the source provider. When the source provider has started the animation
 * it switches to it.
 *
 * This might be useful when we want to synchronously start the unfold animation and render
 * the first frame during turning on the screen. For example, this is used in Launcher where
 * we need to render the first frame of the animation immediately after receiving a configuration
 * change event so Window Manager will wait for this frame to be rendered before unblocking
 * the screen. We can't rely on the original transition progress as it starts the animation
 * after the screen fully turned on (and unblocked), at this moment it is already too late to
 * start the animation.
 *
 * Using this provider we could render the first frame preemptively by sending 'transition started'
 * and '0' transition progress before the original progress provider sends these events.
 */
class PreemptiveUnfoldTransitionProgressProvider(
        private val source: UnfoldTransitionProgressProvider,
        private val handler: Handler
) : UnfoldTransitionProgressProvider, TransitionProgressListener {

    private val timeoutRunnable = Runnable {
        if (isRunning) {
            listeners.forEach { it.onTransitionFinished() }
            onPreemptiveStartFinished()
            Log.wtf(TAG, "Timeout occurred when waiting for the source transition to start")
        }
    }

    private val listeners = arrayListOf<TransitionProgressListener>()
    private var isPreemptivelyRunning = false
    private var isSourceRunning = false

    private val isRunning: Boolean
        get() = isPreemptivelyRunning || isSourceRunning

    private val sourceListener =
            object : TransitionProgressListener {
                override fun onTransitionStarted() {
                    handler.removeCallbacks(timeoutRunnable)

                    if (!isRunning) {
                        listeners.forEach { it.onTransitionStarted() }
                    }

                    onPreemptiveStartFinished()
                    isSourceRunning = true
                }

                override fun onTransitionProgress(progress: Float) {
                    if (isRunning) {
                        listeners.forEach { it.onTransitionProgress(progress) }
                        isSourceRunning = true
                    }
                }

                override fun onTransitionFinishing() {
                    if (isRunning) {
                        listeners.forEach { it.onTransitionFinishing() }
                        isSourceRunning = true
                    }
                }

                override fun onTransitionFinished() {
                    if (isRunning) {
                        listeners.forEach { it.onTransitionFinished() }
                    }

                    isSourceRunning = false
                    onPreemptiveStartFinished()
                    handler.removeCallbacks(timeoutRunnable)
                }
            }

    fun init() {
        source.addCallback(sourceListener)
    }

    /**
     * Starts the animation preemptively.
     *
     * - If the source provider is already running, this method won't change any behavior
     * - If the source provider has not started running yet, it will call onTransitionStarted
     *   for all listeners and optionally onTransitionProgress(initialProgress) if supplied.
     *   When the source provider starts the animation it will switch to send progress and finished
     *   events from it.
     *   If the source provider won't start the animation within a timeout, the animation will be
     *   cancelled and onTransitionFinished will be delivered to the current listeners.
     */
    @JvmOverloads
    fun preemptivelyStartTransition(initialProgress: Float? = null) {
        if (!isRunning) {
            Trace.beginAsyncSection("$TAG#startedPreemptively", 0)

            listeners.forEach { it.onTransitionStarted() }
            initialProgress?.let { progress ->
                listeners.forEach { it.onTransitionProgress(progress) }
            }

            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, PREEMPTIVE_UNFOLD_TIMEOUT_MS)
        }

        isPreemptivelyRunning = true
    }

    fun cancelPreemptiveStart() {
        handler.removeCallbacks(timeoutRunnable)
        if (isRunning) {
            listeners.forEach { it.onTransitionFinished() }
        }
        onPreemptiveStartFinished()
    }

    private fun onPreemptiveStartFinished() {
        if (isPreemptivelyRunning) {
            Trace.endAsyncSection("$TAG#startedPreemptively", 0)
            isPreemptivelyRunning = false
        }
    }

    override fun destroy() {
        handler.removeCallbacks(timeoutRunnable)
        source.removeCallback(sourceListener)
        source.destroy()
    }

    override fun addCallback(listener: TransitionProgressListener) {
        listeners += listener
    }

    override fun removeCallback(listener: TransitionProgressListener) {
        listeners -= listener
    }
}

const val TAG = "PreemptiveUnfoldTransitionProgressProvider"
const val PREEMPTIVE_UNFOLD_TIMEOUT_MS = 1700L
