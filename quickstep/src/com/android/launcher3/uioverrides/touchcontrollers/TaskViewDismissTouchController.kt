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
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.statemanager.StateManager.StateListener
import com.android.launcher3.statemanager.StatefulContainer
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.util.TouchController
import com.android.mechanics.spec.Breakpoint
import com.android.mechanics.spec.Breakpoint.Companion.maxLimit
import com.android.mechanics.spec.Breakpoint.Companion.minLimit
import com.android.mechanics.spec.BreakpointKey
import com.android.mechanics.spec.DirectionalMotionSpec
import com.android.mechanics.spec.Guarantee
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spring.SpringParameters
import com.android.mechanics.view.DistanceGestureContext
import com.android.mechanics.view.ViewMotionValue
import com.android.quickstep.views.RecentsDismissUtils
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import com.google.android.msdl.data.model.MSDLToken
import kotlin.math.abs
import kotlin.math.ceil

/** Touch controller for handling task view card dismiss swipes */
class TaskViewDismissTouchController<CONTAINER, T : BaseState<T>>(
    private val container: CONTAINER,
    private val taskViewRecentsTouchContext: TaskViewRecentsTouchContext,
) : TouchController, SingleAxisSwipeDetector.Listener where
CONTAINER : Context,
CONTAINER : RecentsViewContainer,
CONTAINER : StatefulContainer<T> {
    private val recentsView: RecentsView<*, *> = container.getOverviewPanel()
    private val detector: SingleAxisSwipeDetector =
        SingleAxisSwipeDetector(
            container as Context,
            this,
            recentsView.pagedOrientationHandler.upDownSwipeDirection,
        )
    private val isRtl = isRtl(container.resources)
    private val upDirection: Int = recentsView.pagedOrientationHandler.getUpDirection(isRtl)
    private val maxUndershoot =
        container.resources.getDimension(R.dimen.task_dismiss_max_undershoot)
    private val detachThreshold =
        container.resources.getDimension(R.dimen.task_dismiss_detach_threshold)
    private val stateListener =
        object : StateListener<T> {
            override fun onStateTransitionStart(toState: T) {
                springAnimation?.cancel()
                clearState()
            }
        }
    private val tempTaskThumbnailBounds = Rect()

    private var taskBeingDragged: TaskView? = null
    private var taskDragDisplacementValue: ViewMotionValue? = null
    private var springAnimation: RecentsDismissUtils.SpringSet? = null
    private var dismissLength: Int = 0
    private var verticalFactor: Int = 0
    private var hasDismissThresholdHapticRun = false
    private var initialDisplacement: Float = 0f
    private var recentsScaleAnimation: SpringAnimation? = null
    private var canInterceptTouch = false
    private var isDismissing = false

    init {
        container.getStateManager().addStateListener(stateListener)
    }

    override fun onTouchControllerDestroyed() {
        container.getStateManager().removeStateListener(stateListener)
    }

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

            // Do not allow dismiss while recents is scrolling.
            !recentsView.scroller.isFinished -> {
                debugLog(TAG, "Not intercepting touch, recents scrolling.")
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
        // On consecutive events, end animation early so user can dismiss next task.
        springAnimation?.speedUpSpringsToEnd()

        if ((ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL)) {
            clearState()
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            canInterceptTouch = onActionDown(ev)
            if (!canInterceptTouch) {
                return false
            }
        }
        // Ignore other actions if touch intercepting has not been enabled in an ACTION_DOWN event.
        if (!canInterceptTouch) {
            return false
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
        if (!canInterceptTouch(ev)) {
            return false
        }
        taskBeingDragged =
            recentsView.taskViews.firstOrNull {
                recentsView.isTaskViewVisible(it) && container.dragLayer.isEventOverView(it, ev)
            }
                // If event is not over a taskView, check if it would have been either over the
                // currently dismissing task being dragged, or over where the next task will be.
                ?: recentsView.taskViews.firstOrNull { taskView ->
                    if (!recentsView.isTaskViewVisible(taskView)) return@firstOrNull false
                    container.dragLayer.getDescendantRectRelativeToSelf(
                        taskView,
                        tempTaskThumbnailBounds,
                    )
                    if (taskView == taskBeingDragged && !isDismissing) {
                        val secondaryTranslation =
                            -taskView.secondaryDismissTranslationProperty.get(taskView).toInt()
                        recentsView.pagedOrientationHandler.extendRectForSecondaryTranslation(
                            tempTaskThumbnailBounds,
                            secondaryTranslation,
                        )
                    } else {
                        val primaryTranslation =
                            recentsView.taskViewsDismissPrimaryTranslations[taskView] ?: 0
                        recentsView.pagedOrientationHandler.extendRectForPrimaryTranslation(
                            tempTaskThumbnailBounds,
                            primaryTranslation,
                        )
                    }
                    tempTaskThumbnailBounds.contains(ev.x.toInt(), ev.y.toInt())
                }

        if (taskBeingDragged == null) {
            debugLog(TAG, "Not intercepting touch, null dragged task.")
            return false
        }
        val secondaryLayerDimension =
            recentsView.pagedOrientationHandler.getSecondaryDimension(container.dragLayer)
        // Dismiss length as bottom of task so it is fully off screen when dismissed.
        // Take into account the recents scale when fully zoomed out on dismiss.
        taskBeingDragged?.getThumbnailBounds(tempTaskThumbnailBounds, relativeToDragLayer = true)
        dismissLength =
            ceil(
                    recentsView.pagedOrientationHandler.getTaskDismissLength(
                        secondaryLayerDimension,
                        tempTaskThumbnailBounds,
                    ) / RECENTS_SCALE_ON_DISMISS_SUCCESS
                )
                .toInt()
        verticalFactor = recentsView.pagedOrientationHandler.getTaskDismissVerticalDirection()
        taskBeingDragged?.isBeingDraggedForDismissal = true

        detector.setDetectableScrollConditions(upDirection, /* ignoreSlop= */ false)
        return true
    }

    override fun onDragStart(start: Boolean, startDisplacement: Float) {
        val taskBeingDragged = taskBeingDragged ?: return
        debugLog(TAG, "Handling touch event.")

        initialDisplacement =
            taskBeingDragged.secondaryDismissTranslationProperty.get(taskBeingDragged)
        taskDragDisplacementValue =
            generateMotionValue(
                initialDisplacement,
                detachThreshold * verticalFactor,
                container.asContext(),
            ) { currentDisplacement ->
                taskBeingDragged.secondaryDismissTranslationProperty.setValue(
                    taskBeingDragged,
                    currentDisplacement,
                )
                if (taskBeingDragged.isRunningTask && recentsView.enableDrawingLiveTile) {
                    recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                        remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value =
                            currentDisplacement
                    }
                    recentsView.redrawLiveTile()
                }
            }

        // Add a tiny bit of translation Z, so that it draws on top of other views. This is relevant
        // (e.g.) when we dismiss a task by sliding it upward: if there is a row of icons above, we
        // want the dragged task to stay above all other views.
        taskBeingDragged.translationZ = 0.1f
    }

    override fun onDrag(displacement: Float): Boolean {
        taskBeingDragged ?: return false
        val currentDisplacement = displacement + initialDisplacement
        val boundedDisplacement =
            boundToRange(abs(currentDisplacement), 0f, dismissLength.toFloat())
        // When swiping below origin, allow slight undershoot to simulate resisting the movement.
        val isAboveOrigin =
            recentsView.pagedOrientationHandler.isGoingUp(currentDisplacement, isRtl)
        val totalDisplacement =
            if (isAboveOrigin) boundedDisplacement * verticalFactor
            else
                mapToRange(
                    boundedDisplacement,
                    0f,
                    dismissLength.toFloat(),
                    0f,
                    maxUndershoot,
                    DECELERATE,
                ) * -verticalFactor
        val dismissFraction = displacement / (dismissLength * verticalFactor).toFloat()
        taskDragDisplacementValue?.input = totalDisplacement
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
        val taskBeingDragged = taskBeingDragged ?: return
        taskDragDisplacementValue?.dispose()
        taskBeingDragged.isBeingDraggedForDismissal = false

        val currentDisplacement =
            taskBeingDragged.secondaryDismissTranslationProperty.get(taskBeingDragged)
        val isBeyondDismissThreshold =
            abs(currentDisplacement) > abs(DISMISS_THRESHOLD_FRACTION * dismissLength)
        val velocityIsGoingUp = recentsView.pagedOrientationHandler.isGoingUp(velocity, isRtl)
        val isFlingingTowardsDismiss = detector.isFling(velocity) && velocityIsGoingUp
        val isFlingingTowardsRestState = detector.isFling(velocity) && !velocityIsGoingUp
        isDismissing =
            isFlingingTowardsDismiss || (isBeyondDismissThreshold && !isFlingingTowardsRestState)
        val dismissThreshold = (DISMISS_THRESHOLD_FRACTION * dismissLength * verticalFactor).toInt()
        val finalPosition = if (isDismissing) (dismissLength * verticalFactor).toFloat() else 0f
        springAnimation =
            recentsView.runTaskDismissSettlingSpringAnimation(
                taskBeingDragged,
                isDismissing,
                RecentsDismissUtils.DismissedTaskData(
                    startVelocity = velocity,
                    dismissLength = dismissLength,
                    finalPosition = finalPosition,
                    dismissThreshold = dismissThreshold,
                ),
                /* shouldRemoveTaskView= */ isDismissing,
                /* isSplitSelection= */ false,
            )
        recentsScaleAnimation =
            recentsView.animateRecentsScale(RECENTS_SCALE_DEFAULT).addEndListener { _, _, _, _ ->
                recentsScaleAnimation = null
            }
    }

    private fun clearState() {
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
        taskBeingDragged?.resetViewTransforms()
        taskBeingDragged = null
        springAnimation = null
        taskDragDisplacementValue = null
        isDismissing = false
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

    private fun generateMotionValue(
        initialDisplacement: Float,
        detachThreshold: Float,
        context: Context,
        updateCallback: (Float) -> Unit,
    ): ViewMotionValue {
        val direction = if (initialDisplacement < 0) InputDirection.Max else InputDirection.Min
        val distanceGestureContext =
            DistanceGestureContext.create(context, initialDisplacement, direction)
        val viewMotionValue =
            ViewMotionValue(
                initialDisplacement,
                distanceGestureContext,
                generateMotionSpec(detachThreshold),
                label = "taskDismiss::displacement",
            )

        viewMotionValue.addUpdateCallback { motionValue -> updateCallback(motionValue.output) }
        return viewMotionValue
    }

    /** Motion spec for an initial magnetic detach. Track linearly otherwise. No reattach. */
    private fun generateMotionSpec(detachThreshold: Float): MotionSpec {
        val spring = SpringParameters(stiffness = 800f, dampingRatio = 0.95f)
        val detachKey = BreakpointKey("TaskDismiss::Detach")
        val breakpoints = mutableListOf<Breakpoint>()
        val mappings = mutableListOf<Mapping>()

        breakpoints.add(minLimit)
        mappings.add(Mapping.Identity)
        breakpoints.add(Breakpoint(detachKey, detachThreshold, spring, Guarantee.None))
        mappings.add(Mapping.Linear(MAGNETIC_DETACH_INTERPOLATION_FRACTION))
        breakpoints.add(maxLimit)

        return MotionSpec(DirectionalMotionSpec(breakpoints, mappings))
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

        private const val MAGNETIC_DETACH_INTERPOLATION_FRACTION = 0.35f
    }
}
