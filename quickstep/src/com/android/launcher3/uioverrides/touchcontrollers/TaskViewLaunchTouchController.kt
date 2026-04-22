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
import com.android.app.animation.Interpolators.ZOOM_IN
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.Utilities.EDGE_NAV_BAR
import com.android.launcher3.Utilities.boundToRange
import com.android.launcher3.Utilities.debugLog
import com.android.launcher3.Utilities.isRtl
import com.android.launcher3.anim.AnimatorPlaybackController
import com.android.launcher3.touch.BaseSwipeDetector
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.FlingBlockCheck
import com.android.launcher3.util.TouchController
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import kotlin.math.abs

/** Touch controller which handles dragging task view cards for launch. */
class TaskViewLaunchTouchController<CONTAINER>(
    private val container: CONTAINER,
    private val taskViewRecentsTouchContext: TaskViewRecentsTouchContext,
) : TouchController, SingleAxisSwipeDetector.Listener where
CONTAINER : Context,
CONTAINER : RecentsViewContainer {
    private val tempRect = Rect()
    private val flingBlockCheck = FlingBlockCheck()
    private val recentsView: RecentsView<*, *> = container.getOverviewPanel()
    private val detector: SingleAxisSwipeDetector =
        SingleAxisSwipeDetector(
            container as Context,
            this,
            recentsView.pagedOrientationHandler.upDownSwipeDirection,
        )
    private val isRtl = isRtl(container.resources)
    private val downDirection = recentsView.pagedOrientationHandler.getDownDirection(isRtl)

    private var taskBeingDragged: TaskView? = null
    private var launchEndDisplacement: Float = 0f
    private var playbackController: AnimatorPlaybackController? = null
    private var verticalFactor: Int = 0
    private var canInterceptTouch = false

    private fun canTaskLaunchTaskView(taskView: TaskView?) =
        taskView != null &&
            taskView === recentsView.currentPageTaskView &&
            DisplayController.getNavigationMode(container).hasGestures &&
            (!recentsView.showAsGrid() || taskView.isLargeTile) &&
            recentsView.isTaskInExpectedScrollPosition(taskView)

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

            // Do not allow launch while recents is scrolling.
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
        if (
            (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) &&
                playbackController == null
        ) {
            clearState()
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            canInterceptTouch = onActionDown(ev)
            if (!canInterceptTouch) {
                clearState()
                return false
            }
        }
        // Ignore other actions if touch intercepting has not been enabled in an ACTION_DOWN event.
        if (!canInterceptTouch) {
            return false
        }
        onControllerTouchEvent(ev)
        val downDirectionIsNegative = downDirection == SingleAxisSwipeDetector.DIRECTION_NEGATIVE
        val wasInitialTouchDown =
            (downDirectionIsNegative && !detector.wasInitialTouchPositive()) ||
                (!downDirectionIsNegative && detector.wasInitialTouchPositive())
        return detector.isDraggingState && wasInitialTouchDown
    }

    override fun onControllerTouchEvent(ev: MotionEvent) = detector.onTouchEvent(ev)

    private fun onActionDown(ev: MotionEvent): Boolean {
        if (!canInterceptTouch(ev)) {
            return false
        }
        taskBeingDragged =
            recentsView.taskViews
                .firstOrNull {
                    recentsView.isTaskViewVisible(it) && container.dragLayer.isEventOverView(it, ev)
                }
                ?.also {
                    verticalFactor =
                        recentsView.pagedOrientationHandler.getTaskDragDisplacementFactor(isRtl)
                }
        if (!canTaskLaunchTaskView(taskBeingDragged)) {
            debugLog(TAG, "Not intercepting touch, task cannot be launched.")
            return false
        }
        detector.setDetectableScrollConditions(downDirection, /* ignoreSlop= */ false)
        return true
    }

    override fun onDragStart(start: Boolean, startDisplacement: Float) {
        val taskBeingDragged = taskBeingDragged ?: return
        debugLog(TAG, "Handling touch event.")

        val secondaryLayerDimension: Int =
            recentsView.pagedOrientationHandler.getSecondaryDimension(container.getDragLayer())
        val maxDuration = 2L * secondaryLayerDimension
        recentsView.clearPendingAnimation()
        val pendingAnimation =
            recentsView.createTaskLaunchAnimation(taskBeingDragged, maxDuration, ZOOM_IN)
        // Since the thumbnail is what is filling the screen, based the end displacement on it.
        taskBeingDragged.getThumbnailBounds(tempRect, /* relativeToDragLayer= */ true)
        launchEndDisplacement =
            recentsView.pagedOrientationHandler
                .getTaskLaunchLength(secondaryLayerDimension, tempRect)
                .toFloat() * verticalFactor
        playbackController =
            pendingAnimation.createPlaybackController()?.apply {
                taskViewRecentsTouchContext.onUserControlledAnimationCreated(this)
                dispatchOnStart()
            }
    }

    override fun onDrag(displacement: Float): Boolean {
        playbackController?.setPlayFraction(
            boundToRange(displacement / launchEndDisplacement, 0f, 1f)
        )
        return true
    }

    override fun onDragEnd(velocity: Float) {
        val playbackController = playbackController ?: return

        val isBeyondLaunchThreshold =
            abs(playbackController.progressFraction) > abs(LAUNCH_THRESHOLD_FRACTION)
        val velocityIsNegative = !recentsView.pagedOrientationHandler.isGoingUp(velocity, isRtl)
        val isFlingingTowardsLaunch = detector.isFling(velocity) && velocityIsNegative
        val isFlingingTowardsRestState = detector.isFling(velocity) && !velocityIsNegative
        val isLaunching =
            isFlingingTowardsLaunch || (isBeyondLaunchThreshold && !isFlingingTowardsRestState)

        val progress = playbackController.progressFraction
        var animationDuration =
            BaseSwipeDetector.calculateDuration(
                velocity,
                if (isLaunching) (1 - progress) else progress,
            )
        if (detector.isFling(velocity) && flingBlockCheck.isBlocked && !isLaunching) {
            animationDuration *= LauncherAnimUtils.blockedFlingDurationFactor(velocity).toLong()
        }

        playbackController.setEndAction(this::clearState)
        playbackController.startWithVelocity(
            container,
            isLaunching,
            velocity,
            launchEndDisplacement,
            animationDuration,
        )
    }

    private fun clearState() {
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
        taskBeingDragged = null
        playbackController = null
    }

    companion object {
        private const val TAG = "TaskViewLaunchTouchController"
        private const val LAUNCH_THRESHOLD_FRACTION: Float = 0.5f
    }
}
