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

package com.android.quickstep.views

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.PointF
import android.graphics.Rect
import android.util.FloatProperty
import android.util.Log
import android.util.Property
import android.view.View
import android.view.View.LAYOUT_DIRECTION_LTR
import android.view.View.LAYOUT_DIRECTION_RTL
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.AbstractFloatingView.TYPE_TASK_MENU
import com.android.launcher3.AbstractFloatingView.getTopOpenViewWithType
import com.android.launcher3.Flags.enableDesktopExplodedView
import com.android.launcher3.Flags.enableLargeDesktopWindowingTile
import com.android.launcher3.Flags.enableOverviewOnConnectedDisplays
import com.android.launcher3.PagedView.INVALID_PAGE
import com.android.launcher3.R
import com.android.launcher3.Utilities.getPivotsForScalingRectToRect
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.statehandlers.DesktopVisibilityController.Companion.INACTIVE_DESK_ID
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview
import com.android.launcher3.util.OverviewReleaseFlags.enableOverviewIconMenu
import com.android.launcher3.util.window.WindowManagerProxy.DesktopVisibilityListener
import com.android.quickstep.GestureState
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle
import com.android.quickstep.util.DesksUtils.Companion.areMultiDesksFlagsEnabled
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.isExternalDisplay
import com.android.quickstep.views.RecentsView.DESKTOP_CAROUSEL_DETACH_PROGRESS
import com.android.quickstep.views.RecentsView.RECENTS_GRID_PROGRESS
import com.android.quickstep.views.RecentsView.RUNNING_TASK_ATTACH_ALPHA
import com.android.quickstep.views.RecentsView.TAG
import com.android.quickstep.views.RecentsView.TASK_THUMBNAIL_SPLASH_ALPHA
import com.android.quickstep.views.TaskView.Companion.FLAG_UPDATE_ALL
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.wm.shell.shared.GroupedTaskInfo
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus.enableMultipleDesktops
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer
import kotlin.math.min
import kotlin.reflect.KMutableProperty1

/**
 * Helper class for [RecentsView]. This util class contains refactored and extracted functions from
 * RecentsView to facilitate the implementation of unit tests.
 */
class RecentsViewUtils(private val recentsView: RecentsView<*, *>) : DesktopVisibilityListener {
    val taskViews = TaskViewsIterable(recentsView)

    /** Callback to be invoked when a new desk is added. */
    interface OnDeskAddedListener {
        /**
         * Called when a new desk is added.
         *
         * @param desktopTaskView The [DesktopTaskView] of the new desk.
         */
        fun onDeskAdded(desktopTaskView: DesktopTaskView)
    }

    private val onDeskAddedListeners = CopyOnWriteArrayList<OnDeskAddedListener>()

    /** Takes a screenshot of all [taskView] and return map of taskId to the screenshot */
    fun screenshotTasks(taskView: TaskView): Map<Int, ThumbnailData> {
        val recentsAnimationController = recentsView.recentsAnimationController ?: return emptyMap()
        return taskView.taskContainers.associate {
            it.task.key.id to recentsAnimationController.screenshotTask(it.task.key.id)
        }
    }

    /**
     * Sorts task groups to move desktop tasks to the end of the list.
     *
     * @param tasks List of group tasks to be sorted.
     * @return Sorted list of GroupTasks to be used in the RecentsView.
     */
    fun sortDesktopTasksToFront(tasks: List<GroupTask>): List<GroupTask> {
        var (desktopTasks, otherTasks) = tasks.partition { it.taskViewType == TaskViewType.DESKTOP }
        if (areMultiDesksFlagsEnabled()) {
            // Desk IDs of newer desks are larger than those of older desks, hence we can use them
            // to sort desks from old to new.
            desktopTasks = desktopTasks.sortedBy { (it as DesktopTask).deskId }
        }
        return otherTasks + desktopTasks
    }

    fun sortExternalDisplayTasksToFront(tasks: List<GroupTask>): List<GroupTask> {
        val (externalDisplayTasks, otherTasks) =
            tasks.partition { it.tasks.firstOrNull().isExternalDisplay }
        return otherTasks + externalDisplayTasks
    }

    class TaskViewsIterable(val recentsView: RecentsView<*, *>) : Iterable<TaskView> {
        /** Iterates TaskViews when its index inside the RecentsView is needed. */
        fun forEachWithIndexInParent(consumer: BiConsumer<Int, TaskView>) {
            recentsView.children.forEachIndexed { index, child ->
                (child as? TaskView)?.let { consumer.accept(index, it) }
            }
        }

        override fun iterator(): Iterator<TaskView> =
            recentsView.children.mapNotNull { it as? TaskView }.iterator()
    }

    /** Counts [TaskView]s that are [DesktopTaskView] instances. */
    private fun getDesktopTaskViewCount(): Int = taskViews.count { it is DesktopTaskView }

    /** Counts [TaskView]s that are not [DesktopTaskView] instances. */
    fun getNonDesktopTaskViewCount(): Int = taskViews.count { it !is DesktopTaskView }

    /** Returns a list of all large TaskView Ids from [TaskView]s */
    fun getLargeTaskViewIds(): List<Int> = taskViews.filter { it.isLargeTile }.map { it.taskViewId }

    /** Returns a list of all large TaskViews [TaskView]s */
    fun getLargeTaskViews(): List<TaskView> = taskViews.filter { it.isLargeTile }

    /** Returns a list of all non-large TaskViews [TaskView]s */
    fun getSmallTaskViews(): List<TaskView> = taskViews.filter { !it.isLargeTile }

    /** Returns all the TaskViews in the top row, without the focused task */
    fun getTopRowTaskViews(): List<TaskView> =
        taskViews.filter { recentsView.mTopRowIdSet.contains(it.taskViewId) }

    /** Returns all the task Ids in the top row, without the focused task */
    fun getTopRowIdArray(): IntArray =
        getTopRowTaskViews().map { it.taskViewId }.toLauncher3IntArray()

    /** Returns all the TaskViews in the bottom row, without the focused task */
    fun getBottomRowTaskViews(): List<TaskView> =
        taskViews.filter { !recentsView.mTopRowIdSet.contains(it.taskViewId) && !it.isLargeTile }

    /** Returns all the task Ids in the bottom row, without the focused task */
    fun getBottomRowIdArray(): IntArray =
        getBottomRowTaskViews().map { it.taskViewId }.toLauncher3IntArray()

    private fun List<Int>.toLauncher3IntArray() =
        IntArray(size).apply { this@toLauncher3IntArray.forEach(::add) }

    /** Counts [TaskView]s that are large tiles. */
    fun getLargeTileCount(): Int = taskViews.count { it.isLargeTile }

    /** Counts [TaskView]s that are grid tasks. */
    fun getGridTaskCount(): Int = taskViews.count { it.isGridTask }

    /** Returns the first TaskView that should be displayed as a large tile. */
    fun getFirstLargeTaskView(): TaskView? =
        taskViews.firstOrNull {
            it.isLargeTile && !(recentsView.isSplitSelectionActive && it is DesktopTaskView)
        }

    /**
     * Returns the [DesktopTaskView] that matches the given [deskId], or null if it doesn't exist.
     */
    fun getDesktopTaskViewForDeskId(deskId: Int): DesktopTaskView? {
        if (deskId == INACTIVE_DESK_ID) {
            return null
        }
        return taskViews.firstOrNull { it is DesktopTaskView && it.deskId == deskId }
            as? DesktopTaskView
    }

    /** Returns the active desk ID of the display that contains the [recentsView] instance. */
    fun getActiveDeskIdOnThisDisplay(): Int =
        DesktopVisibilityController.INSTANCE.get(recentsView.context)
            .getActiveDeskId(recentsView.mContainer.display.displayId)

    /** Returns the expected focus task. */
    fun getFirstNonDesktopTaskView(): TaskView? =
        if (enableLargeDesktopWindowingTile()) taskViews.firstOrNull { it !is DesktopTaskView }
        else taskViews.firstOrNull()

    fun getLastDesktopTaskView(): TaskView? = taskViews.lastOrNull { it is DesktopTaskView }

    /**
     * Returns the [TaskView] that should be the current page during task binding, in the following
     * priorities:
     * 1. Running task
     * 2. Focused task
     * 3. First non-desktop task
     * 4. Last desktop task
     * 5. null otherwise
     */
    fun getExpectedCurrentTask(runningTaskView: TaskView?, focusedTaskView: TaskView?): TaskView? =
        runningTaskView
            ?: focusedTaskView
            ?: taskViews.firstOrNull {
                it !is DesktopTaskView &&
                    (enableOverviewOnConnectedDisplays() || !it.isExternalDisplay)
            }
            ?: taskViews.lastOrNull()

    private fun getDeviceProfile() = (recentsView.mContainer as RecentsViewContainer).deviceProfile

    fun getRunningTaskExpectedIndex(runningTaskView: TaskView): Int {
        if (areMultiDesksFlagsEnabled() && runningTaskView is DesktopTaskView) {
            // Use the [deskId] to keep desks in the order of their creation, as a newer desk
            // always has a larger [deskId] than the older desks.
            val desktopTaskView =
                taskViews.firstOrNull {
                    it is DesktopTaskView &&
                        it.deskId != INACTIVE_DESK_ID &&
                        it.deskId <= runningTaskView.deskId
                }
            if (desktopTaskView != null) return recentsView.indexOfChild(desktopTaskView)
        }
        val firstTaskViewIndex = recentsView.indexOfChild(getFirstTaskView())
        return if (getDeviceProfile().deviceProperties.isTablet) {
            var index = firstTaskViewIndex
            if (enableLargeDesktopWindowingTile() && runningTaskView !is DesktopTaskView) {
                // For fullsreen tasks, skip over Desktop tasks in its section
                index +=
                    if (runningTaskView.isExternalDisplay) {
                        taskViews.count { it is DesktopTaskView && it.isExternalDisplay }
                    } else {
                        taskViews.count { it is DesktopTaskView && !it.isExternalDisplay }
                    }
            }
            if (!runningTaskView.isExternalDisplay) {
                // For main display section, skip over external display tasks
                index += taskViews.count { it.isExternalDisplay }
            }
            index
        } else {
            val currentIndex: Int = recentsView.indexOfChild(runningTaskView)
            return if (currentIndex != -1) {
                currentIndex // Keep the position if running task already in layout.
            } else {
                // New running task are added to the front to begin with.
                firstTaskViewIndex
            }
        }
    }

    /** Returns the first TaskView if it exists, or null otherwise. */
    fun getFirstTaskView(): TaskView? = taskViews.firstOrNull()

    /** Returns the last TaskView if it exists, or null otherwise. */
    fun getLastTaskView(): TaskView? = taskViews.lastOrNull()

    /** Returns the first TaskView that is not large */
    fun getFirstSmallTaskView(): TaskView? = taskViews.firstOrNull { !it.isLargeTile }

    /** Returns the last TaskView that should be displayed as a large tile. */
    fun getLastLargeTaskView(): TaskView? = taskViews.lastOrNull { it.isLargeTile }

    override fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
        recentsView.addDeskButton?.isInvisible = !canCreateDesks
    }

    private fun animateDesktopTaskViewSpringIn(desktopTaskView: DesktopTaskView) {
        val taskDismissFloatProperty =
            FloatPropertyCompat.createFloatPropertyCompat(
                desktopTaskView.primaryDismissTranslationProperty
            )

        with(recentsView) {
            // Calculate initial translation to bring it offscreen.
            val desktopTaskViewIndex = indexOfChild(desktopTaskView)
            val midpointIndex =
                if (getTaskViewAt(desktopTaskViewIndex + 1) != null) desktopTaskViewIndex + 1
                else INVALID_PAGE
            var offscreenTranslationX =
                getHorizontalOffsetSize(desktopTaskViewIndex, midpointIndex, 1f)

            // Add 40dp to the offscreen translation.
            val additionalOffsetPx =
                context.resources.getDimensionPixelSize(
                    R.dimen.newly_created_desktop_offscreen_position
                )
            offscreenTranslationX += if (isRtl) additionalOffsetPx else -additionalOffsetPx
            desktopTaskView.primaryDismissTranslationProperty.set(
                desktopTaskView,
                offscreenTranslationX,
            )
            desktopTaskView.isInvisible = false

            val dampingRatio =
                context.resources.getFloat(R.dimen.newly_created_desktop_spring_damping_ratio)
            val stiffness =
                context.resources.getFloat(R.dimen.newly_created_desktop_spring_stiffness)

            SpringAnimation(desktopTaskView, taskDismissFloatProperty)
                .setSpring(SpringForce(0f).setDampingRatio(dampingRatio).setStiffness(stiffness))
                .start()
        }
    }

    override fun onDeskAdded(displayId: Int, deskId: Int) {
        with(recentsView) {
            // Ignore desk changes that don't belong to this display.
            if (displayId != mContainer.displayId) {
                return
            }

            if (getDesktopTaskViewForDeskId(deskId) != null) {
                Log.e(TAG, "A task view for this desk has already been added.")
                return
            }

            val currentTaskView = currentPageTaskView

            // We assume that a newly added desk is always empty and gets added to the left of the
            // `AddNewDesktopButton`.
            val desktopTaskView = getTaskViewFromPool(TaskViewType.DESKTOP) as DesktopTaskView
            desktopTaskView.bind(
                DesktopTask(deskId, displayId, emptyList()),
                pagedViewOrientedState,
                taskOverlayFactory,
            )
            desktopTaskView.isInvisible = true

            val insertionIndex = 1 + indexOfChild(addDeskButton!!)
            addView(desktopTaskView, insertionIndex)
            updateTaskSize()
            updateChildTaskOrientations()
            updateScrollSynchronously()
            animateDesktopTaskViewSpringIn(desktopTaskView)

            // Set Current Page based on the stored TaskView.
            currentTaskView?.let { setCurrentPage(indexOfChild(it)) }

            onDeskAddedListeners.forEach { it.onDeskAdded(desktopTaskView) }
        }
    }

    override fun onDeskRemoved(displayId: Int, deskId: Int) {
        with(recentsView) {
            // Ignore desk changes that don't belong to this display.
            if (displayId != mContainer.displayId) {
                return
            }

            // We need to distinguish between desk removals that are triggered from outside of
            // overview vs. the ones that were initiated from overview by dismissing the
            // corresponding desktop task view.
            getDesktopTaskViewForDeskId(deskId)?.let {
                dismissTaskView(it, /* animateTaskView= */ true, /* removeTask= */ true)
            }
        }
    }

    /**
     * Gets the list of accessibility children. Currently all the children of RecentsViews are
     * added, and in the reverse order to the list.
     */
    fun getAccessibilityChildren(): List<View> = recentsView.children.toList().reversed()

    @JvmOverloads
    /** Returns the first [TaskView], with some tasks possibly hidden in the carousel. */
    fun getFirstTaskViewInCarousel(
        nonRunningTaskCarouselHidden: Boolean,
        runningTaskView: TaskView? = recentsView.runningTaskView,
    ): TaskView? =
        taskViews.firstOrNull {
            it.isVisibleInCarousel(runningTaskView, nonRunningTaskCarouselHidden)
        }

    /** Returns the last [TaskView], with some tasks possibly hidden in the carousel. */
    fun getLastTaskViewInCarousel(nonRunningTaskCarouselHidden: Boolean): TaskView? =
        taskViews.lastOrNull {
            it.isVisibleInCarousel(recentsView.runningTaskView, nonRunningTaskCarouselHidden)
        }

    /** Returns if any small tasks are fully visible */
    fun isAnySmallTaskFullyVisible(): Boolean =
        taskViews.any { !it.isLargeTile && recentsView.isTaskViewFullyVisible(it) }

    /** Apply attachAlpha to all [TaskView] accordingly to different conditions. */
    fun applyAttachAlpha(nonRunningTaskCarouselHidden: Boolean) {
        taskViews.forEach { taskView ->
            taskView.attachAlpha =
                if (taskView == recentsView.runningTaskView) {
                    RUNNING_TASK_ATTACH_ALPHA.get(recentsView)
                } else {
                    if (
                        taskView.isVisibleInCarousel(
                            recentsView.runningTaskView,
                            nonRunningTaskCarouselHidden,
                        )
                    )
                        1f
                    else 0f
                }
        }
    }

    fun TaskView.isVisibleInCarousel(
        runningTaskView: TaskView?,
        nonRunningTaskCarouselHidden: Boolean,
    ): Boolean =
        if (!nonRunningTaskCarouselHidden) true
        else getCarouselType() == runningTaskView.getCarouselType()

    /** Returns the carousel type of the TaskView, and default to fullscreen if it's null. */
    private fun TaskView?.getCarouselType(): TaskViewCarousel =
        if (this is DesktopTaskView) TaskViewCarousel.DESKTOP else TaskViewCarousel.FULL_SCREEN

    private enum class TaskViewCarousel {
        FULL_SCREEN,
        DESKTOP,
    }

    /** Returns true if there are at least one TaskView has been added to the RecentsView. */
    fun hasTaskViews() = taskViews.any()

    fun getTaskContainerById(taskId: Int) =
        taskViews.firstNotNullOfOrNull { it.getTaskContainerById(taskId) }

    private fun getRowRect(firstView: View?, lastView: View?, outRowRect: Rect) {
        outRowRect.setEmpty()
        firstView?.let {
            it.getHitRect(TEMP_RECT)
            outRowRect.union(TEMP_RECT)
        }
        lastView?.let {
            it.getHitRect(TEMP_RECT)
            outRowRect.union(TEMP_RECT)
        }
    }

    private fun getRowRect(rowTaskViewIds: IntArray, outRowRect: Rect) {
        if (rowTaskViewIds.isEmpty) {
            outRowRect.setEmpty()
            return
        }
        getRowRect(
            recentsView.getTaskViewFromTaskViewId(rowTaskViewIds.get(0)),
            recentsView.getTaskViewFromTaskViewId(rowTaskViewIds.get(rowTaskViewIds.size() - 1)),
            outRowRect,
        )
    }

    fun updateTaskViewDeadZoneRect(
        outTaskViewRowRect: Rect,
        outTopRowRect: Rect,
        outBottomRowRect: Rect,
    ) {
        if (!getDeviceProfile().deviceProperties.isTablet) {
            getRowRect(getFirstTaskView(), getLastTaskView(), outTaskViewRowRect)
            return
        }
        getRowRect(getFirstLargeTaskView(), getLastLargeTaskView(), outTaskViewRowRect)
        getRowRect(getTopRowIdArray(), outTopRowRect)
        getRowRect(getBottomRowIdArray(), outBottomRowRect)

        // Expand large tile Rect to include space between top/bottom row.
        val nonEmptyRowRect =
            when {
                !outTopRowRect.isEmpty -> outTopRowRect
                !outBottomRowRect.isEmpty -> outBottomRowRect
                else -> return
            }
        if (recentsView.isRtl) {
            if (outTaskViewRowRect.left > nonEmptyRowRect.right) {
                outTaskViewRowRect.left = nonEmptyRowRect.right
            }
        } else {
            if (outTaskViewRowRect.right < nonEmptyRowRect.left) {
                outTaskViewRowRect.right = nonEmptyRowRect.left
            }
        }

        // Expand the shorter row Rect to include the space between the 2 rows.
        if (outTopRowRect.isEmpty || outBottomRowRect.isEmpty) return
        if (outTopRowRect.width() <= outBottomRowRect.width()) {
            if (outTopRowRect.bottom < outBottomRowRect.top) {
                outTopRowRect.bottom = outBottomRowRect.top
            }
        } else {
            if (outBottomRowRect.top > outTopRowRect.bottom) {
                outBottomRowRect.top = outTopRowRect.bottom
            }
        }
    }

    private fun getTaskMenu(): TaskMenuView? =
        getTopOpenViewWithType(recentsView.mContainer, TYPE_TASK_MENU) as? TaskMenuView

    fun taskMenuIsOpen(): Boolean {
        if (enableOverviewIconMenu()) {
            return getTaskMenu()?.isOpen == true
        }
        return false
    }

    fun updateChildTaskOrientations() {
        with(recentsView) {
            taskViews.forEach { it.setOrientationState(mOrientationState) }
            if (enableOverviewIconMenu()) {
                children.forEach {
                    it.layoutDirection = if (isRtl) LAYOUT_DIRECTION_LTR else LAYOUT_DIRECTION_RTL
                }
            }

            // Return when it's not fake landscape
            if (mOrientationState.isRecentsActivityRotationAllowed) return@with

            // Rotation is supported on phone (details at b/254198019#comment4)
            getTaskMenu()?.onRotationChanged()
        }
    }

    fun updateCentralTask() {
        val isTablet: Boolean = getDeviceProfile().deviceProperties.isTablet
        val actionsViewCanRelateToTaskView = !(isTablet && enableGridOnlyOverview())
        val focusedTaskView = recentsView.focusedTaskView
        val currentPageTaskView = recentsView.currentPageTaskView

        fun isInExpectedScrollPosition(taskView: TaskView?) =
            taskView?.let { recentsView.isTaskInExpectedScrollPosition(it) } ?: false

        val centralTaskIds: Set<Int> =
            when {
                !actionsViewCanRelateToTaskView -> emptySet()
                isTablet && isInExpectedScrollPosition(focusedTaskView) ->
                    focusedTaskView!!.taskIdSet
                isInExpectedScrollPosition(currentPageTaskView) -> currentPageTaskView!!.taskIdSet
                else -> emptySet()
            }

        recentsView.mRecentsViewModel.updateCentralTaskIds(centralTaskIds)
    }

    var deskExplodeProgress: Float = 0f
        set(value) {
            field = value
            taskViews.filterIsInstance<DesktopTaskView>().forEach { it.explodeProgress = field }
        }

    var selectedTaskView: TaskView? = null
        set(newValue) {
            val oldValue = field
            field = newValue
            if (oldValue != newValue) {
                onSelectedTaskViewUpdated(oldValue, newValue)
            }
        }

    private fun onSelectedTaskViewUpdated(
        oldSelectedTaskView: TaskView?,
        newSelectedTaskView: TaskView?,
    ) {
        if (!enableGridOnlyOverview()) return
        with(recentsView) {
            oldSelectedTaskView?.modalScale = 1f
            oldSelectedTaskView?.modalPivot = null

            if (newSelectedTaskView == null) return

            val modalTaskBounds = mTempRect
            getModalTaskSize(modalTaskBounds)
            val selectedTaskBounds = getTaskBounds(newSelectedTaskView)

            // Map bounds to selectedTaskView's coordinate system.
            modalTaskBounds.offset(-selectedTaskBounds.left, -selectedTaskBounds.top)
            selectedTaskBounds.offset(-selectedTaskBounds.left, -selectedTaskBounds.top)

            val modalScale =
                min(
                    (modalTaskBounds.height().toFloat() / selectedTaskBounds.height()),
                    (modalTaskBounds.width().toFloat() / selectedTaskBounds.width()),
                )
            val modalPivot = PointF()
            getPivotsForScalingRectToRect(modalTaskBounds, selectedTaskBounds, modalPivot)

            newSelectedTaskView.modalScale = modalScale
            newSelectedTaskView.modalPivot = modalPivot
        }
    }

    /**
     * Creates a [DesktopTaskView] for the currently active desk on this display, which contains the
     * tasks with the given [groupedTaskInfo].
     */
    fun createDesktopTaskViewForActiveDesk(groupedTaskInfo: GroupedTaskInfo): DesktopTaskView {
        val desktopTaskView =
            recentsView.getTaskViewFromPool(TaskViewType.DESKTOP) as DesktopTaskView
        val tasks: List<Task> = groupedTaskInfo.taskInfoList.map { taskInfo -> Task.from(taskInfo) }
        desktopTaskView.bind(
            DesktopTask(groupedTaskInfo.deskId, groupedTaskInfo.deskDisplayId, tasks),
            recentsView.mOrientationState,
            recentsView.mTaskOverlayFactory,
        )
        return desktopTaskView
    }

    fun getRunningTaskViewFromGroupTaskInfo(groupedTaskInfo: GroupedTaskInfo) =
        if (enableMultipleDesktops(recentsView.context)) {
            if (groupedTaskInfo.isBaseType(GroupedTaskInfo.TYPE_DESK)) {
                getDesktopTaskViewForDeskId(groupedTaskInfo.deskId)
            } else {
                val runningTaskIds = groupedTaskInfo.taskInfoList.map { it.taskId }.toIntArray()
                val taskView = recentsView.getTaskViewByTaskIds(runningTaskIds)
                if (taskView?.type == groupedTaskInfo.getTaskViewType()) taskView else null
            }
        } else {
            if (
                groupedTaskInfo.isBaseType(GroupedTaskInfo.TYPE_DESK) &&
                    groupedTaskInfo.taskInfoList.size == 1
            ) {
                recentsView.getTaskViewByTaskId(groupedTaskInfo.taskInfo1!!.taskId)
                    as? DesktopTaskView
            } else {
                val runningTaskIds = groupedTaskInfo.taskInfoList.map { it.taskId }.toIntArray()
                recentsView.getTaskViewByTaskIds(runningTaskIds)
            }
        }

    private fun GroupedTaskInfo.getTaskViewType() =
        when {
            isBaseType(GroupedTaskInfo.TYPE_FULLSCREEN) -> TaskViewType.SINGLE
            isBaseType(GroupedTaskInfo.TYPE_SPLIT) -> TaskViewType.GROUPED
            isBaseType(GroupedTaskInfo.TYPE_DESK) -> TaskViewType.DESKTOP
            else -> null
        }

    fun onPrepareGestureEndAnimation(
        animatorSet: AnimatorSet,
        endTarget: GestureState.GestureEndTarget,
        remoteTargetHandles: Array<RemoteTargetHandle>,
        isHandlingAtomicEvent: Boolean,
    ) {
        // Create ObjectAnimator that immediately settles on [endStateValue] when
        // [isHandlingAtomicEvent] is true.
        fun <T> immediateObjectAnimator(
            target: T,
            property: Property<T, Float>,
            endStateValue: Float,
        ) =
            if (isHandlingAtomicEvent)
                ObjectAnimator.ofFloat(target, property, endStateValue, endStateValue)
            else ObjectAnimator.ofFloat(target, property, endStateValue)

        with(recentsView) {
            Log.d(TAG, "onPrepareGestureEndAnimation - endTarget: $endTarget")
            mCurrentGestureEndTarget = endTarget
            val endState: BaseState<*> = mContainerInterface.stateFromGestureEndTarget(endTarget)

            // Starting the desk exploded animation when the gesture from an app is released.
            if (enableDesktopExplodedView()) {
                animatorSet.play(
                    ObjectAnimator.ofFloat(
                        this,
                        DESK_EXPLODE_PROGRESS,
                        if (endState.showExplodedDesktopView()) 1f else 0f,
                    )
                )
                taskViews.filterIsInstance<DesktopTaskView>().forEach {
                    it.remoteTargetHandles = remoteTargetHandles
                }
            }

            if (endState.displayOverviewTasksAsGrid(getDeviceProfile())) {
                updateGridProperties()
                animatorSet.play(immediateObjectAnimator(this, RECENTS_GRID_PROGRESS, 1f))

                val runningTaskView = runningTaskView
                var runningTaskGridTranslationX = 0f
                var runningTaskGridTranslationY = 0f
                if (runningTaskView != null) {
                    // Apply the grid translation to running task unless it's being snapped to
                    // and removes the current translation applied to the running task.
                    runningTaskGridTranslationX =
                        (runningTaskView.gridTranslationX - runningTaskView.nonGridTranslationX)
                    runningTaskGridTranslationY = runningTaskView.gridTranslationY
                }
                remoteTargetHandles.forEach { remoteTargetHandle ->
                    val taskViewSimulator = remoteTargetHandle.taskViewSimulator
                    if (enableGridOnlyOverview()) {
                        animatorSet.play(taskViewSimulator.carouselScale.animateToValue(1f))
                        animatorSet.play(
                            taskViewSimulator.taskGridTranslationX.animateToValue(
                                runningTaskGridTranslationX
                            )
                        )
                        animatorSet.play(
                            taskViewSimulator.taskGridTranslationY.animateToValue(
                                runningTaskGridTranslationY
                            )
                        )
                    } else {
                        animatorSet.play(
                            taskViewSimulator.taskPrimaryTranslation.animateToValue(
                                runningTaskGridTranslationX
                            )
                        )
                        animatorSet.play(
                            taskViewSimulator.taskSecondaryTranslation.animateToValue(
                                runningTaskGridTranslationY
                            )
                        )
                    }
                }
            }
            animatorSet.play(
                immediateObjectAnimator(
                    this,
                    TASK_THUMBNAIL_SPLASH_ALPHA,
                    if (endState.showTaskThumbnailSplash()) 1f else 0f,
                )
            )
            if (enableLargeDesktopWindowingTile()) {
                animatorSet.play(ObjectAnimator.ofFloat(this, DESKTOP_CAROUSEL_DETACH_PROGRESS, 0f))
            }

            if (enableGridOnlyOverview()) {
                // Reload visible tasks according to new [mCurrentGestureEndTarget] value.
                loadVisibleTaskData(FLAG_UPDATE_ALL)
            }
        }
    }

    fun resetShareUIState() {
        taskViews.flatMap { it.taskContainers }.forEach { it.overlay.resetShareUI() }
    }

    /**
     * Adds a listener to be notified when a new desk is added.
     *
     * @param onDeskAddedListener The listener to add.
     */
    fun addOnDeskAddedListener(onDeskAddedListener: OnDeskAddedListener) {
        onDeskAddedListeners += onDeskAddedListener
    }

    /**
     * Removes a listener that was previously added to be notified when a new desk is added.
     *
     * @param onDeskAddedListener The listener to remove.
     */
    fun removeOnDeskAddedListener(onDeskAddedListener: OnDeskAddedListener) {
        onDeskAddedListeners -= onDeskAddedListener
    }

    companion object {
        class RecentsViewFloatProperty(
            private val utilsProperty: KMutableProperty1<RecentsViewUtils, Float>
        ) : FloatProperty<RecentsView<*, *>>(utilsProperty.name) {
            override fun get(recentsView: RecentsView<*, *>): Float =
                utilsProperty.get(recentsView.mUtils)

            override fun setValue(recentsView: RecentsView<*, *>, value: Float) {
                utilsProperty.set(recentsView.mUtils, value)
            }
        }

        @JvmField
        val DESK_EXPLODE_PROGRESS = RecentsViewFloatProperty(RecentsViewUtils::deskExplodeProgress)

        val TEMP_RECT = Rect()
    }
}
