/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.unfold.util

import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener

/**
 * Manages progress listeners that can have smaller lifespan than the unfold animation.
 *
 * Allows to limit getting transition updates to only when
 * [ScopedUnfoldTransitionProgressProvider.setReadyToHandleTransition] is called with
 * readyToHandleTransition = true
 *
 * If the transition has already started by the moment when the clients are ready to play the
 * transition then it will report transition started callback and current animation progress.
 */
open class ScopedUnfoldTransitionProgressProvider
@JvmOverloads
constructor(source: UnfoldTransitionProgressProvider? = null) :
    UnfoldTransitionProgressProvider, TransitionProgressListener {

    private var source: UnfoldTransitionProgressProvider? = null

    private val listeners: MutableList<TransitionProgressListener> = mutableListOf()

    private var isReadyToHandleTransition = false
    private var isTransitionRunning = false
    private var lastTransitionProgress = PROGRESS_UNSET

    init {
        setSourceProvider(source)
    }
    /**
     * Sets the source for the unfold transition progress updates. Replaces current provider if it
     * is already set
     *
     * @param provider transition provider that emits transition progress updates
     */
    fun setSourceProvider(provider: UnfoldTransitionProgressProvider?) {
        source?.removeCallback(this)

        if (provider != null) {
            source = provider
            provider.addCallback(this)
        } else {
            source = null
        }
    }

    /**
     * Allows to notify this provide whether the listeners can play the transition or not.
     *
     * Call this method with readyToHandleTransition = true when all listeners are ready to consume
     * the transition progress events.
     *
     * Call it with readyToHandleTransition = false when listeners can't process the events.
     */
    fun setReadyToHandleTransition(isReadyToHandleTransition: Boolean) {
        if (isTransitionRunning) {
            if (isReadyToHandleTransition) {
                listeners.forEach { it.onTransitionStarted() }
                if (lastTransitionProgress != PROGRESS_UNSET) {
                    listeners.forEach { it.onTransitionProgress(lastTransitionProgress) }
                }
            } else {
                isTransitionRunning = false
                listeners.forEach { it.onTransitionFinished() }
            }
        }
        this.isReadyToHandleTransition = isReadyToHandleTransition
    }

    override fun addCallback(listener: TransitionProgressListener) {
        listeners += listener
    }

    override fun removeCallback(listener: TransitionProgressListener) {
        listeners -= listener
    }

    override fun destroy() {
        source?.removeCallback(this)
        source?.destroy()
    }

    override fun onTransitionStarted() {
        isTransitionRunning = true
        if (isReadyToHandleTransition) {
            listeners.forEach { it.onTransitionStarted() }
        }
    }

    override fun onTransitionProgress(progress: Float) {
        if (isReadyToHandleTransition) {
            listeners.forEach { it.onTransitionProgress(progress) }
        }
        lastTransitionProgress = progress
    }

    override fun onTransitionFinishing() {
        if (isReadyToHandleTransition) {
            listeners.forEach { it.onTransitionFinishing() }
        }
    }

    override fun onTransitionFinished() {
        if (isReadyToHandleTransition) {
            listeners.forEach { it.onTransitionFinished() }
        }
        isTransitionRunning = false
        lastTransitionProgress = PROGRESS_UNSET
    }

    companion object {
        private const val PROGRESS_UNSET = -1f
    }
}
