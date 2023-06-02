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
package com.android.launcher3.taskbar

import android.view.MotionEvent
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_NEGATIVE
import com.android.launcher3.touch.SingleAxisSwipeDetector.VERTICAL
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.TouchController
import com.android.quickstep.inputconsumers.TaskbarUnstashInputConsumer

/**
 * A helper [TouchController] for [TaskbarDragLayerController], specifically to handle touch events
 * to stash Transient Taskbar. There are two cases to handle:
 * - A touch outside of Transient Taskbar bounds will immediately stash on [MotionEvent.ACTION_DOWN]
 *   or [MotionEvent.ACTION_OUTSIDE].
 * - Touches inside Transient Taskbar bounds will stash if it is detected as a swipe down gesture.
 *
 * Note: touches to *unstash* Taskbar are handled by [TaskbarUnstashInputConsumer].
 */
class TaskbarStashViaTouchController(val controllers: TaskbarControllers) : TouchController {

    private val activity: TaskbarActivityContext = controllers.taskbarActivityContext
    private val enabled = DisplayController.isTransientTaskbar(activity)
    private val swipeDownDetector: SingleAxisSwipeDetector
    private val translationCallback = controllers.taskbarTranslationController.transitionCallback
    /** Interpolator to apply resistance as user swipes down to the bottom of the screen. */
    private val displacementInterpolator = LINEAR
    /** How far we can translate the TaskbarView before it's offscreen. */
    private val maxVisualDisplacement =
        activity.resources.getDimensionPixelSize(R.dimen.transient_taskbar_bottom_margin).toFloat()
    /** How far the swipe could go, if user swiped from the very top of TaskbarView. */
    private val maxTouchDisplacement = maxVisualDisplacement + activity.deviceProfile.taskbarHeight
    private val touchDisplacementToStash =
        activity.resources.getDimensionPixelSize(R.dimen.taskbar_to_nav_threshold).toFloat()

    /** The height of the system gesture region, so we don't stash when touching down there. */
    private var gestureHeightYThreshold = 0f

    init {
        updateGestureHeight()
        swipeDownDetector = SingleAxisSwipeDetector(activity, createSwipeListener(), VERTICAL)
        swipeDownDetector.setDetectableScrollConditions(DIRECTION_NEGATIVE, false)
    }

    fun updateGestureHeight() {
        if (!enabled) return

        val gestureHeight: Int =
            ResourceUtils.getNavbarSize(
                ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE,
                activity.resources
            )
        gestureHeightYThreshold = (activity.deviceProfile.heightPx - gestureHeight).toFloat()
    }

    private fun createSwipeListener() =
        object : SingleAxisSwipeDetector.Listener {
            private var lastDisplacement = 0f

            override fun onDragStart(start: Boolean, startDisplacement: Float) {}

            override fun onDrag(displacement: Float): Boolean {
                lastDisplacement = displacement
                if (displacement < 0) return false
                // Apply resistance so that the visual displacement doesn't go beyond the screen.
                translationCallback.onActionMove(
                    Utilities.mapToRange(
                        displacement,
                        0f,
                        maxTouchDisplacement,
                        0f,
                        maxVisualDisplacement,
                        displacementInterpolator
                    )
                )
                return false
            }

            override fun onDragEnd(velocity: Float) {
                val isFlingDown = swipeDownDetector.isFling(velocity) && velocity > 0
                val isSignificantDistance = lastDisplacement > touchDisplacementToStash
                if (isFlingDown || isSignificantDistance) {
                    // Successfully triggered stash.
                    controllers.taskbarStashController.updateAndAnimateTransientTaskbar(true)
                }
                translationCallback.onActionEnd()
                swipeDownDetector.finishedScrolling()
            }
        }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!enabled) {
            return false
        }
        val bubbleControllers = controllers.bubbleControllers.orElse(null)
        if (bubbleControllers != null && bubbleControllers.bubbleBarViewController.isExpanded) {
            return false
        }
        if (
            (bubbleControllers == null || bubbleControllers.bubbleStashController.isStashed) &&
                controllers.taskbarStashController.isStashed
        ) {
            return false
        }

        val screenCoordinatesEv = MotionEvent.obtain(ev)
        screenCoordinatesEv.setLocation(ev.rawX, ev.rawY)
        if (ev.action == MotionEvent.ACTION_OUTSIDE) {
            controllers.taskbarStashController.updateAndAnimateTransientTaskbar(true)
        } else if (controllers.taskbarViewController.isEventOverAnyItem(screenCoordinatesEv)) {
            swipeDownDetector.onTouchEvent(ev)
            if (swipeDownDetector.isDraggingState) {
                return true
            }
        } else if (ev.action == MotionEvent.ACTION_DOWN) {
            val isDownOnBubbleBar =
                (bubbleControllers != null &&
                    bubbleControllers.bubbleBarViewController.isEventOverAnyItem(
                        screenCoordinatesEv
                    ))
            if (!isDownOnBubbleBar && screenCoordinatesEv.y < gestureHeightYThreshold) {
                controllers.taskbarStashController.updateAndAnimateTransientTaskbar(true)
            }
        }
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent) = swipeDownDetector.onTouchEvent(ev)
}
