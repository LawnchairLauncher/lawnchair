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

package com.android.launcher3.taskbar

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color.TRANSPARENT
import android.util.FloatProperty
import android.util.IntProperty
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.annotation.UiThread
import androidx.core.animation.doOnEnd
import com.android.app.animation.Interpolators.EMPHASIZED
import com.android.app.animation.Interpolators.LINEAR
import com.android.internal.dynamicanimation.animation.FloatValueHolder
import com.android.internal.dynamicanimation.animation.SpringAnimation
import com.android.internal.dynamicanimation.animation.SpringForce
import com.android.internal.dynamicanimation.animation.SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
import com.android.internal.dynamicanimation.animation.SpringForce.DAMPING_RATIO_NO_BOUNCY
import com.android.launcher3.BubbleTextView
import com.android.launcher3.BubbleTextView.RunningAppState
import com.android.launcher3.BubbleTextView.RunningAppState.MINIMIZED
import com.android.launcher3.BubbleTextView.RunningAppState.NOT_RUNNING
import com.android.launcher3.BubbleTextView.RunningAppState.RUNNING
import com.android.launcher3.R
import com.android.launcher3.Utilities as LauncherUtilities
import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.taskbar.TaskbarViewController.TRANSITION_DEFAULT_DURATION
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_APP_RUNNING_STATE_ANIM

private const val SPRING_START = 0f
private const val SPRING_END = 100f

private val SPRING_NO_BOUNCY =
    SpringForce(SPRING_END).apply {
        dampingRatio = DAMPING_RATIO_NO_BOUNCY
        stiffness = 3800f
    }
private val SPRING_MEDIUM_BOUNCY =
    SpringForce(SPRING_END).apply {
        dampingRatio = DAMPING_RATIO_MEDIUM_BOUNCY
        stiffness = 400f
    }

private val TRANSLATE_Y_PROPERTY =
    object : FloatProperty<BubbleTextView>("runningStateTranslateY") {
        private val BubbleTextView.runningStateTranslateYProp: MultiPropertyFactory<*>.MultiProperty
            get() = translateDelegate.getTranslationY(INDEX_TASKBAR_APP_RUNNING_STATE_ANIM)

        override fun get(btv: BubbleTextView): Float = btv.runningStateTranslateYProp.value

        override fun setValue(btv: BubbleTextView, translateY: Float) {
            btv.runningStateTranslateYProp.value = translateY
            btv.invalidate()
        }
    }

private val LINE_COLOR_PROPERTY =
    object : IntProperty<BubbleTextView>("lineIndicatorColor") {
        override fun get(btv: BubbleTextView): Int = btv.lineIndicatorColor

        override fun setValue(btv: BubbleTextView, color: Int) {
            btv.lineIndicatorColor = color
        }
    }

private val LINE_WIDTH_PROPERTY =
    object : FloatProperty<BubbleTextView>("lineIndicatorWidth") {
        override fun get(btv: BubbleTextView): Float = btv.lineIndicatorWidth

        override fun setValue(btv: BubbleTextView, width: Float) {
            btv.lineIndicatorWidth = width
        }
    }

/** Manages Taskbar [BubbleTextView] animations from changes in [RunningAppState]. */
class TaskbarRunningAppStateAnimationController(context: Context) {

    /** Animating [BubbleTextView] instances, where invoking each value cancels the animation. */
    private val runningAnimations = mutableMapOf<BubbleTextView, () -> Unit>()

    private val argbEvaluator = ArgbEvaluator()
    private val runningLineColor =
        context.resources.getColor(R.color.taskbar_running_app_indicator_color, context.theme)
    private val minimizedLineColor =
        context.resources.getColor(R.color.taskbar_minimized_app_indicator_color, context.theme)

    private val runningLineWidth =
        context.resources
            .getDimensionPixelSize(R.dimen.taskbar_running_app_indicator_width)
            .toFloat()
    private val minimizedLineWidth =
        context.resources
            .getDimensionPixelSize(R.dimen.taskbar_minimized_app_indicator_width)
            .toFloat()

    private val appTranslateYSpring =
        context.resources.getDimensionPixelSize(R.dimen.taskbar_app_translate_y_spring).toFloat()
    private val lineWidthSpring =
        context.resources
            .getDimensionPixelSize(R.dimen.taskbar_line_indicator_width_spring)
            .toFloat()

    // Copies the keys to avoid concurrent modification due to value callbacks modifying the map.
    fun onDestroy() = runningAnimations.keys.toList().forEach { cancelAnimation(it) }

    @UiThread
    fun updateRunningState(btv: BubbleTextView, runningState: RunningAppState, animate: Boolean) {
        val prevRunningState = btv.runningAppState ?: NOT_RUNNING
        if (runningState == prevRunningState) return

        cancelAnimation(btv)
        btv.runningAppState = runningState
        if (!animate) return applyRunningState(btv)

        val isPinnedApp = btv.tag is TaskItemInfo
        if (
            (prevRunningState == RUNNING && runningState == MINIMIZED) ||
                (prevRunningState == MINIMIZED && runningState == RUNNING) ||
                (isPinnedApp && runningState == RUNNING)
        ) {
            return startAppBounceAnimation(btv)
        }

        // Otherwise just animate line width and color.
        AnimatorSet().run {
            if (runningState == RUNNING) {
                // New unpinned app - delay animation until icon is mostly scaled in.
                startDelay = UNPINNED_APP_LINE_ANIM_DELAY
            }

            duration = LINE_ANIM_DURATION
            interpolator = EMPHASIZED

            playTogether(
                ObjectAnimator.ofFloat(btv, LINE_WIDTH_PROPERTY, runningState.lineWidth),
                ObjectAnimator.ofArgb(btv, LINE_COLOR_PROPERTY, runningState.lineColor),
            )

            doOnEnd {
                runningAnimations.remove(btv)
                applyRunningState(btv)
            }

            runningAnimations[btv] = this::cancel
            start()
        }
    }

    fun isAnimationRunning(btv: BubbleTextView): Boolean = runningAnimations.containsKey(btv)

    private fun cancelAnimation(btv: BubbleTextView) = runningAnimations[btv]?.invoke()

    private fun startAppBounceAnimation(btv: BubbleTextView) {
        val isMinimized = btv.runningAppState == MINIMIZED
        val translateYSpring = if (isMinimized) appTranslateYSpring else -appTranslateYSpring
        val prevLineWidth = btv.lineIndicatorWidth
        val prevLineColor = btv.lineIndicatorColor

        val translateYProp =
            btv.translateDelegate.getTranslationY(INDEX_TASKBAR_APP_RUNNING_STATE_ANIM)
        fun updateTranslateY(value: Float) {
            translateYProp.value = value
            btv.invalidate()
        }

        SpringAnimation(FloatValueHolder()).run {
            spring = SPRING_NO_BOUNCY
            addUpdateListener { _, v, _ ->
                updateTranslateY(mapValue(v, 0f, translateYSpring))
                if (isMinimized) {
                    btv.lineIndicatorWidth = mapValue(v, prevLineWidth, lineWidthSpring)
                }
            }

            addEndListener { _, canceled, _, _ ->
                runningAnimations.remove(btv)
                if (canceled) return@addEndListener applyRunningState(btv)

                val startLineWidth = if (isMinimized) lineWidthSpring else prevLineWidth
                val endLineWidth = btv.runningAppState.lineWidth
                val endLineColor = btv.runningAppState.lineColor

                val springs =
                    listOf(
                        SpringAnimation(FloatValueHolder()).apply {
                            spring = SPRING_MEDIUM_BOUNCY
                            addUpdateListener { _, v, _ ->
                                updateTranslateY(mapValue(v, translateYSpring, 0f))
                                btv.lineIndicatorWidth = mapValue(v, startLineWidth, endLineWidth)
                            }
                        },
                        SpringAnimation(FloatValueHolder()).apply {
                            spring = SPRING_NO_BOUNCY
                            addUpdateListener { _, v, _ ->
                                btv.lineIndicatorColor =
                                    argbEvaluator.evaluate(
                                        v / SPRING_END,
                                        prevLineColor,
                                        endLineColor,
                                    ) as Int
                            }
                        },
                    )

                runningAnimations[btv] = { for (s in springs) s.cancel() }
                var runningSprings = springs.size
                for (s in springs) {
                    s.addEndListener { _, canceled2, _, _ ->
                        if (--runningSprings == 0) {
                            runningAnimations.remove(btv)
                            if (canceled2) applyRunningState(btv)
                        }
                    }
                    s.start()
                }
            }

            runningAnimations[btv] = this::cancel
            start()
        }
    }

    private fun applyRunningState(btv: BubbleTextView) {
        btv.lineIndicatorWidth = btv.runningAppState.lineWidth
        btv.lineIndicatorColor = btv.runningAppState.lineColor
        TRANSLATE_Y_PROPERTY[btv] = 0f
    }

    private fun mapValue(value: Float, min: Float, max: Float): Float {
        return LauncherUtilities.mapToRange(value, SPRING_START, SPRING_END, min, max, LINEAR)
    }

    @get:ColorInt
    val RunningAppState.lineColor: Int
        get() {
            return when (this) {
                NOT_RUNNING -> TRANSPARENT
                RUNNING -> runningLineColor
                MINIMIZED -> minimizedLineColor
            }
        }

    @get:Px
    val RunningAppState.lineWidth: Float
        get() {
            return when (this) {
                NOT_RUNNING -> 0f
                RUNNING -> runningLineWidth
                MINIMIZED -> minimizedLineWidth
            }
        }

    companion object {
        const val LINE_ANIM_DURATION = 100L
        const val UNPINNED_APP_LINE_ANIM_DELAY = TRANSITION_DEFAULT_DURATION - LINE_ANIM_DURATION
    }
}
