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

package com.android.wm.shell.desktopmode

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.Choreographer
import android.view.SurfaceControl.Transaction
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo.Change
import androidx.core.util.Supplier
import com.android.wm.shell.animation.FloatProperties
import com.android.wm.shell.desktopmode.SpringDragToDesktopTransitionHandler.Companion.POSITION_SPRING_DAMPING_RATIO
import com.android.wm.shell.desktopmode.SpringDragToDesktopTransitionHandler.Companion.POSITION_SPRING_STIFFNESS
import com.android.wm.shell.desktopmode.SpringDragToDesktopTransitionHandler.Companion.SIZE_SPRING_DAMPING_RATIO
import com.android.wm.shell.desktopmode.SpringDragToDesktopTransitionHandler.Companion.SIZE_SPRING_STIFFNESS
import com.android.wm.shell.desktopmode.SpringDragToDesktopTransitionHandler.Companion.getAnimationFraction
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import javax.inject.Inject

/**
 * Helper class for creating and managing animations related to drag and drop operations in Desktop
 * Mode. This class provides methods to create different types of animations, for example, covers
 * different animations for tab tearing.
 *
 * <p>It utilizes {@link PhysicsAnimator} for physics-based animations and {@link ValueAnimator} for
 * simpler animations like fade-in.</p>
 */
class DesktopModeDragAndDropAnimatorHelper
@Inject
constructor(val context: Context, val transactionSupplier: Supplier<Transaction>) {

    /**
     * Creates an animator for a given change, incorporating start and finish callbacks.
     *
     * This function is responsible for creating an animator that handles the visual changes defined
     * by the provided [Change] object. It leverages a transaction supplier to manage the transition
     * and provides callbacks to be executed when the animation starts and finishes.
     *
     * @param change The [Change] object describing the desired visual transition. It should contain
     *   information like the view that should be animated (leash) and the start/end values.
     * @param finishCallback A [TransitionFinishCallback] that will be invoked when the animation
     *   completes. It will inform the caller that the transition is finished.
     * @return An [DesktopModeDragAndDropAnimator] instance configured to perform the change
     *   described by the `change` parameter.
     */
    fun createAnimator(
        change: Change,
        draggedTaskBounds: Rect,
        finishCallback: TransitionFinishCallback,
    ): DesktopModeDragAndDropAnimator {
        val transaction = transactionSupplier.get()

        val animatorStartedCallback: () -> Unit = {
            transaction.show(change.leash)
            transaction.apply()
        }
        val animatorFinishedCallback: () -> Unit = { finishCallback.onTransitionFinished(null) }

        return if (DesktopExperienceFlags.ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION.isTrue) {
            createSpringAnimator(
                change,
                draggedTaskBounds,
                animatorStartedCallback,
                animatorFinishedCallback,
            )
        } else {
            createAlphaAnimator(change, animatorStartedCallback, animatorFinishedCallback)
        }
    }

    private fun createSpringAnimator(
        change: Change,
        draggedTaskBounds: Rect,
        onStart: () -> Unit,
        onFinish: () -> Unit,
    ): DesktopModeDragAndDropAnimator {
        val transaction = transactionSupplier.get()

        val positionSpringConfig =
            PhysicsAnimator.SpringConfig(POSITION_SPRING_STIFFNESS, POSITION_SPRING_DAMPING_RATIO)
        val sizeSpringConfig =
            PhysicsAnimator.SpringConfig(SIZE_SPRING_STIFFNESS, SIZE_SPRING_DAMPING_RATIO)
        val endBounds = change.endAbsBounds

        var hasCalledStart = false
        return DesktopModeDragAndDropSpringAnimator(
            PhysicsAnimator.getInstance(Rect(draggedTaskBounds))
                // TODO(b/412571881): Add velocity to tab tearing animation
                .spring(FloatProperties.RECT_X, endBounds.left.toFloat(), positionSpringConfig)
                .spring(FloatProperties.RECT_Y, endBounds.top.toFloat(), positionSpringConfig)
                .spring(FloatProperties.RECT_WIDTH, endBounds.width().toFloat(), sizeSpringConfig)
                .spring(FloatProperties.RECT_HEIGHT, endBounds.height().toFloat(), sizeSpringConfig)
                .addUpdateListener { animBounds, _ ->
                    if (!hasCalledStart) {
                        onStart.invoke()
                        hasCalledStart = true
                    }
                    val animFraction =
                        getAnimationFraction(
                            startBounds = draggedTaskBounds,
                            endBounds = endBounds,
                            animBounds = animBounds,
                        )
                    transaction.apply {
                        setAlpha(change.leash, animFraction)
                        setScale(change.leash, animFraction, animFraction)
                        setPosition(
                            change.leash,
                            animBounds.left.toFloat(),
                            animBounds.top.toFloat(),
                        )
                        setFrameTimeline(Choreographer.getInstance().vsyncId)
                        apply()
                    }
                }
                .withEndActions({ onFinish.invoke() })
        )
    }

    private fun createAlphaAnimator(
        change: Change,
        onStart: () -> Unit,
        onFinish: () -> Unit,
    ): DesktopModeDragAndDropAnimator {
        val transaction = transactionSupplier.get()

        val alphaAnimator = ValueAnimator()
        alphaAnimator.setFloatValues(0f, 1f)
        alphaAnimator.setDuration(FADE_IN_ANIMATION_DURATION)

        alphaAnimator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    onStart.invoke()
                }

                override fun onAnimationEnd(animation: Animator) {
                    onFinish.invoke()
                }
            }
        )
        alphaAnimator.addUpdateListener { animation: ValueAnimator ->
            transaction.setAlpha(change.leash, animation.animatedFraction)
            transaction.apply()
        }

        return DesktopModeDragAndDropAlphaAnimator(alphaAnimator)
    }

    companion object {
        const val FADE_IN_ANIMATION_DURATION = 300L
    }
}

/**
 * Abstract base class defining the contract for animations related to drag-and-drop operations
 * within a Desktop Mode feature.
 *
 * This abstract class serves as a necessary wrapper to provide compatibility between different
 * types of animators used in specific drag-and-drop scenarios. Subclasses might use standard
 * Android `Animator` instances or more specialized animators like `PhysicsAnimator`.
 */
abstract class DesktopModeDragAndDropAnimator {
    /** Starts the specific drag-and-drop animation sequence. */
    abstract fun start()
}

/**
 * A concrete implementation of [DesktopModeDragAndDropAnimator] specifically designed for animating
 * alpha of the window.
 *
 * @param animator The standard Android [Animator] instance that executes the launch animation.
 */
class DesktopModeDragAndDropAlphaAnimator(val animator: Animator) :
    DesktopModeDragAndDropAnimator() {

    override fun start() = animator.start()
}

/**
 * A concrete implementation of [DesktopModeDragAndDropAnimator] specifically designed for spring
 * animations of different properties of the window (position, size etc.)
 *
 * @param animator The `PhysicsAnimator<Rect>` instance responsible for the spring animation.
 */
class DesktopModeDragAndDropSpringAnimator(val animator: PhysicsAnimator<Rect>) :
    DesktopModeDragAndDropAnimator() {

    override fun start() = animator.start()
}
