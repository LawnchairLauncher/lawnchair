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

package com.android.quickstep.views

import android.os.VibrationAttributes
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.Flags.enableGridOnlyOverview
import com.android.launcher3.R
import com.android.launcher3.Utilities.boundToRange
import com.android.launcher3.util.DynamicResource
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.quickstep.util.TaskGridNavHelper
import com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.InteractionProperties
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Helper class for [RecentsView]. This util class contains refactored and extracted functions from
 * RecentsView related to TaskView dismissal.
 */
class RecentsDismissUtils(private val recentsView: RecentsView<*, *>) {

    /**
     * Creates the spring animations which run when a dragged task view in overview is released.
     *
     * <p>When a task dismiss is cancelled, the task will return to its original position via a
     * spring animation. As it passes the threshold of its settling state, its neighbors will spring
     * in response to the perceived impact of the settling task.
     */
    fun createTaskDismissSettlingSpringAnimation(
        draggedTaskView: TaskView?,
        velocity: Float,
        isDismissing: Boolean,
        dismissLength: Int,
        onEndRunnable: () -> Unit,
    ): SpringAnimation? {
        draggedTaskView ?: return null
        val taskDismissFloatProperty =
            FloatPropertyCompat.createFloatPropertyCompat(
                draggedTaskView.secondaryDismissTranslationProperty
            )
        val minVelocity =
            recentsView.pagedOrientationHandler.getSecondaryDimension(draggedTaskView).toFloat()
        val startVelocity = abs(velocity).coerceAtLeast(minVelocity) * velocity.sign
        // Animate dragged task towards dismissal or rest state.
        val draggedTaskViewSpringAnimation =
            SpringAnimation(draggedTaskView, taskDismissFloatProperty)
                .setSpring(createExpressiveDismissSpringForce())
                .setStartVelocity(startVelocity)
                .addUpdateListener { animation, value, _ ->
                    if (isDismissing && abs(value) >= abs(dismissLength)) {
                        animation.cancel()
                    } else if (draggedTaskView.isRunningTask && recentsView.enableDrawingLiveTile) {
                        recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                            remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value =
                                taskDismissFloatProperty.getValue(draggedTaskView)
                        }
                        recentsView.redrawLiveTile()
                    }
                }
                .addEndListener { _, _, _, _ ->
                    if (isDismissing) {
                        if (!recentsView.showAsGrid() || enableGridOnlyOverview()) {
                            runTaskGridReflowSpringAnimation(
                                draggedTaskView,
                                getDismissedTaskGapForReflow(draggedTaskView),
                                onEndRunnable,
                            )
                        } else {
                            recentsView.dismissTaskView(
                                draggedTaskView,
                                /* animateTaskView = */ false,
                                /* removeTask = */ true,
                            )
                            onEndRunnable()
                        }
                    } else {
                        recentsView.onDismissAnimationEnds()
                        onEndRunnable()
                    }
                }
        if (!isDismissing) {
            addNeighborSettlingSpringAnimations(
                draggedTaskView,
                draggedTaskViewSpringAnimation,
                driverProgressThreshold = 0f,
                isSpringDirectionVertical = true,
                minVelocity = startVelocity,
            )
        }
        return draggedTaskViewSpringAnimation
    }

    private fun addNeighborSettlingSpringAnimations(
        draggedTaskView: TaskView,
        springAnimationDriver: SpringAnimation,
        tasksToExclude: List<TaskView> = emptyList(),
        driverProgressThreshold: Float,
        isSpringDirectionVertical: Boolean,
        minVelocity: Float,
    ) {
        // Empty spring animation exists for conditional start, and to drive neighboring springs.
        val neighborsToSettle =
            SpringAnimation(FloatValueHolder()).setSpring(createExpressiveDismissSpringForce())

        // Add tasks before dragged index, fanning out from the dragged task.
        // The order they are added matters, as each spring drives the next.
        var previousNeighbor = neighborsToSettle
        getTasksOffsetPairAdjacentToDraggedTask(draggedTaskView, towardsStart = true)
            .filter { (taskView, _) -> !tasksToExclude.contains(taskView) }
            .forEach { (taskView, offset) ->
                previousNeighbor =
                    createNeighboringTaskViewSpringAnimation(
                        taskView,
                        offset * ADDITIONAL_DISMISS_DAMPING_RATIO,
                        previousNeighbor,
                        isSpringDirectionVertical,
                    )
            }
        // Add tasks after dragged index, fanning out from the dragged task.
        // The order they are added matters, as each spring drives the next.
        previousNeighbor = neighborsToSettle
        getTasksOffsetPairAdjacentToDraggedTask(draggedTaskView, towardsStart = false)
            .filter { (taskView, _) -> !tasksToExclude.contains(taskView) }
            .forEach { (taskView, offset) ->
                previousNeighbor =
                    createNeighboringTaskViewSpringAnimation(
                        taskView,
                        offset * ADDITIONAL_DISMISS_DAMPING_RATIO,
                        previousNeighbor,
                        isSpringDirectionVertical,
                    )
            }

        val isCurrentDisplacementAboveOrigin =
            recentsView.pagedOrientationHandler.isGoingUp(
                draggedTaskView.secondaryDismissTranslationProperty.get(draggedTaskView),
                recentsView.isRtl,
            )
        addThresholdSpringAnimationTrigger(
            springAnimationDriver,
            progressThreshold = driverProgressThreshold,
            neighborsToSettle,
            isCurrentDisplacementAboveOrigin,
            minVelocity,
        )
    }

    /** As spring passes threshold for the first time, run conditional spring with velocity. */
    private fun addThresholdSpringAnimationTrigger(
        springAnimationDriver: SpringAnimation,
        progressThreshold: Float,
        conditionalSpring: SpringAnimation,
        isCurrentDisplacementAboveOrigin: Boolean,
        minVelocity: Float,
    ) {
        val runSettlingAtVelocity = { velocity: Float ->
            conditionalSpring.setStartVelocity(velocity).animateToFinalPosition(0f)
            playDismissSettlingHaptic(velocity)
        }
        if (isCurrentDisplacementAboveOrigin) {
            var lastPosition = 0f
            var startSettling = false
            springAnimationDriver.addUpdateListener { _, value, velocity ->
                // We do not compare to the threshold directly, as the update listener
                // does not necessarily hit every value. Do not check again once it has started
                // settling, as a spring can bounce past the end value multiple times.
                if (startSettling) return@addUpdateListener
                if (
                    lastPosition < progressThreshold && value >= progressThreshold ||
                        lastPosition > progressThreshold && value <= progressThreshold
                ) {
                    startSettling = true
                }
                lastPosition = value
                if (startSettling) {
                    runSettlingAtVelocity(velocity)
                }
            }
        } else {
            // Run settling animations immediately when displacement is already below settled state.
            runSettlingAtVelocity(minVelocity)
        }
    }

    /**
     * Gets pairs of (TaskView, offset) adjacent the dragged task in visual order.
     *
     * <p>Gets tasks either before or after the dragged task along with their offset from it. The
     * offset is the distance between indices for carousels, or distance between columns for grids.
     */
    private fun getTasksOffsetPairAdjacentToDraggedTask(
        draggedTaskView: TaskView,
        towardsStart: Boolean,
    ): Sequence<Pair<TaskView, Int>> {
        if (recentsView.showAsGrid()) {
            val taskGridNavHelper =
                TaskGridNavHelper(
                    recentsView.mUtils.getTopRowIdArray(),
                    recentsView.mUtils.getBottomRowIdArray(),
                    recentsView.mUtils.getLargeTaskViewIds(),
                    hasAddDesktopButton = false,
                )
            return taskGridNavHelper
                .gridTaskViewIdOffsetPairInTabOrderSequence(
                    draggedTaskView.taskViewId,
                    towardsStart,
                )
                .mapNotNull { (taskViewId, columnOffset) ->
                    recentsView.getTaskViewFromTaskViewId(taskViewId)?.let { taskView ->
                        Pair(taskView, columnOffset)
                    }
                }
        } else {
            val taskViewList = recentsView.mUtils.taskViews.toList()
            val draggedTaskViewIndex = taskViewList.indexOf(draggedTaskView)

            return if (towardsStart) {
                taskViewList
                    .take(draggedTaskViewIndex)
                    .reversed()
                    .mapIndexed { index, taskView -> Pair(taskView, index + 1) }
                    .asSequence()
            } else {
                taskViewList
                    .takeLast(taskViewList.size - draggedTaskViewIndex - 1)
                    .mapIndexed { index, taskView -> Pair(taskView, index + 1) }
                    .asSequence()
            }
        }
    }

    /** Creates a neighboring task view spring, driven by the spring of its neighbor. */
    private fun createNeighboringTaskViewSpringAnimation(
        taskView: TaskView,
        dampingOffsetRatio: Float,
        previousNeighborSpringAnimation: SpringAnimation,
        springingDirectionVertical: Boolean,
    ): SpringAnimation {
        val springProperty =
            if (springingDirectionVertical) taskView.secondaryDismissTranslationProperty
            else taskView.primaryDismissTranslationProperty
        val neighboringTaskViewSpringAnimation =
            SpringAnimation(taskView, FloatPropertyCompat.createFloatPropertyCompat(springProperty))
                .setSpring(createExpressiveDismissSpringForce(dampingOffsetRatio))
        // Update live tile on spring animation.
        if (taskView.isRunningTask && recentsView.enableDrawingLiveTile) {
            neighboringTaskViewSpringAnimation.addUpdateListener { _, _, _ ->
                recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                    val taskTranslation =
                        if (springingDirectionVertical) {
                            remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation
                        } else {
                            remoteTargetHandle.taskViewSimulator.taskPrimaryTranslation
                        }
                    taskTranslation.value = springProperty.get(taskView)
                }
                recentsView.redrawLiveTile()
            }
        }
        // Drive current neighbor's spring with the previous neighbor's.
        previousNeighborSpringAnimation.addUpdateListener { _, value, _ ->
            neighboringTaskViewSpringAnimation.animateToFinalPosition(value)
        }
        return neighboringTaskViewSpringAnimation
    }

    private fun createExpressiveDismissSpringForce(dampingRatioOffset: Float = 0f): SpringForce {
        val resourceProvider = DynamicResource.provider(recentsView.mContainer)
        return SpringForce()
            .setDampingRatio(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_task_trans_y_damping_ratio) +
                    dampingRatioOffset
            )
            .setStiffness(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_task_trans_y_stiffness)
            )
    }

    private fun createExpressiveGridReflowSpringForce(
        finalPosition: Float = Float.MAX_VALUE
    ): SpringForce {
        val resourceProvider = DynamicResource.provider(recentsView.mContainer)
        return SpringForce(finalPosition)
            .setDampingRatio(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_task_trans_x_damping_ratio)
            )
            .setStiffness(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_task_trans_x_stiffness)
            )
    }

    /**
     * Plays a haptic as the dragged task view settles back into its rest state.
     *
     * <p>Haptic intensity is proportional to velocity.
     */
    private fun playDismissSettlingHaptic(velocity: Float) {
        val maxDismissSettlingVelocity =
            recentsView.pagedOrientationHandler.getSecondaryDimension(recentsView)
        MSDLPlayerWrapper.INSTANCE.get(recentsView.context)
            ?.playToken(
                MSDLToken.CANCEL,
                InteractionProperties.DynamicVibrationScale(
                    boundToRange(abs(velocity) / maxDismissSettlingVelocity, 0f, 1f),
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                        .setFlags(VibrationAttributes.FLAG_PIPELINED_EFFECT)
                        .build(),
                ),
            )
    }

    /** Animates RecentsView's scale to the provided value, using spring animations. */
    fun animateRecentsScale(scale: Float): SpringAnimation {
        val resourceProvider = DynamicResource.provider(recentsView.mContainer)
        val dampingRatio = resourceProvider.getFloat(R.dimen.swipe_up_rect_scale_damping_ratio)
        val stiffness = resourceProvider.getFloat(R.dimen.swipe_up_rect_scale_stiffness)

        // Spring which sets the Recents scale on update. This is needed, as the SpringAnimation
        // struggles to animate small values like changing recents scale from 0.9 to 1. So
        // we animate over a larger range (e.g. 900 to 1000) and convert back to the required value.
        // (This is instead of converting RECENTS_SCALE_PROPERTY to a FloatPropertyCompat and
        // animating it directly via springs.)
        val initialRecentsScaleSpringValue =
            RECENTS_SCALE_SPRING_MULTIPLIER * RECENTS_SCALE_PROPERTY.get(recentsView)
        return SpringAnimation(FloatValueHolder(initialRecentsScaleSpringValue))
            .setSpring(
                SpringForce(initialRecentsScaleSpringValue)
                    .setDampingRatio(dampingRatio)
                    .setStiffness(stiffness)
            )
            .addUpdateListener { _, value, _ ->
                RECENTS_SCALE_PROPERTY.setValue(
                    recentsView,
                    value / RECENTS_SCALE_SPRING_MULTIPLIER,
                )
            }
            .apply { animateToFinalPosition(RECENTS_SCALE_SPRING_MULTIPLIER * scale) }
    }

    /** Animates with springs the TaskViews beyond the dismissed task to fill the gap it left. */
    private fun runTaskGridReflowSpringAnimation(
        dismissedTaskView: TaskView,
        dismissedTaskGap: Float,
        onEndRunnable: () -> Unit,
    ) {
        // Empty spring animation exists for conditional start, and to drive neighboring springs.
        val springAnimationDriver =
            SpringAnimation(FloatValueHolder())
                .setSpring(createExpressiveGridReflowSpringForce(finalPosition = dismissedTaskGap))
        val towardsStart = if (recentsView.isRtl) dismissedTaskGap < 0 else dismissedTaskGap > 0

        var tasksToReflow: List<TaskView>
        // Build the chains of Spring Animations
        when {
            !recentsView.showAsGrid() -> {
                tasksToReflow =
                    getTasksToReflow(
                        recentsView.mUtils.taskViews.toList(),
                        dismissedTaskView,
                        towardsStart,
                    )
                buildDismissReflowSpringAnimationChain(
                    tasksToReflow,
                    dismissedTaskGap,
                    previousSpring = springAnimationDriver,
                )
            }
            dismissedTaskView.isLargeTile -> {
                tasksToReflow =
                    getTasksToReflow(
                        recentsView.mUtils.getLargeTaskViews(),
                        dismissedTaskView,
                        towardsStart,
                    )
                val lastSpringAnimation =
                    buildDismissReflowSpringAnimationChain(
                        tasksToReflow,
                        dismissedTaskGap,
                        previousSpring = springAnimationDriver,
                    )
                // Add all top and bottom grid tasks when animating towards the end of the grid.
                if (!towardsStart) {
                    tasksToReflow += recentsView.mUtils.getTopRowTaskViews()
                    tasksToReflow += recentsView.mUtils.getBottomRowTaskViews()
                    buildDismissReflowSpringAnimationChain(
                        recentsView.mUtils.getTopRowTaskViews(),
                        dismissedTaskGap,
                        previousSpring = lastSpringAnimation,
                    )
                    buildDismissReflowSpringAnimationChain(
                        recentsView.mUtils.getBottomRowTaskViews(),
                        dismissedTaskGap,
                        previousSpring = lastSpringAnimation,
                    )
                }
            }
            recentsView.isOnGridBottomRow(dismissedTaskView) -> {
                tasksToReflow =
                    getTasksToReflow(
                        recentsView.mUtils.getBottomRowTaskViews(),
                        dismissedTaskView,
                        towardsStart,
                    )
                buildDismissReflowSpringAnimationChain(
                    tasksToReflow,
                    dismissedTaskGap,
                    previousSpring = springAnimationDriver,
                )
            }
            else -> {
                tasksToReflow =
                    getTasksToReflow(
                        recentsView.mUtils.getTopRowTaskViews(),
                        dismissedTaskView,
                        towardsStart,
                    )
                buildDismissReflowSpringAnimationChain(
                    tasksToReflow,
                    dismissedTaskGap,
                    previousSpring = springAnimationDriver,
                )
            }
        }

        if (tasksToReflow.isNotEmpty()) {
            addNeighborSettlingSpringAnimations(
                dismissedTaskView,
                springAnimationDriver,
                tasksToExclude = tasksToReflow,
                driverProgressThreshold = dismissedTaskGap,
                isSpringDirectionVertical = false,
                minVelocity = 0f,
            )
        } else {
            springAnimationDriver.addEndListener { _, _, _, _ ->
                // Play the same haptic as when neighbors spring into place.
                MSDLPlayerWrapper.INSTANCE.get(recentsView.context)?.playToken(MSDLToken.CANCEL)
            }
        }

        // Start animations and remove the dismissed task at the end, dismiss immediately if no
        // neighboring tasks exist.
        val runGridEndAnimationAndRelayout = {
            recentsView.expressiveDismissTaskView(dismissedTaskView, onEndRunnable)
        }
        springAnimationDriver?.apply {
            addEndListener { _, _, _, _ -> runGridEndAnimationAndRelayout() }
            animateToFinalPosition(dismissedTaskGap)
        } ?: runGridEndAnimationAndRelayout()
    }

    private fun getDismissedTaskGapForReflow(dismissedTaskView: TaskView): Float {
        val screenStart = recentsView.pagedOrientationHandler.getPrimaryScroll(recentsView)
        val screenEnd =
            screenStart + recentsView.pagedOrientationHandler.getMeasuredSize(recentsView)
        val taskStart =
            recentsView.pagedOrientationHandler.getChildStart(dismissedTaskView) +
                dismissedTaskView.getOffsetAdjustment(recentsView.showAsGrid())
        val taskSize =
            recentsView.pagedOrientationHandler.getMeasuredSize(dismissedTaskView) *
                dismissedTaskView.getSizeAdjustment(recentsView.showAsFullscreen())
        val taskEnd = taskStart + taskSize

        val isDismissedTaskBeyondEndOfScreen =
            if (recentsView.isRtl) taskEnd > screenEnd else taskStart < screenStart
        if (
            dismissedTaskView.isLargeTile &&
                isDismissedTaskBeyondEndOfScreen &&
                recentsView.mUtils.getLargeTileCount() == 1
        ) {
            return with(recentsView) {
                    pagedOrientationHandler.getPrimaryScroll(this) -
                        getScrollForPage(indexOfChild(mUtils.getFirstNonDesktopTaskView()))
                }
                .toFloat()
        }

        // If current page is beyond last TaskView's index, use last TaskView to calculate offset.
        val lastTaskViewIndex = recentsView.indexOfChild(recentsView.mUtils.getLastTaskView())
        val currentPage = recentsView.currentPage.coerceAtMost(lastTaskViewIndex)
        val dismissHorizontalFactor =
            when {
                dismissedTaskView.isGridTask -> 1f
                currentPage == lastTaskViewIndex -> -1f
                recentsView.indexOfChild(dismissedTaskView) < currentPage -> -1f
                else -> 1f
            } * (if (recentsView.isRtl) 1f else -1f)

        return (recentsView.pagedOrientationHandler.getPrimarySize(dismissedTaskView) +
            recentsView.pageSpacing) * dismissHorizontalFactor
    }

    private fun getTasksToReflow(
        taskViews: List<TaskView>,
        dismissedTaskView: TaskView,
        towardsStart: Boolean,
    ): List<TaskView> {
        val dismissedTaskViewIndex = taskViews.indexOf(dismissedTaskView)
        if (dismissedTaskViewIndex == -1) {
            return emptyList()
        }
        return if (towardsStart) {
            taskViews.take(dismissedTaskViewIndex).reversed()
        } else {
            taskViews.takeLast(taskViews.size - dismissedTaskViewIndex - 1)
        }
    }

    private fun willTaskBeVisibleAfterDismiss(taskView: TaskView, taskTranslation: Int): Boolean {
        val screenStart = recentsView.pagedOrientationHandler.getPrimaryScroll(recentsView)
        val screenEnd =
            screenStart + recentsView.pagedOrientationHandler.getMeasuredSize(recentsView)
        return recentsView.isTaskViewWithinBounds(
            taskView,
            screenStart,
            screenEnd,
            /* taskViewTranslation = */ taskTranslation,
        )
    }

    /** Builds a chain of spring animations for task reflow after dismissal */
    private fun buildDismissReflowSpringAnimationChain(
        taskViews: Iterable<TaskView>,
        dismissedTaskGap: Float,
        previousSpring: SpringAnimation,
    ): SpringAnimation {
        var lastTaskViewSpring = previousSpring
        taskViews
            .filter { taskView ->
                willTaskBeVisibleAfterDismiss(taskView, dismissedTaskGap.roundToInt())
            }
            .forEach { taskView ->
                val taskViewSpringAnimation =
                    SpringAnimation(
                            taskView,
                            FloatPropertyCompat.createFloatPropertyCompat(
                                taskView.primaryDismissTranslationProperty
                            ),
                        )
                        .setSpring(createExpressiveGridReflowSpringForce(dismissedTaskGap))
                // Update live tile on spring animation.
                if (taskView.isRunningTask && recentsView.enableDrawingLiveTile) {
                    taskViewSpringAnimation.addUpdateListener { _, _, _ ->
                        recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                            remoteTargetHandle.taskViewSimulator.taskPrimaryTranslation.value =
                                taskView.primaryDismissTranslationProperty.get(taskView)
                        }
                        recentsView.redrawLiveTile()
                    }
                }
                lastTaskViewSpring.addUpdateListener { _, value, _ ->
                    taskViewSpringAnimation.animateToFinalPosition(value)
                }
                lastTaskViewSpring = taskViewSpringAnimation
            }
        return lastTaskViewSpring
    }

    private companion object {
        // The additional damping to apply to tasks further from the dismissed task.
        private const val ADDITIONAL_DISMISS_DAMPING_RATIO = 0.15f
        private const val RECENTS_SCALE_SPRING_MULTIPLIER = 1000f
    }
}
