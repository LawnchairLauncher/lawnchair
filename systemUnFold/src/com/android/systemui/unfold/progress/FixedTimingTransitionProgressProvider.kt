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
package com.android.systemui.unfold.progress

import android.animation.Animator
import android.animation.ObjectAnimator
import android.util.FloatProperty
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import com.android.systemui.unfold.updates.FOLD_UPDATE_FINISH_CLOSED
import com.android.systemui.unfold.updates.FoldStateProvider
import com.android.systemui.unfold.updates.FoldStateProvider.FoldUpdate
import javax.inject.Inject

/** Emits animation progress with fixed timing after unfolding */
internal class FixedTimingTransitionProgressProvider
@Inject
constructor(private val foldStateProvider: FoldStateProvider) :
    UnfoldTransitionProgressProvider, FoldStateProvider.FoldUpdatesListener {

    private val animatorListener = AnimatorListener()
    private val animator =
        ObjectAnimator.ofFloat(this, AnimationProgressProperty, 0f, 1f).apply {
            duration = TRANSITION_TIME_MILLIS
            addListener(animatorListener)
        }

    private var transitionProgress: Float = 0.0f
        set(value) {
            listeners.forEach { it.onTransitionProgress(value) }
            field = value
        }

    private val listeners: MutableList<TransitionProgressListener> = mutableListOf()

    init {
        foldStateProvider.addCallback(this)
        foldStateProvider.start()
    }

    override fun destroy() {
        animator.cancel()
        foldStateProvider.removeCallback(this)
        foldStateProvider.stop()
    }

    override fun onFoldUpdate(@FoldUpdate update: Int) {
        if (update == FOLD_UPDATE_FINISH_CLOSED) {
             animator.cancel()
        }
    }

    override fun onUnfoldedScreenAvailable() {
        animator.start()
    }

    override fun addCallback(listener: TransitionProgressListener) {
        listeners.add(listener)
    }

    override fun removeCallback(listener: TransitionProgressListener) {
        listeners.remove(listener)
    }

    private object AnimationProgressProperty :
        FloatProperty<FixedTimingTransitionProgressProvider>("animation_progress") {

        override fun setValue(provider: FixedTimingTransitionProgressProvider, value: Float) {
            provider.transitionProgress = value
        }

        override fun get(provider: FixedTimingTransitionProgressProvider): Float =
            provider.transitionProgress
    }

    private inner class AnimatorListener : Animator.AnimatorListener {

        override fun onAnimationStart(animator: Animator) {
            listeners.forEach { it.onTransitionStarted() }
            listeners.forEach { it.onTransitionFinishing() }
        }

        override fun onAnimationEnd(animator: Animator) {
            listeners.forEach { it.onTransitionFinished() }
        }

        override fun onAnimationRepeat(animator: Animator) {}

        override fun onAnimationCancel(animator: Animator) {}
    }

    private companion object {
        private const val TRANSITION_TIME_MILLIS = 400L
    }
}
