/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar.bubbles

import android.animation.ValueAnimator
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.animation.doOnEnd
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.anim.SpringAnimationBuilder
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarThresholdUtils
import com.android.launcher3.taskbar.bubbles.BubbleBarSwipeController.BarState.COLLAPSED
import com.android.launcher3.taskbar.bubbles.BubbleBarSwipeController.BarState.EXPANDED
import com.android.launcher3.taskbar.bubbles.BubbleBarSwipeController.BarState.STASHED
import com.android.launcher3.taskbar.bubbles.BubbleBarSwipeController.BarState.UNKNOWN
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.touch.OverScroll

/** Handle swipe events on the bubble bar and handle */
class BubbleBarSwipeController {

    private val context: Context

    private var bubbleStashedHandleViewController: BubbleStashedHandleViewController? = null
    private lateinit var bubbleBarViewController: BubbleBarViewController
    private lateinit var bubbleStashController: BubbleStashController

    private var springAnimation: ValueAnimator? = null
    private val animatedSwipeTranslation = AnimatedFloat(this::onSwipeUpdate)

    private val unstashThreshold: Int
    private val maxOverscroll: Int

    private var swipeState: SwipeState = SwipeState(startState = UNKNOWN)

    constructor(tac: TaskbarActivityContext) : this(tac, DefaultDimensionProvider(tac))

    @VisibleForTesting
    constructor(context: Context, dimensionProvider: DimensionProvider) {
        this.context = context
        unstashThreshold = dimensionProvider.unstashThreshold
        maxOverscroll = dimensionProvider.maxOverscroll
    }

    fun init(bubbleControllers: BubbleControllers) {
        bubbleStashedHandleViewController =
            bubbleControllers.bubbleStashedHandleViewController.orElse(null)
        bubbleBarViewController = bubbleControllers.bubbleBarViewController
        bubbleStashController = bubbleControllers.bubbleStashController
    }

    /** Start tracking a new swipe gesture */
    fun start() {
        if (springAnimation != null) reset()
        val startState =
            when {
                bubbleStashController.isStashed -> STASHED
                bubbleBarViewController.isExpanded -> EXPANDED
                bubbleStashController.isBubbleBarVisible() -> COLLAPSED
                else -> UNKNOWN
            }
        swipeState = SwipeState(startState = startState, currentState = startState)
    }

    /** Update swipe distance to [dy] */
    fun swipeTo(dy: Float) {
        if (!canHandleSwipe(dy)) {
            return
        }
        animatedSwipeTranslation.updateValue(dy)

        swipeState.passedUnstash = isUnstash(dy)
        // Tracking swipe gesture if we pass unstash threshold at least once during gesture
        swipeState.isSwipe = swipeState.isSwipe || swipeState.passedUnstash
        when {
            canUnstash() && swipeState.passedUnstash -> {
                swipeState.currentState = COLLAPSED
                bubbleStashController.showBubbleBar(expandBubbles = false, bubbleBarGesture = true)
            }
            canStash() && !swipeState.passedUnstash -> {
                swipeState.currentState = STASHED
                bubbleStashController.stashBubbleBar()
            }
        }
    }

    /** Finish tracking swipe gesture. Animate views back to resting state */
    fun finish() {
        if (swipeState.passedUnstash && swipeState.startState in setOf(STASHED, COLLAPSED)) {
            bubbleStashController.showBubbleBar(expandBubbles = true, bubbleBarGesture = true)
        }
        if (animatedSwipeTranslation.value == 0f) {
            reset()
        } else {
            springToRest()
        }
    }

    /** Returns `true` if we are tracking a swipe gesture */
    fun isSwipeGesture(): Boolean {
        return swipeState.isSwipe
    }

    private fun canHandleSwipe(dy: Float): Boolean {
        return when (swipeState.startState) {
            STASHED -> {
                if (swipeState.currentState == COLLAPSED) {
                    // if we have unstashed the bar, allow swipe in both directions
                    true
                } else {
                    // otherwise, only allow swipe up on stash handle
                    dy < 0
                }
            }
            COLLAPSED -> dy < 0 // collapsed bar can only be swiped up
            UNKNOWN,
            EXPANDED -> false // expanded bar can't be swiped
        }
    }

    private fun isUnstash(dy: Float): Boolean {
        return dy < -unstashThreshold
    }

    private fun canStash(): Boolean {
        // Only allow stashing if we started from stashed state
        return swipeState.startState == STASHED && swipeState.currentState == COLLAPSED
    }

    private fun canUnstash(): Boolean {
        return swipeState.currentState == STASHED
    }

    private fun reset() {
        springAnimation?.let {
            if (it.isRunning) {
                it.removeAllListeners()
                it.cancel()
                animatedSwipeTranslation.updateValue(0f)
            }
        }
        springAnimation = null
        swipeState = SwipeState(startState = UNKNOWN)
    }

    private fun onSwipeUpdate(value: Float) {
        val dampedSwipe = -OverScroll.dampedScroll(-value, maxOverscroll).toFloat()
        bubbleStashedHandleViewController?.setTranslationYForSwipe(dampedSwipe)
        bubbleBarViewController.setTranslationYForSwipe(dampedSwipe)
    }

    private fun springToRest() {
        springAnimation =
            SpringAnimationBuilder(context)
                .setStartValue(animatedSwipeTranslation.value)
                .setEndValue(0f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .build(animatedSwipeTranslation, AnimatedFloat.VALUE)
                .also { it.doOnEnd { reset() } }
        springAnimation?.start()
    }

    internal data class SwipeState(
        val startState: BarState,
        var currentState: BarState = UNKNOWN,
        var passedUnstash: Boolean = false,
        var isSwipe: Boolean = false,
    )

    internal enum class BarState {
        UNKNOWN,
        STASHED,
        COLLAPSED,
        EXPANDED,
    }

    /** Allows overriding the dimension provider for testing */
    @VisibleForTesting
    interface DimensionProvider {
        val unstashThreshold: Int
        val maxOverscroll: Int
    }

    private class DefaultDimensionProvider(taskbarActivityContext: TaskbarActivityContext) :
        DimensionProvider {
        override val unstashThreshold: Int
        override val maxOverscroll: Int

        init {
            val resources = taskbarActivityContext.resources
            unstashThreshold =
                TaskbarThresholdUtils.getFromNavThreshold(
                    resources,
                    taskbarActivityContext.deviceProfile,
                )
            maxOverscroll = taskbarActivityContext.deviceProfile.heightPx - unstashThreshold
        }
    }
}
