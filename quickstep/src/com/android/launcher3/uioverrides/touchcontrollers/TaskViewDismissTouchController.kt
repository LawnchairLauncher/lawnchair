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
package com.android.launcher3.uioverrides.touchcontrollers

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import androidx.dynamicanimation.animation.SpringAnimation
import com.android.app.animation.Interpolators.DECELERATE
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.Utilities.EDGE_NAV_BAR
import com.android.launcher3.Utilities.boundToRange
import com.android.launcher3.Utilities.debugLog
import com.android.launcher3.Utilities.isRtl
import com.android.launcher3.Utilities.mapToRange
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.util.TouchController
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import com.google.android.msdl.data.model.MSDLToken
import kotlin.math.abs

/** Touch controller for handling task view card dismiss swipes */
class TaskViewDismissTouchController<CONTAINER>(
    private val container: CONTAINER,
    private val taskViewRecentsTouchContext: TaskViewRecentsTouchContext,
) : TouchController, SingleAxisSwipeDetector.Listener where
CONTAINER : Context,
CONTAINER : RecentsViewContainer {
    private val recentsView: RecentsView<*, *> = container.getOverviewPanel()
    private val detector: SingleAxisSwipeDetector =
        SingleAxisSwipeDetector(
            container as Context,
            this,
            recentsView.pagedOrientationHandler.upDownSwipeDirection,
        )
    private val isRtl = isRtl(container.resources)
    private val upDirection: Int = recentsView.pagedOrientationHandler.getUpDirection(isRtl)

    private val tempTaskThumbnailBounds = Rect()

    private var taskBeingDragged: TaskView? = null
    private var springAnimation: SpringAnimation? = null
    private var dismissLength: Int = 0
    private var verticalFactor: Int = 0
    private var hasDismissThresholdHapticRun = false
    private var initialDisplacement: Float = 0f
    private var recentsScaleAnimation: SpringAnimation? = null
    private var isBlockedDuringDismissal = false

    private fun canInterceptTouch(ev: MotionEvent): Boolean =
        when {
            // Don't intercept swipes on the nav bar, as user might be trying to go home during a
            // task dismiss animation.
            (ev.edgeFlags and EDGE_NAV_BAR) != 0 -> {
                debugLog(TAG, "Not intercepting edge swipe on nav bar.")
                false
            }

            // Floating views that a TouchController should not try to intercept touches from.
            AbstractFloatingView.getTopOpenViewWithType(
                container,
                AbstractFloatingView.TYPE_TOUCH_CONTROLLER_NO_INTERCEPT,
            ) != null -> {
                debugLog(TAG, "Not intercepting, open floating view blocking touch.")
                false
            }

            // Disable swiping if the task overlay is modal.
            taskViewRecentsTouchContext.isRecentsModal -> {
                debugLog(TAG, "Not intercepting touch in modal overlay.")
                false
            }

            else ->
                taskViewRecentsTouchContext.isRecentsInteractive.also { isRecentsInteractive ->
                    if (!isRecentsInteractive) {
                        debugLog(TAG, "Not intercepting touch, recents not interactive.")
                    }
                }
        }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if ((ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL)) {
            clearState()
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (!onActionDown(ev)) {
                return false
            }
        }

        onControllerTouchEvent(ev)
        val upDirectionIsPositive = upDirection == SingleAxisSwipeDetector.DIRECTION_POSITIVE
        val wasInitialTouchUp =
            (upDirectionIsPositive && detector.wasInitialTouchPositive()) ||
                (!upDirectionIsPositive && !detector.wasInitialTouchPositive())
        return detector.isDraggingState && wasInitialTouchUp
    }

    override fun onControllerTouchEvent(ev: MotionEvent?): Boolean = detector.onTouchEvent(ev)

    private fun onActionDown(ev: MotionEvent): Boolean {
        springAnimation?.cancel()
        recentsScaleAnimation?.cancel()
        if (!canInterceptTouch(ev)) {
            return false
        }
        taskBeingDragged =
            recentsView.taskViews
                .firstOrNull {
                    recentsView.isTaskViewVisible(it) && container.dragLayer.isEventOverView(it, ev)
                }
                ?.also {
                    val secondaryLayerDimension =
                        recentsView.pagedOrientationHandler.getSecondaryDimension(
                            container.dragLayer
                        )
                    // Dismiss length as bottom of task so it is fully off screen when dismissed.
                    it.getThumbnailBounds(tempTaskThumbnailBounds, relativeToDragLayer = true)
                    dismissLength =
                        recentsView.pagedOrientationHandler.getTaskDismissLength(
                            secondaryLayerDimension,
                            tempTaskThumbnailBounds,
                        )
                    verticalFactor =
                        recentsView.pagedOrientationHandler.getTaskDismissVerticalDirection()
                }
        detector.setDetectableScrollConditions(upDirection, /* ignoreSlop= */ false)
        return true
    }

    override fun onDragStart(start: Boolean, startDisplacement: Float) {
        if (isBlockedDuringDismissal) return
        val taskBeingDragged = taskBeingDragged ?: return
        debugLog(TAG, "Handling touch event.")

        initialDisplacement =
            taskBeingDragged.secondaryDismissTranslationProperty.get(taskBeingDragged)

        // Add a tiny bit of translation Z, so that it draws on top of other views. This is relevant
        // (e.g.) when we dismiss a task by sliding it upward: if there is a row of icons above, we
        // want the dragged task to stay above all other views.
        taskBeingDragged.translationZ = 0.1f
    }

    override fun onDrag(displacement: Float): Boolean {
        if (isBlockedDuringDismissal) return true
        val taskBeingDragged = taskBeingDragged ?: return false
        val currentDisplacement = displacement + initialDisplacement
        val boundedDisplacement =
            boundToRange(abs(currentDisplacement), 0f, dismissLength.toFloat())
        // When swiping below origin, allow slight undershoot to simulate resisting the movement.
        val totalDisplacement =
            if (recentsView.pagedOrientationHandler.isGoingUp(currentDisplacement, isRtl))
                boundedDisplacement * verticalFactor
            else
                mapToRange(
                    boundedDisplacement,
                    0f,
                    dismissLength.toFloat(),
                    0f,
                    container.resources.getDimension(R.dimen.task_dismiss_max_undershoot),
                    DECELERATE,
                ) * -verticalFactor
        taskBeingDragged.secondaryDismissTranslationProperty.setValue(
            taskBeingDragged,
            totalDisplacement,
        )
        if (taskBeingDragged.isRunningTask && recentsView.enableDrawingLiveTile) {
            recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value =
                    totalDisplacement
            }
            recentsView.redrawLiveTile()
        }
        val dismissFraction = displacement / (dismissLength * verticalFactor).toFloat()
        RECENTS_SCALE_PROPERTY.setValue(recentsView, getRecentsScale(dismissFraction))
        playDismissThresholdHaptic(displacement)
        return true
    }

    /**
     * Play a haptic to alert the user they have passed the dismiss threshold.
     *
     * <p>Check within a range of the threshold value, as the drag event does not necessarily happen
     * at the exact threshold's displacement.
     */
    private fun playDismissThresholdHaptic(displacement: Float) {
        val dismissThreshold = (DISMISS_THRESHOLD_FRACTION * dismissLength * verticalFactor)
        val inHapticRange =
            displacement >= (dismissThreshold - DISMISS_THRESHOLD_HAPTIC_RANGE) &&
                displacement <= (dismissThreshold + DISMISS_THRESHOLD_HAPTIC_RANGE)
        if (!inHapticRange) {
            hasDismissThresholdHapticRun = false
        } else if (!hasDismissThresholdHapticRun) {
            MSDLPlayerWrapper.INSTANCE.get(recentsView.context)
                .playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
            hasDismissThresholdHapticRun = true
        }
    }

    override fun onDragEnd(velocity: Float) {
        if (isBlockedDuringDismissal) return
        val taskBeingDragged = taskBeingDragged ?: return

        val currentDisplacement =
            taskBeingDragged.secondaryDismissTranslationProperty.get(taskBeingDragged)
        val isBeyondDismissThreshold =
            abs(currentDisplacement) > abs(DISMISS_THRESHOLD_FRACTION * dismissLength)
        val velocityIsGoingUp = recentsView.pagedOrientationHandler.isGoingUp(velocity, isRtl)
        val isFlingingTowardsDismiss = detector.isFling(velocity) && velocityIsGoingUp
        val isFlingingTowardsRestState = detector.isFling(velocity) && !velocityIsGoingUp
        val isDismissing =
            isFlingingTowardsDismiss || (isBeyondDismissThreshold && !isFlingingTowardsRestState)
        springAnimation =
            recentsView
                .createTaskDismissSettlingSpringAnimation(
                    taskBeingDragged,
                    velocity,
                    isDismissing,
                    dismissLength,
                    this::clearState,
                )
                .apply {
                    animateToFinalPosition(
                        if (isDismissing) (dismissLength * verticalFactor).toFloat() else 0f
                    )
                }
        isBlockedDuringDismissal = true
        recentsScaleAnimation =
            recentsView.animateRecentsScale(RECENTS_SCALE_DEFAULT).addEndListener { _, _, _, _ ->
                recentsScaleAnimation = null
            }
    }

    private fun clearState() {
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
        taskBeingDragged?.translationZ = 0f
        taskBeingDragged = null
        springAnimation = null
        isBlockedDuringDismissal = false
    }

    private fun getRecentsScale(dismissFraction: Float): Float {
        return when {
            // Do not scale recents when dragging below origin.
            dismissFraction <= 0 -> {
                RECENTS_SCALE_DEFAULT
            }
            // Initially scale recents as the drag begins, up to the first threshold.
            dismissFraction < RECENTS_SCALE_FIRST_THRESHOLD_FRACTION -> {
                mapToRange(
                    dismissFraction,
                    0f,
                    RECENTS_SCALE_FIRST_THRESHOLD_FRACTION,
                    RECENTS_SCALE_DEFAULT,
                    RECENTS_SCALE_ON_DISMISS_CANCEL,
                    LINEAR,
                )
            }
            // Keep scale consistent until dragging to the dismiss threshold.
            dismissFraction < RECENTS_SCALE_DISMISS_THRESHOLD_FRACTION -> {
                RECENTS_SCALE_ON_DISMISS_CANCEL
            }
            // Scale beyond the dismiss threshold again, to indicate dismiss will occur on release.
            dismissFraction < RECENTS_SCALE_SECOND_THRESHOLD_FRACTION -> {
                mapToRange(
                    dismissFraction,
                    RECENTS_SCALE_DISMISS_THRESHOLD_FRACTION,
                    RECENTS_SCALE_SECOND_THRESHOLD_FRACTION,
                    RECENTS_SCALE_ON_DISMISS_CANCEL,
                    RECENTS_SCALE_ON_DISMISS_SUCCESS,
                    LINEAR,
                )
            }
            // Keep scale beyond the dismiss threshold scaling consistent.
            else -> {
                RECENTS_SCALE_ON_DISMISS_SUCCESS
            }
        }
    }

    companion object {
        private const val TAG = "TaskViewDismissTouchController"

        private const val DISMISS_THRESHOLD_FRACTION = 0.5f
        private const val DISMISS_THRESHOLD_HAPTIC_RANGE = 10f

        private const val RECENTS_SCALE_ON_DISMISS_CANCEL = 0.9875f
        private const val RECENTS_SCALE_ON_DISMISS_SUCCESS = 0.975f
        private const val RECENTS_SCALE_DEFAULT = 1f
        private const val RECENTS_SCALE_FIRST_THRESHOLD_FRACTION = 0.2f
        private const val RECENTS_SCALE_DISMISS_THRESHOLD_FRACTION = 0.5f
        private const val RECENTS_SCALE_SECOND_THRESHOLD_FRACTION = 0.575f
    }
}
