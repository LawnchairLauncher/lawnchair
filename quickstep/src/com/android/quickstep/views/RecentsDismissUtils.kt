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

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.view.View
import androidx.core.graphics.toRectF
import androidx.core.view.children
import androidx.core.view.contains
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.internal.jank.Cuj
import com.android.launcher3.PagedView
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.util.DynamicResource
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.util.DesksUtils.Companion.areMultiDesksFlagsEnabled
import com.android.quickstep.util.TaskGridNavHelper
import com.android.quickstep.util.isDefaultDisplay
import com.android.quickstep.util.isExternalDisplay
import com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY
import com.android.quickstep.views.RecentsViewUtils.OnDeskAddedListener
import com.android.quickstep.views.TaskView.Companion.GRID_END_TRANSLATION_X
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.util.concurrent.ListeningExecutorService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Helper class for [RecentsView]. This util class contains refactored and extracted functions from
 * RecentsView related to TaskView dismissal.
 */
class RecentsDismissUtils
@AssistedInject
constructor(
    @Assisted private val recentsView: RecentsView<*, *>,
    private val systemUiProxy: SystemUiProxy,
    @LightweightBackground(LightweightBackgroundPriority.UI)
    private val uiHelperExecutor: ListeningExecutorService,
    private val activityManagerWrapper: ActivityManagerWrapper,
    private val msdlPlayerWrapper: MSDLPlayerWrapper,
) {
    @AssistedFactory
    interface Factory {
        fun create(recentsView: RecentsView<*, *>): RecentsDismissUtils
    }

    /**
     * [OnDeskAddedListener] which launches the new desk right after it is created.
     *
     * This is mainly used for clearing all desks via the clear all button in the recent view or the
     * removal of the last task in a desk.
     */
    private val launchNewDeskListener =
        object : OnDeskAddedListener {
            override fun onDeskAdded(desktopTaskView: DesktopTaskView) {
                desktopTaskView.launchWithAnimation()
                recentsView.mUtils.removeOnDeskAddedListener(this)
            }
        }

    /**
     * Runs the default spring animation when a dismissed task view in overview is released.
     *
     * <p>When a task dismiss is cancelled, the task will return to its original position via a
     * spring animation. As it passes the threshold of its settling state, its neighbors will spring
     * in response to the perceived impact of the settling task.
     */
    fun createTaskDismissSpringAnimation(
        dismissedTaskView: TaskView?,
        shouldRemoveTaskView: Boolean,
        isSplitSelection: Boolean,
    ): SpringSet? {
        if (dismissedTaskView == null || isSplitSelection) {
            return createTaskDismissSpringAnimation(
                dismissedTaskView,
                isDismissing = true,
                DismissedTaskData(
                    startVelocity = 0f,
                    dismissLength = 0,
                    dismissThreshold = 0,
                    finalPosition = 0f,
                ),
                shouldRemoveTaskView,
                isSplitSelection,
            )
        }
        return createTaskDismissSpringAnimation(
            dismissedTaskView,
            isDismissing = true,
            getDefaultDismissedTaskData(dismissedTaskView),
            shouldRemoveTaskView,
            isSplitSelection,
        )
    }

    /**
     * Runs the spring animations when a dismissed task view in overview is released.
     *
     * <p>When a task dismiss is cancelled, the task will return to its original position via a
     * spring animation. As it passes the threshold of its settling state, its neighbors will spring
     * in response to the perceived impact of the settling task.
     */
    fun createTaskDismissSpringAnimation(
        dismissedTaskView: TaskView?,
        isDismissing: Boolean,
        dismissedTaskData: DismissedTaskData,
        shouldRemoveTaskView: Boolean,
        isSplitSelection: Boolean,
    ): SpringSet? {
        val gridEndData = getGridEndData(dismissedTaskView)
        val dismissedTaskSecondaryDimension =
            if (dismissedTaskView == null)
                recentsView.pagedOrientationHandler.getSecondarySize(
                    recentsView.mLastComputedTaskSize.toRectF()
                )
            else {
                recentsView.pagedOrientationHandler
                    .getSecondaryDimension(dismissedTaskView)
                    .toFloat()
            }
        val verticalFactor =
            recentsView.pagedOrientationHandler.getTaskDismissVerticalDirection().toFloat()
        val startVelocity =
            abs(dismissedTaskData.startVelocity).coerceAtLeast(dismissedTaskSecondaryDimension) *
                dismissedTaskData.startVelocity.sign

        // Spring that animates the dismissed task.
        val dismissedTaskViewSpring =
            if (isSplitSelection || dismissedTaskView == null) null
            else {
                createDismissedTaskViewSpringAnimation(
                    dismissedTaskView,
                    isDismissing,
                    DismissedTaskData(
                        startVelocity = startVelocity,
                        dismissLength = dismissedTaskData.dismissLength,
                        finalPosition = dismissedTaskData.finalPosition,
                        dismissThreshold = dismissedTaskData.dismissThreshold,
                    ),
                )
            }

        // SpringSet tracking all dismiss springs before running end-snapping and relayout.
        var springSet =
            dismissedTaskViewSpring?.let { SpringSet(it, dismissedTaskData.finalPosition) }

        if (isDismissing) {
            // The spring set that will reflow the tasks to fill the gap left by the dismissed task.
            val reflowSpringSet =
                createTaskGridReflowSpringSet(
                    dismissedTaskView,
                    getDismissedTaskGapForReflow(dismissedTaskView, isSplitSelection),
                    gridEndData,
                    isSplitSelection,
                )
            if (springSet == null) {
                // Only reflow, as there is no dismissed task to animate.
                springSet = reflowSpringSet
            } else if (reflowSpringSet != null) {
                springSet.playAfterThreshold(
                    driverThreshold = dismissedTaskSecondaryDimension * verticalFactor,
                    triggeredSpringSet = reflowSpringSet,
                )
            }
        } else if (springSet != null && dismissedTaskView != null) {
            // Neighbor settling spring animations.
            val neighborSettlingSpringSet =
                createNeighborSettlingSpringSet(dismissedTaskView, isSpringDirectionVertical = true)
            springSet.playAfterThreshold(
                driverThreshold = dismissedTaskData.finalPosition,
                triggeredSpringSet = neighborSettlingSpringSet,
                minVelocity = startVelocity,
            )
            springSet.addEndListener {
                InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_OVERVIEW_TASK_DISMISS)
            }
        }

        if (!isSplitSelection) {
            springSet?.addStartListener {
                InteractionJankMonitorWrapper.begin(
                    recentsView,
                    Cuj.CUJ_LAUNCHER_OVERVIEW_TASK_DISMISS,
                )
            }
        }
        val endRunnable = {
            if (isDismissing) {
                onEndSnappingAndRelayout(
                    dismissedTaskView,
                    shouldRemoveTaskView,
                    isSplitSelection,
                    gridEndData,
                )
            } else {
                recentsView.onDismissAnimationEnds()
            }
        }
        if (springSet == null) {
            endRunnable()
            return null
        }
        return springSet.addEndListener(endRunnable).start()
    }

    /** Default dismissed task view spring animation. */
    private fun createDismissedTaskViewSpringAnimation(
        dismissedTaskView: TaskView
    ): SpringAnimation? {
        return createDismissedTaskViewSpringAnimation(
            dismissedTaskView,
            isDismissing = true,
            getDefaultDismissedTaskData(dismissedTaskView),
        )
    }

    /** Dismissed task view spring animation. */
    private fun createDismissedTaskViewSpringAnimation(
        dismissedTaskView: TaskView,
        isDismissing: Boolean,
        dismissedTaskData: DismissedTaskData,
    ): SpringAnimation? {
        val taskDismissFloatProperty =
            FloatPropertyCompat.createFloatPropertyCompat(
                dismissedTaskView.secondaryDismissTranslationProperty
            )
        var previousDisplacement = taskDismissFloatProperty.getValue(dismissedTaskView)
        // Animate dismissed task towards dismissal or rest state.
        val dismissedTaskViewSpringAnimation =
            SpringAnimation(dismissedTaskView, taskDismissFloatProperty)
                .setSpring(
                    createExpressiveDismissSpringForce()
                        .setFinalPosition(dismissedTaskData.finalPosition)
                )
                .setStartVelocity(dismissedTaskData.startVelocity)
                .addUpdateListener { animation, currentDisplacement, _ ->
                    // Play haptic as task crosses dismiss threshold from above or below.
                    val previousBeyondThreshold =
                        abs(previousDisplacement) >= abs(dismissedTaskData.dismissThreshold)
                    val currentBeyondThreshold =
                        abs(currentDisplacement) >= abs(dismissedTaskData.dismissThreshold)
                    if (previousBeyondThreshold != currentBeyondThreshold) {
                        msdlPlayerWrapper.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
                    }
                    previousDisplacement = currentDisplacement

                    if (dismissedTaskView.isRunningTask && recentsView.enableDrawingLiveTile) {
                        recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                            remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value =
                                taskDismissFloatProperty.getValue(dismissedTaskView)
                        }
                        recentsView.redrawLiveTile()
                    }
                    // End dismissed task animation once beyond the screen so next animations play.
                    if (
                        isDismissing &&
                            abs(currentDisplacement) >= abs(dismissedTaskData.dismissLength)
                    ) {
                        (animation as SpringAnimation).skipToEnd()
                    }
                }
        return dismissedTaskViewSpringAnimation
    }

    private fun getDefaultDismissedTaskData(dismissedTaskView: TaskView): DismissedTaskData {
        with(recentsView) {
            dismissedTaskView.getThumbnailBounds(mTempRect, /* relativeToDragLayer= */ true)
            val secondaryLayerDimension =
                pagedOrientationHandler.getSecondaryDimension(
                    (mContainer as ActivityContext).getDragLayer()
                )
            val verticalFactor = pagedOrientationHandler.getTaskDismissVerticalDirection().toFloat()
            val dismissLength =
                (pagedOrientationHandler.getTaskDismissLength(secondaryLayerDimension, mTempRect) *
                        verticalFactor)
                    .toInt()
            val dismissThreshold = (dismissLength * DEFAULT_DISMISS_THRESHOLD_FRACTION).toInt()
            val startVelocity = mTempRect.height().toFloat()
            val finalPosition = dismissLength.toFloat()
            return DismissedTaskData(
                startVelocity = startVelocity,
                dismissLength = dismissLength,
                finalPosition = finalPosition,
                dismissThreshold = dismissThreshold,
            )
        }
    }

    /** Dismisses all */
    fun dismissAllTasks() {
        val allDismissSprings =
            recentsView.mUtils.taskViews
                .reversed()
                .filter { taskView -> recentsView.isTaskViewVisible(taskView) }
                .mapNotNull { createDismissedTaskViewSpringAnimation(it) }
        SpringSet(SpringAnimation(FloatValueHolder()).setSpring(SpringForce(1f)))
            .playTogether(allDismissSprings)
            .addEndListener {
                with(recentsView) {
                    // Remove desktops first, since desks can be empty (so they have no recent
                    // tasks), and closing all tasks on a desk doesn't always necessarily mean that
                    // the desk will be removed. So, there are no guarantees that the below call to
                    // `ActivityManagerWrapper::removeAllRecentTasks()` will be enough.
                    if (areMultiDesksFlagsEnabled() && context.displayId.isExternalDisplay) {
                        mUtils.addOnDeskAddedListener(launchNewDeskListener)
                    }
                    systemUiProxy.removeAllDesks()

                    // Remove all the task views now
                    finishRecentsAnimation(/* toRecents */ true, /* shouldPip */ false) {
                        uiHelperExecutor.execute { activityManagerWrapper.removeAllRecentTasks() }
                        removeAllTaskViews()
                        if (context.displayId.isDefaultDisplay || !areMultiDesksFlagsEnabled()) {
                            startHome()
                        }
                    }
                }
            }
            .start()
    }

    /** Bounce neighboring tasks due to a canceled dismiss or the reflow of tasks after dismiss. */
    private fun createNeighborSettlingSpringSet(
        dismissedTaskView: TaskView,
        tasksToExclude: List<TaskView> = emptyList(),
        isSpringDirectionVertical: Boolean,
    ): SpringSet {
        // Empty spring animation exists for conditional start, and to drive neighboring springs.
        val neighborsToSettle =
            SpringAnimation(FloatValueHolder())
                .setSpring(createExpressiveDismissSpringForce().setFinalPosition(0f))
        val neighborSettlingSpringSet = SpringSet(neighborsToSettle)

        // Add tasks before dismissed index, fanning out from the dismissed task.
        // The order they are added matters, as each spring drives the next.
        var previousNeighbor = neighborsToSettle
        getTasksOffsetPairAdjacentToDismissedTask(dismissedTaskView, towardsStart = true)
            .filter { (taskView, _) -> !tasksToExclude.contains(taskView) }
            .forEach { (taskView, offset) ->
                previousNeighbor =
                    createNeighboringTaskViewSpringAnimation(
                        taskView,
                        offset * ADDITIONAL_DISMISS_DAMPING_RATIO,
                        previousNeighbor,
                        isSpringDirectionVertical,
                        neighborSettlingSpringSet,
                    )
            }
        // Add tasks after dismissed index, fanning out from the dismissed task.
        // The order they are added matters, as each spring drives the next.
        previousNeighbor = neighborsToSettle
        getTasksOffsetPairAdjacentToDismissedTask(dismissedTaskView, towardsStart = false)
            .filter { (taskView, _) -> !tasksToExclude.contains(taskView) }
            .forEach { (taskView, offset) ->
                previousNeighbor =
                    createNeighboringTaskViewSpringAnimation(
                        taskView,
                        offset * ADDITIONAL_DISMISS_DAMPING_RATIO,
                        previousNeighbor,
                        isSpringDirectionVertical,
                        neighborSettlingSpringSet,
                    )
            }
        return neighborSettlingSpringSet
    }

    /**
     * Gets pairs of (TaskView, offset) adjacent the dismissed task in visual order.
     *
     * <p>Gets tasks either before or after the dismissed task along with their offset from it. The
     * offset is the distance between indices for carousels, or distance between columns for grids.
     */
    private fun getTasksOffsetPairAdjacentToDismissedTask(
        dismissedTaskView: TaskView,
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
                    dismissedTaskView.taskViewId,
                    towardsStart,
                )
                .mapNotNull { (taskViewId, columnOffset) ->
                    recentsView.getTaskViewFromTaskViewId(taskViewId)?.let { taskView ->
                        Pair(taskView, columnOffset)
                    }
                }
        } else {
            val taskViewList = recentsView.mUtils.taskViews.toList()
            val dismissedTaskViewIndex = taskViewList.indexOf(dismissedTaskView)
            if (taskViewList.isEmpty() || dismissedTaskViewIndex == -1) return emptySequence()

            return if (towardsStart) {
                taskViewList
                    .take(dismissedTaskViewIndex)
                    .reversed()
                    .mapIndexed { index, taskView -> Pair(taskView, index + 1) }
                    .asSequence()
            } else {
                taskViewList
                    .takeLast(taskViewList.size - dismissedTaskViewIndex - 1)
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
        neighborSettlingSpringSet: SpringSet,
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
        neighborSettlingSpringSet.trackSpring(neighboringTaskViewSpringAnimation)
        // Drive current neighbor's spring with the previous neighbor's.
        previousNeighborSpringAnimation.addUpdateListener { _, value, _ ->
            neighboringTaskViewSpringAnimation.animateToFinalPosition(value)
        }
        return neighboringTaskViewSpringAnimation
    }

    /** Animates with springs the TaskViews beyond the dismissed task to fill the gap it left. */
    private fun createTaskGridReflowSpringSet(
        dismissedTaskView: TaskView?,
        dismissedTaskGap: Float,
        gridEndData: GridEndData,
        isSplitSelection: Boolean,
    ): SpringSet? {
        val towardsStart = if (recentsView.isRtl) dismissedTaskGap < 0 else dismissedTaskGap > 0
        // Grid end translation to run after all reflow animations have completed.
        val gridEndSpringSet = createGridEndTranslationSpringSet(gridEndData)
        val tasksWithOffsetsToReflow = getTasksToReflow(dismissedTaskView, towardsStart)
        if (tasksWithOffsetsToReflow.isEmpty()) {
            return gridEndSpringSet
        } else {
            // Empty spring exists for conditional start, and to drive neighboring springs.
            val reflowSpringAnimationDriver =
                SpringAnimation(FloatValueHolder())
                    .setSpring(
                        createExpressiveGridReflowSpringForce(finalPosition = dismissedTaskGap)
                    )
            recentsView.mTaskViewsDismissPrimaryTranslations.clear()
            // Separate spring end manager for reflow to coordinate start of grid end springs.
            val reflowSpringSet = SpringSet(reflowSpringAnimationDriver, dismissedTaskGap)
            buildDismissReflowSpringAnimationChain(
                tasksWithOffsetsToReflow,
                dismissedTaskGap,
                previousSpring = reflowSpringAnimationDriver,
                reflowSpringSet,
                isSplitSelection,
            )

            // Animate the settling of the neighbors as reflow tasks settle into place.
            if (dismissedTaskView != null) {
                val neighborSettlingSpringSet =
                    createNeighborSettlingSpringSet(
                        dismissedTaskView,
                        tasksToExclude = tasksWithOffsetsToReflow.map { (taskView, _) -> taskView },
                        isSpringDirectionVertical = false,
                    )
                reflowSpringSet.playAfterThreshold(
                    driverThreshold = dismissedTaskGap,
                    triggeredSpringSet = neighborSettlingSpringSet,
                )
            }
            if (gridEndSpringSet != null) {
                reflowSpringSet.playAfterThreshold(
                    driverThreshold = dismissedTaskGap,
                    triggeredSpringSet = gridEndSpringSet,
                )
            }
            return reflowSpringSet
        }
    }

    private fun getDismissedTaskGapForReflow(
        dismissedTaskView: TaskView?,
        isSplitSelection: Boolean,
    ): Float {
        with(recentsView) {
            val dismissedTaskGap =
                if (dismissedTaskView == null) {
                    0f
                } else {
                    // If current page beyond last TaskView's index, use last TaskView to calculate
                    // offset.
                    val lastTaskViewIndex = indexOfChild(mUtils.getLastTaskView())
                    val currentPage = currentPage.coerceAtMost(lastTaskViewIndex)
                    val dismissHorizontalFactor =
                        when {
                            dismissedTaskView.isGridTask -> 1f
                            currentPage == lastTaskViewIndex -> -1f
                            indexOfChild(dismissedTaskView) < currentPage -> -1f
                            else -> 1f
                        } * (if (isRtl) 1f else -1f)
                    (pagedOrientationHandler.getPrimarySize(dismissedTaskView) + pageSpacing) *
                        dismissHorizontalFactor
                }
            // Sliding translation for splitting tasks with large tiles present.
            val slidingTranslation =
                if (isSplitSelection && currentPageTaskView is DesktopTaskView) {
                    val nextSnappedPage = indexOfChild(mUtils.getFirstNonDesktopTaskView())
                    val newClearAllShortTotalWidthTranslation =
                        getGridEndData(dismissedTaskView = null)
                            .newClearAllShortTotalWidthTranslation
                    pagedOrientationHandler.getPrimaryScroll(this) -
                        getScrollForPage(nextSnappedPage) +
                        if (isRtl) newClearAllShortTotalWidthTranslation
                        else -newClearAllShortTotalWidthTranslation
                } else {
                    0f
                }
            return dismissedTaskGap + if (isRtl) slidingTranslation else -slidingTranslation
        }
    }

    private fun getTasksToReflow(
        dismissedTaskView: TaskView?,
        towardsStart: Boolean,
    ): List<Pair<TaskView, Int>> {
        // Null if splitting tasks while Desktop tasks are visible. Reflow all remaining grid tasks.
        if (dismissedTaskView == null) {
            return (recentsView.mUtils.getTopRowTaskViews().mapIndexed { index, taskView ->
                    taskView to index
                } +
                    recentsView.mUtils.getBottomRowTaskViews().mapIndexed { index, taskView ->
                        taskView to index
                    })
                .sortedBy { it.second }
        }
        val isDismissedTaskViewOnTopRow = recentsView.isOnGridTopRow(dismissedTaskView)
        val isDismissedTaskViewOnBottomRow = recentsView.isOnGridBottomRow(dismissedTaskView)
        return getTasksOffsetPairAdjacentToDismissedTask(dismissedTaskView, towardsStart)
            .filter { (taskView, _) ->
                when {
                    isDismissedTaskViewOnBottomRow -> recentsView.isOnGridBottomRow(taskView)
                    isDismissedTaskViewOnTopRow -> recentsView.isOnGridTopRow(taskView)
                    else -> true
                }
            }
            .toList()
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
        taskViewOffsetPairs: List<Pair<TaskView, Int>>,
        dismissedTaskGap: Float,
        previousSpring: SpringAnimation,
        reflowSpringSet: SpringSet,
        isSplitSelection: Boolean,
    ): SpringAnimation {
        if (taskViewOffsetPairs.isEmpty()) return previousSpring
        var lastTaskViewSpring = previousSpring
        var previousColumnDriverSpring = previousSpring
        var lastColumnOffset = taskViewOffsetPairs.first().second
        taskViewOffsetPairs
            .filter { (taskView, _) ->
                willTaskBeVisibleAfterDismiss(taskView, dismissedTaskGap.roundToInt())
            }
            .forEach { (taskView, column) ->
                val startValue =
                    if (
                        isSplitSelection &&
                            taskView !is DesktopTaskView &&
                            recentsView.currentPageTaskView is DesktopTaskView &&
                            !recentsView.isTaskViewVisible(taskView)
                    ) {
                        dismissedTaskGap +
                            (if (recentsView.isRtl) -recentsView.mLastComputedTaskSize.right
                            else recentsView.mLastComputedTaskSize.right)
                    } else 0f
                val taskViewSpringAnimation =
                    SpringAnimation(
                            taskView,
                            FloatPropertyCompat.createFloatPropertyCompat(
                                taskView.primaryDismissTranslationProperty
                            ),
                        )
                        .setSpring(createExpressiveGridReflowSpringForce(dismissedTaskGap))
                        .setStartValue(startValue)
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
                // For grid overview, if we are animating tasks in the same column offset, they
                // should both be pulled by the previous spring at the same time.
                if (column != lastColumnOffset) {
                    previousColumnDriverSpring = lastTaskViewSpring
                    lastColumnOffset = column
                }
                previousColumnDriverSpring.addUpdateListener { _, value, _ ->
                    taskViewSpringAnimation.animateToFinalPosition(value)
                }
                lastTaskViewSpring = taskViewSpringAnimation
                reflowSpringSet.trackSpring(taskViewSpringAnimation, dismissedTaskGap)
                recentsView.mTaskViewsDismissPrimaryTranslations[taskView] =
                    dismissedTaskGap.toInt()
            }
        return lastTaskViewSpring
    }

    /** Animates the grid to compensate the clear all gap after dismissal. */
    private fun createGridEndTranslationSpringSet(gridEndData: GridEndData): SpringSet? {
        val gridEndOffset = gridEndData.gridEndOffset
        if (gridEndOffset == 0f) {
            return null
        }

        // Create spring animation to drive all task view grid translations simultaneously.
        val gridEndSpring =
            SpringAnimation(FloatValueHolder())
                .setSpring(createExpressiveGridReflowSpringForce(finalPosition = gridEndOffset))
        val gridEndSpringSet = SpringSet(gridEndSpring, gridEndOffset)
        recentsView.mUtils.taskViews.forEach { taskView ->
            val taskViewGridEndSpringAnimation =
                SpringAnimation(
                        taskView,
                        FloatPropertyCompat.createFloatPropertyCompat(GRID_END_TRANSLATION_X),
                    )
                    .setSpring(createExpressiveGridReflowSpringForce(gridEndOffset))
            // Update live tile on spring animation.
            if (taskView.isRunningTask && recentsView.enableDrawingLiveTile) {
                taskViewGridEndSpringAnimation.addUpdateListener { _, _, _ ->
                    recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                        remoteTargetHandle.taskViewSimulator.taskPrimaryTranslation.value =
                            GRID_END_TRANSLATION_X.get(taskView)
                    }
                    recentsView.redrawLiveTile()
                }
            }
            gridEndSpringSet.trackSpring(taskViewGridEndSpringAnimation, gridEndOffset)
            gridEndSpring.addUpdateListener { _, value, _ ->
                taskViewGridEndSpringAnimation.animateToFinalPosition(value)
            }
        }
        // Animate alpha of clear all if translating grid to hide it.
        if (recentsView.isClearAllHidden) {
            SpringAnimation(
                    recentsView.clearAllButton,
                    FloatPropertyCompat.createFloatPropertyCompat(ClearAllButton.DISMISS_ALPHA),
                )
                .setSpring(createExpressiveDismissAlphaSpringForce())
                .addEndListener { _, _, _, _ -> recentsView.clearAllButton.dismissAlpha = 1f }
                .animateToFinalPosition(0f)
        }
        return gridEndSpringSet
    }

    /** Returns the distance between the end of the grid and clear all button after dismissal. */
    fun getGridEndData(
        dismissedTaskView: TaskView?,
        isExpressiveDismiss: Boolean = true,
        isFocusedTaskDismissed: Boolean = false,
        nextFocusedTaskView: TaskView? = null,
        isStagingFocusedTask: Boolean = false,
        nextFocusedTaskFromTop: Boolean = false,
        nextFocusedTaskWidth: Float = 0f,
    ): GridEndData {
        var gridEndOffset = 0f
        var snapToLastTask = false
        var newClearAllShortTotalWidthTranslation: Float
        var currentPageSnapsToEndOfGrid: Boolean
        with(recentsView) {
            val lastGridTaskView = if (showAsGrid()) lastGridTaskView else null
            val currentPageScroll = getScrollForPage(currentPage)
            val lastGridTaskScroll = getScrollForPage(indexOfChild(lastGridTaskView))
            currentPageSnapsToEndOfGrid = currentPageScroll == lastGridTaskScroll
            var topGridRowCount = mTopRowIdSet.size()
            var bottomGridRowCount =
                taskViewCount - mTopRowIdSet.size() - mUtils.getLargeTileCount()
            val topRowLonger = topGridRowCount > bottomGridRowCount
            val bottomRowLonger = bottomGridRowCount > topGridRowCount
            val dismissedFromTop =
                dismissedTaskView != null && mTopRowIdSet.contains(dismissedTaskView.taskViewId)
            val dismissedFromBottom =
                dismissedTaskView != null && !dismissedFromTop && !dismissedTaskView.isLargeTile
            if (dismissedFromTop || (isFocusedTaskDismissed && nextFocusedTaskFromTop)) {
                topGridRowCount--
            }
            if (dismissedFromBottom || (isFocusedTaskDismissed && !nextFocusedTaskFromTop)) {
                bottomGridRowCount--
            }
            newClearAllShortTotalWidthTranslation =
                getNewClearAllShortTotalWidthTranslation(
                    topGridRowCount,
                    bottomGridRowCount,
                    isStagingFocusedTask,
                )
            val isLastGridTaskViewVisibleForDismiss =
                when {
                    lastGridTaskView == null -> false
                    isExpressiveDismiss ->
                        isTaskViewVisible(lastGridTaskView) || lastGridTaskView == dismissedTaskView
                    else -> lastGridTaskView.isVisibleToUser
                }
            if (!isLastGridTaskViewVisibleForDismiss) {
                return GridEndData(
                    gridEndOffset,
                    snapToLastTask,
                    newClearAllShortTotalWidthTranslation,
                    currentPageSnapsToEndOfGrid,
                )
            }
            val dismissedTaskWidth =
                if (dismissedTaskView == null) 0f
                else (dismissedTaskView.layoutParams.width + pageSpacing).toFloat()
            val gapWidth =
                when {
                    (topRowLonger && dismissedFromTop) ||
                        (bottomRowLonger && dismissedFromBottom) -> dismissedTaskWidth
                    nextFocusedTaskView != null &&
                        ((topRowLonger && nextFocusedTaskFromTop) ||
                            (bottomRowLonger && !nextFocusedTaskFromTop)) -> nextFocusedTaskWidth
                    else -> 0f
                }
            if (gapWidth > 0) {
                if (clearAllShortTotalWidthTranslation == 0) {
                    val gapCompensation = gapWidth - newClearAllShortTotalWidthTranslation
                    gridEndOffset += if (isRtl) -gapCompensation else gapCompensation
                }
                if (isClearAllHidden) {
                    // If ClearAllButton isn't fully shown, snap to the last task.
                    snapToLastTask = true
                }
            }
            val isLeftRightSplit =
                (mContainer as ActivityContext).getDeviceProfile().isLeftRightSplit &&
                    isSplitSelectionActive
            if (isLeftRightSplit && !isStagingFocusedTask) {
                // LastTask's scroll is the minimum scroll in split select, if current scroll is
                // beyond that, we'll need to snap to last task instead.
                getLastGridTaskView()?.let { lastTask ->
                    val primaryScroll = pagedOrientationHandler.getPrimaryScroll(this)
                    val lastTaskScroll = getScrollForPage(indexOfChild(lastTask))
                    if (
                        (isRtl && primaryScroll < lastTaskScroll) ||
                            (!isRtl && primaryScroll > lastTaskScroll)
                    ) {
                        snapToLastTask = true
                    }
                }
            }
            if (snapToLastTask) {
                gridEndOffset += snapToLastTaskScrollDiff.toFloat()
            } else if (isLeftRightSplit && currentPageSnapsToEndOfGrid) {
                // Use last task as reference point for scroll diff and snapping calculation as it's
                // the only invariant point in landscape split screen.
                snapToLastTask = true
            }

            // Handle large tile scroll when dismissing the last small task.
            if (mUtils.getGridTaskCount() == 1 && dismissedTaskView?.isGridTask == true) {
                mUtils.getLastLargeTaskView()?.let { lastLargeTile ->
                    val primaryScroll = pagedOrientationHandler.getPrimaryScroll(this)
                    val lastLargeTileScroll = getScrollForPage(indexOfChild(lastLargeTile))
                    gridEndOffset = (primaryScroll - lastLargeTileScroll).toFloat()

                    if (!isClearAllHidden) {
                        // If ClearAllButton is visible, reduce the distance by scroll difference
                        // between ClearAllButton and the last task.
                        gridEndOffset +=
                            getLastTaskScroll(
                                    /*clearAllScroll=*/ 0,
                                    pagedOrientationHandler.getPrimarySize(clearAllButton),
                                )
                                .toFloat()
                    }
                }
            }
        }
        return GridEndData(
            gridEndOffset,
            snapToLastTask,
            newClearAllShortTotalWidthTranslation,
            currentPageSnapsToEndOfGrid,
        )
    }

    private fun getNewClearAllShortTotalWidthTranslation(
        topGridRowCount: Int,
        bottomGridRowCount: Int,
        isStagingFocusedTask: Boolean,
    ): Float {
        with(recentsView) {
            if (clearAllShortTotalWidthTranslation != 0) {
                return 0f
            }
            // If first task is not in the expected position (mLastComputedTaskSize) and too
            // close to ClearAllButton, then apply extra translation to ClearAllButton.
            var longRowWidth =
                max(topGridRowCount, bottomGridRowCount) *
                    (mLastComputedGridTaskSize.width() + pageSpacing)
            if (!enableGridOnlyOverview() && !isStagingFocusedTask) {
                longRowWidth += mLastComputedTaskSize.width() + pageSpacing
            }
            val firstTaskStart = mLastComputedGridSize.left + longRowWidth
            val expectedFirstTaskStart = mLastComputedTaskSize.right
            // Compensate the removed gap if we don't already have shortTotalCompensation,
            // and adjust accordingly to the new shortTotalCompensation after dismiss.
            return if (firstTaskStart < expectedFirstTaskStart) {
                (expectedFirstTaskStart - firstTaskStart).toFloat()
            } else {
                0f
            }
        }
    }

    private fun onEndSnappingAndRelayout(
        dismissedTaskView: TaskView?,
        shouldRemoveTask: Boolean,
        dismissingForSplitSelection: Boolean,
        gridEndData: GridEndData,
    ) {
        with(recentsView) {
            if (pageCount == 0) {
                return@with
            }
            updateCurveProperties()
            loadVisibleTaskData(TaskView.FLAG_UPDATE_ALL)

            // Page snapping and relayout to run after all animations have completed.
            val onFinishComplete = {
                // Reset task translations as they may have updated via the dismiss animations.
                resetTaskVisuals()

                // Denote if any task has been dismissed for grid rebalancing.
                mAnyTaskHasBeenDismissed = true
                // Cache group task before removing.
                handleGroupTaskRemoval(dismissedTaskView, shouldRemoveTask)

                // Get page to snap to before removing dismissed task.
                val dismissedTaskViewId = dismissedTaskView?.taskViewId ?: INVALID_TASK_ID
                val pageToSnapTo =
                    when {
                        (dismissedTaskView != null &&
                            (!showAsGrid() || dismissedTaskView.isLargeTile)) -> {
                            getPageToSnapTo(dismissedTaskView)
                        }
                        showAsGrid() -> {
                            getPageToSnapToForGrid(gridEndData, dismissedTaskViewId)
                        }
                        else -> {
                            currentPage
                        }
                    }

                // Remove dismissed task.
                removeViewInLayout(dismissedTaskView)
                mTopRowIdSet.remove(dismissedTaskViewId)

                // Update the UI after removal and snap to page.
                updateUiAfterTaskRemoval(dismissedTaskView, pageToSnapTo)

                if (!dismissingForSplitSelection) {
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_OVERVIEW_TASK_DISMISS)
                }
            }

            // Run the final page snapping and relayout
            if (enableDrawingLiveTile && dismissedTaskView?.isRunningTask == true) {
                finishRecentsAnimation(
                    /* toRecents */ true,
                    /* shouldPip */ false,
                    onFinishComplete,
                )
            } else {
                onFinishComplete()
            }
        }
    }

    /**
     * Caches the groupTask before removing it. As the deskId might become invalid by
     * removeViewInLayout called on the dismissed task. It might happen before
     * removeGroupTaskInternal which runs on a helper thread.
     */
    private fun handleGroupTaskRemoval(dismissedTaskView: TaskView?, shouldRemoveTask: Boolean) {
        with(recentsView) {
            if (shouldRemoveTask && dismissedTaskView != null) {
                val groupTask = dismissedTaskView.groupTask
                if (groupTask != null) {
                    // For the multi desk case, the launcher should switch to the new desk once the
                    // last task of the previous desk is removed.
                    if (
                        areMultiDesksFlagsEnabled() &&
                            context.displayId.isExternalDisplay &&
                            taskViewCount == 1 &&
                            contains(dismissedTaskView)
                    ) {
                        mUtils.addOnDeskAddedListener(launchNewDeskListener)
                    }
                    if (dismissedTaskView.isRunningTask) {
                        finishRecentsAnimation(/* toRecents */ true, /* shouldPip */ false) {
                            removeGroupTaskInternal(groupTask)
                        }
                    } else {
                        removeGroupTaskInternal(groupTask)
                    }
                    (mContainer as ActivityContext)
                        .statsLogManager
                        .logger()
                        .withItemInfo(dismissedTaskView.itemInfo)
                        .log(LauncherEvent.LAUNCHER_TASK_DISMISS_SWIPE_UP)
                }
            }
        }
    }

    private fun getPageToSnapTo(dismissedTaskView: TaskView?): Int {
        with(recentsView) {
            var pageToSnapTo = currentPage
            if (
                indexOfChild(dismissedTaskView) < pageToSnapTo ||
                    pageToSnapTo == indexOfChild(mUtils.getLastTaskView())
            ) {
                pageToSnapTo--
            }
            return pageToSnapTo
        }
    }

    private fun getPageToSnapToForGrid(gridEndData: GridEndData, dismissedTaskViewId: Int): Int {
        with(recentsView) {
            var pageToSnapTo = currentPage
            var taskViewIdToSnapTo = -1
            currentPageScrollDiff = 0

            if (gridEndData.gridEndOffset != 0f) {
                if (gridEndData.snapToLastTask) {
                    // Last task will be determined after removing dismissed task.
                    pageToSnapTo = -1
                } else if (taskViewCount > 2) {
                    pageToSnapTo = indexOfChild(clearAllButton)
                } else if (isClearAllHidden) {
                    // Snap to focused task if clear all is hidden.
                    pageToSnapTo = firstTaskViewIndex
                }
            } else {
                val snappedTaskView = currentPageTaskView
                if (snappedTaskView != null && !gridEndData.snapToLastTask) {
                    val snappedTaskViewId = snappedTaskView.taskViewId
                    val isSnappedTaskInTopRow = mTopRowIdSet.contains(snappedTaskViewId)
                    val taskViewIdArray =
                        if (isSnappedTaskInTopRow) {
                            mUtils.getTopRowIdArray()
                        } else {
                            mUtils.getBottomRowIdArray()
                        }
                    val snappedIndex = taskViewIdArray.indexOf(snappedTaskViewId)
                    taskViewIdArray.removeValue(dismissedTaskViewId)
                    if (snappedIndex >= 0 && snappedIndex < taskViewIdArray.size()) {
                        taskViewIdToSnapTo = taskViewIdArray[snappedIndex]
                    } else if (snappedIndex == taskViewIdArray.size()) {
                        val inverseRowTaskViewIdArray =
                            if (isSnappedTaskInTopRow) {
                                mUtils.getBottomRowIdArray()
                            } else {
                                mUtils.getTopRowIdArray()
                            }
                        if (snappedIndex < inverseRowTaskViewIdArray.size()) {
                            taskViewIdToSnapTo = inverseRowTaskViewIdArray[snappedIndex]
                        }
                    }
                }
                val primaryScroll = pagedOrientationHandler.getPrimaryScroll(this)
                val currentPageScroll = getScrollForPage(currentPage)
                currentPageScrollDiff = primaryScroll - currentPageScroll
            }

            // Calculate page to snap to as if the dismissed task view is removed from the grid.
            val topRowIdArray = mUtils.getTopRowIdArray()
            val bottomRowIdArray = mUtils.getBottomRowIdArray()
            topRowIdArray.removeValue(dismissedTaskViewId)
            bottomRowIdArray.removeValue(dismissedTaskViewId)
            val children =
                children.filter { child -> child != getTaskViewFromTaskViewId(dismissedTaskViewId) }
            if (gridEndData.snapToLastTask) {
                val lastGridTaskView = getLastGridTaskView(topRowIdArray, bottomRowIdArray)
                pageToSnapTo =
                    if (lastGridTaskView == null) PagedView.INVALID_PAGE
                    else children.indexOf(lastGridTaskView as View)
                if (pageToSnapTo == PagedView.INVALID_PAGE) {
                    val lastLargeTaskView = mUtils.getLastLargeTaskView()
                    pageToSnapTo =
                        if (lastLargeTaskView == null) PagedView.INVALID_PAGE
                        else children.indexOf(lastLargeTaskView as View)
                }
            } else if (taskViewIdToSnapTo != -1) {
                // If snapping to another page due to indices rearranging, find
                // the new index after dismissal & rearrange using the TaskView ID.
                pageToSnapTo =
                    children.indexOf(getTaskViewFromTaskViewId(taskViewIdToSnapTo) as View)
                if (!gridEndData.currentPageSnapsToEndOfGrid) {
                    val dismissedTaskViewIndex =
                        indexOfChild(getTaskViewFromTaskViewId(dismissedTaskViewId))
                    currentPageScrollDiff +=
                        getOffsetFromScrollPosition(
                            pageToSnapTo,
                            topRowIdArray,
                            bottomRowIdArray,
                            dismissedTaskViewIndex,
                        )
                }
            }
            return pageToSnapTo
        }
    }

    private fun updateUiAfterTaskRemoval(dismissedTaskView: TaskView?, pageToSnapTo: Int) {
        with(recentsView) {
            if (taskViewCount == 0) {
                if (!isSplitSelectionActive) {
                    removeViewInLayout(clearAllButton)
                    removeViewInLayout(addDeskButton)
                    if (dismissedTaskView === homeTaskView) {
                        updateEmptyMessage()
                    } else {
                        if (!areMultiDesksFlagsEnabled() || context.displayId.isDefaultDisplay) {
                            startHome()
                        }
                    }
                }
            } else {
                updateTaskSize()
                mUtils.updateChildTaskOrientations()
                updateScrollSynchronously()

                val highestVisibleTaskView = getHighestVisibleTaskView()
                if (showAsGrid() && highestVisibleTaskView != null) {
                    rebalanceTasksInGrid(highestVisibleTaskView)
                }
                pageBeginTransition()
                currentPage = pageToSnapTo
                dispatchScrollChanged()
                updateActionsViewFocusedScroll()
                if (
                    isClearAllHidden &&
                        !(mContainer as ActivityContext)
                            .getDeviceProfile()
                            .deviceProperties
                            .isTablet
                ) {
                    actionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING, false)
                }
            }
            updateCurrentTaskActionsVisibility()
            onDismissAnimationEnds()
            mTaskViewsDismissPrimaryTranslations.clear()
        }
    }

    private fun rebalanceTasksInGrid(highestVisibleTaskView: TaskView) {
        with(recentsView) {
            val screenStart = pagedOrientationHandler.getPrimaryScroll(this)
            val taskStart =
                (pagedOrientationHandler.getChildStart(highestVisibleTaskView) +
                    highestVisibleTaskView.getOffsetAdjustment(true).toInt())

            val shouldRebalance =
                if (isRtl) {
                    taskStart <= screenStart + pageSpacing
                } else {
                    val screenEnd = (screenStart + pagedOrientationHandler.getMeasuredSize(this))
                    val taskSize =
                        (pagedOrientationHandler.getMeasuredSize(highestVisibleTaskView) *
                                highestVisibleTaskView.getSizeAdjustment(false))
                            .toInt()
                    val taskEnd = taskStart + taskSize
                    taskEnd >= screenEnd - pageSpacing
                }

            if (shouldRebalance) {
                updateGridProperties(highestVisibleTaskView)
                updateScrollSynchronously()
            }
        }
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

    private fun createExpressiveDismissAlphaSpringForce(): SpringForce {
        val resourceProvider = DynamicResource.provider(recentsView.mContainer)
        return SpringForce()
            .setDampingRatio(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_effects_damping_ratio)
            )
            .setStiffness(resourceProvider.getFloat(R.dimen.expressive_dismiss_effects_stiffness))
    }

    /**
     * Plays a set of {@link SpringAnimation} objects in the specified order.
     *
     * <p>Animations can play together, in sequence, or after a specified threshold is passed.
     */
    class SpringSet(private val driverSpring: SpringAnimation, driverSpringThreshold: Float = 0f) {
        private val trackedSprings = mutableSetOf<SpringAnimation>()
        private val trackedSpringSets = mutableSetOf<SpringSet>()
        private var runningSpringCount = 0
        private val startListenerSet = mutableSetOf<() -> Unit>()
        private val endListenerSet = mutableSetOf<() -> Unit>()
        private var hasStarted = false
        private var hasEnded = false

        init {
            trackSpring(driverSpring, driverSpringThreshold)
        }

        fun start(): SpringSet {
            if (hasStarted) {
                return this
            }
            hasStarted = true
            if (trackedSprings.isEmpty()) {
                onEnd()
                return this
            }
            driverSpring.start()
            startListenerSet.forEach { it() }
            return this
        }

        private fun onEnd() {
            if (hasEnded) {
                return
            }
            hasEnded = true
            endListenerSet.forEach { it() }
        }

        fun cancel(): SpringSet {
            driverSpring.cancel()
            trackedSprings.forEach { it.cancel() }
            trackedSpringSets.forEach { it.cancel() }
            onEnd()
            return this
        }

        /**
         * Increase spring constants to force animations to end quickly.
         *
         * <p>A high stiffness applies more force to the object to bring it to its end value. A
         * damping ratio of 1f is critically damped, and the object will return to equilibrium
         * within the shortest amount of time.
         */
        fun speedUpSpringsToEnd(): SpringSet {
            trackedSprings.forEach {
                it.spring.setStiffness(SPEED_UP_STIFFNESS).setDampingRatio(1f)
            }
            trackedSpringSets.forEach { it.speedUpSpringsToEnd() }
            return this
        }

        fun addStartListener(startListener: () -> Unit): SpringSet {
            startListenerSet.add(startListener)
            return this
        }

        fun addEndListener(endRunnable: () -> Unit): SpringSet {
            endListenerSet.add(endRunnable)
            return this
        }

        fun trackSpring(spring: SpringAnimation, minimumDistance: Float = 0f): SpringSet {
            if (trackedSprings.contains(spring)) {
                throw IllegalArgumentException("SpringSet already contains this spring.")
            }
            trackedSprings.add(spring)
            runningSpringCount++
            var canSpringEnd = false
            spring.addUpdateListener { _, value, _ ->
                // Do not allow end listener to fire until we have passed the minimum distance.
                if (!canSpringEnd && abs(value - minimumDistance) < spring.minimumVisibleChange) {
                    canSpringEnd = true
                }
            }
            spring.addEndListener { _, _, _, _ ->
                if (!canSpringEnd || runningSpringCount == 0) {
                    return@addEndListener
                }
                if (--runningSpringCount == 0) {
                    onEnd()
                }
            }
            return this
        }

        private fun trackSpringSet(springSet: SpringSet): SpringSet {
            runningSpringCount++
            trackedSpringSets.add(springSet)
            springSet.addEndListener {
                if (--runningSpringCount == 0) {
                    onEnd()
                }
            }
            return this
        }

        fun playAfterThreshold(
            driverThreshold: Float,
            triggeredSpringSet: SpringSet,
            minVelocity: Float = 0f,
        ): SpringSet {
            trackSpringSet(triggeredSpringSet)
            var lastPosition = 0f
            var isTriggered = false
            driverSpring.addUpdateListener { _, value, velocity ->
                // We do not compare to the threshold directly, as the update listener
                // does not necessarily hit every value. Do not check again once it has started
                // settling, as a spring can bounce past the end value multiple times.
                if (isTriggered) return@addUpdateListener
                if (
                    lastPosition < driverThreshold && value >= driverThreshold ||
                        lastPosition > driverThreshold && value <= driverThreshold
                ) {
                    isTriggered = true
                }
                lastPosition = value
                if (isTriggered) {
                    val startVelocity =
                        abs(velocity).coerceAtLeast(abs(minVelocity)) * velocity.sign
                    triggeredSpringSet.driverSpring.setStartVelocity(startVelocity)
                    triggeredSpringSet.start()
                }
            }
            return this
        }

        fun playTogether(springs: List<SpringAnimation>): SpringSet {
            springs.forEach {
                trackSpring(it)
                addStartListener { it.start() }
            }
            return this
        }
    }

    data class GridEndData(
        val gridEndOffset: Float,
        val snapToLastTask: Boolean,
        val newClearAllShortTotalWidthTranslation: Float,
        val currentPageSnapsToEndOfGrid: Boolean,
    )

    data class DismissedTaskData(
        val startVelocity: Float,
        val dismissLength: Int,
        val finalPosition: Float,
        val dismissThreshold: Int,
    )

    private companion object {
        // The additional damping to apply to tasks further from the dismissed task.
        private const val ADDITIONAL_DISMISS_DAMPING_RATIO = 0.15f
        private const val RECENTS_SCALE_SPRING_MULTIPLIER = 1000f
        private const val DEFAULT_DISMISS_THRESHOLD_FRACTION = 0.5f
        private const val SPEED_UP_STIFFNESS = 100_000f
    }
}
