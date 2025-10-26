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

import android.graphics.PointF
import android.graphics.Rect
import android.util.FloatProperty
import android.view.KeyEvent
import android.view.View
import android.view.View.LAYOUT_DIRECTION_LTR
import android.view.View.LAYOUT_DIRECTION_RTL
import androidx.core.view.children
import com.android.launcher3.AbstractFloatingView.TYPE_TASK_MENU
import com.android.launcher3.AbstractFloatingView.getTopOpenViewWithType
import com.android.launcher3.Flags.enableGridOnlyOverview
import com.android.launcher3.Flags.enableLargeDesktopWindowingTile
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.Flags.enableSeparateExternalDisplayTasks
import com.android.launcher3.Utilities.getPivotsForScalingRectToRect
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.statehandlers.DesktopVisibilityController.Companion.INACTIVE_DESK_ID
import com.android.launcher3.util.IntArray
import com.android.quickstep.util.DesksUtils.Companion.areMultiDesksFlagsEnabled
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.isExternalDisplay
import com.android.quickstep.views.RecentsView.RUNNING_TASK_ATTACH_ALPHA
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.wm.shell.shared.GroupedTaskInfo
import java.util.function.BiConsumer
import kotlin.math.min
import kotlin.reflect.KMutableProperty1

/**
 * Helper class for [RecentsView]. This util class contains refactored and extracted functions from
 * RecentsView to facilitate the implementation of unit tests.
 */
class RecentsViewUtils(private val recentsView: RecentsView<*, *>) {
    val taskViews = TaskViewsIterable(recentsView)

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

    /** Returns all the TaskViews in the top row, without the focused task */
    fun getTopRowTaskViews(): List<TaskView> =
        taskViews.filter { recentsView.mTopRowIdSet.contains(it.taskViewId) }

    /** Returns all the task Ids in the top row, without the focused task */
    fun getTopRowIdArray(): IntArray = getTopRowTaskViews().map { it.taskViewId }.toIntArray()

    /** Returns all the TaskViews in the bottom row, without the focused task */
    fun getBottomRowTaskViews(): List<TaskView> =
        taskViews.filter { !recentsView.mTopRowIdSet.contains(it.taskViewId) && !it.isLargeTile }

    /** Returns all the task Ids in the bottom row, without the focused task */
    fun getBottomRowIdArray(): IntArray = getBottomRowTaskViews().map { it.taskViewId }.toIntArray()

    private fun List<Int>.toIntArray() = IntArray(size).apply { this@toIntArray.forEach(::add) }

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
                    !(enableSeparateExternalDisplayTasks() && it.isExternalDisplay)
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
        return if (getDeviceProfile().isTablet) {
            var index = firstTaskViewIndex
            if (enableLargeDesktopWindowingTile() && runningTaskView !is DesktopTaskView) {
                // For fullsreen tasks, skip over Desktop tasks in its section
                index +=
                    if (enableSeparateExternalDisplayTasks()) {
                        if (runningTaskView.isExternalDisplay) {
                            taskViews.count { it is DesktopTaskView && it.isExternalDisplay }
                        } else {
                            taskViews.count { it is DesktopTaskView && !it.isExternalDisplay }
                        }
                    } else {
                        getDesktopTaskViewCount()
                    }
            }
            if (enableSeparateExternalDisplayTasks() && !runningTaskView.isExternalDisplay) {
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
        if (!getDeviceProfile().isTablet) {
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

    fun shouldInterceptKeyEvent(event: KeyEvent): Boolean {
        if (enableOverviewIconMenu()) {
            return getTaskMenu()?.isOpen == true || event.keyCode == KeyEvent.KEYCODE_TAB
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
        val isTablet: Boolean = getDeviceProfile().isTablet
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
