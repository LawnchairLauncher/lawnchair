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

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewStub
import com.android.internal.jank.Cuj
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.util.RecentsOrientedState
import com.android.quickstep.util.SplitSelectStateController
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import com.android.wm.shell.Flags.enableFlexibleTwoAppSplit
import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition

/**
 * TaskView that contains and shows thumbnails for not one, BUT TWO(!!) tasks
 *
 * That's right. If you call within the next 5 minutes we'll go ahead and double your order and send
 * you !! TWO !! Tasks along with their TaskThumbnailViews complimentary. On. The. House. And not
 * only that, we'll even clean up your thumbnail request if you don't like it. All the benefits of
 * one TaskView, except DOUBLED!
 *
 * (Icon loading sold separately, fees may apply. Shipping & Handling for Overlays not included).
 */
class GroupedTaskView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    TaskView(context, attrs, type = TaskViewType.GROUPED) {

    private val MINIMUM_RATIO_TO_SHOW_ICON = 0.2f

    val leftTopTaskContainer: TaskContainer
        get() = taskContainers[0]

    val rightBottomTaskContainer: TaskContainer
        get() = taskContainers[1]

    // TODO(b/336612373): Support new TTV for GroupedTaskView
    var splitBoundsConfig: SplitConfigurationOptions.SplitBounds? = null
        private set

    @get:PersistentSnapPosition
    val snapPosition: Int
        /** Returns the [PersistentSnapPosition] of this pair of tasks. */
        get() = splitBoundsConfig?.snapPosition ?: STAGE_POSITION_UNDEFINED

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(widthSize, heightSize)
        val splitBoundsConfig = splitBoundsConfig ?: return
        val inSplitSelection = getThisTaskCurrentlyInSplitSelection() != INVALID_TASK_ID
        pagedOrientationHandler.measureGroupedTaskViewThumbnailBounds(
            leftTopTaskContainer.snapshotView,
            rightBottomTaskContainer.snapshotView,
            widthSize,
            heightSize,
            splitBoundsConfig,
            container.deviceProfile,
            layoutDirection == LAYOUT_DIRECTION_RTL,
            inSplitSelection,
        )

        if (!enableOverviewIconMenu()) {
            updateIconPlacement()
        }
    }

    override fun inflateViewStubs() {
        super.inflateViewStubs()
        findViewById<ViewStub>(R.id.bottomright_snapshot)
            ?.apply {
                layoutResource =
                    if (enableRefactorTaskThumbnail()) R.layout.task_thumbnail
                    else R.layout.task_thumbnail_deprecated
            }
            ?.inflate()
        findViewById<ViewStub>(R.id.bottomRight_icon)
            ?.apply {
                layoutResource =
                    if (enableOverviewIconMenu()) R.layout.icon_app_chip_view
                    else R.layout.icon_view
            }
            ?.inflate()
    }

    override fun onRecycle() {
        super.onRecycle()
        splitBoundsConfig = null
    }

    fun bind(
        primaryTask: Task,
        secondaryTask: Task,
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory,
        splitBoundsConfig: SplitConfigurationOptions.SplitBounds?,
    ) {
        cancelPendingLoadTasks()
        taskContainers =
            listOf(
                createTaskContainer(
                    primaryTask,
                    R.id.snapshot,
                    R.id.icon,
                    R.id.show_windows,
                    R.id.digital_wellbeing_toast,
                    STAGE_POSITION_TOP_OR_LEFT,
                    taskOverlayFactory,
                ),
                createTaskContainer(
                    secondaryTask,
                    R.id.bottomright_snapshot,
                    R.id.bottomRight_icon,
                    R.id.show_windows_right,
                    R.id.bottomRight_digital_wellbeing_toast,
                    STAGE_POSITION_BOTTOM_OR_RIGHT,
                    taskOverlayFactory,
                ),
            )
        this.splitBoundsConfig = splitBoundsConfig
        taskContainers.forEach { it.digitalWellBeingToast?.splitBounds = splitBoundsConfig }
        onBind(orientedState)
    }

    override fun setOrientationState(orientationState: RecentsOrientedState) {
        if (enableOverviewIconMenu()) {
            splitBoundsConfig?.let {
                val groupedTaskViewSizes =
                    orientationState.orientationHandler.getGroupedTaskViewSizes(
                        container.deviceProfile,
                        it,
                        layoutParams.width,
                        layoutParams.height,
                    )
                val iconViewMarginStart =
                    resources.getDimensionPixelSize(
                        R.dimen.task_thumbnail_icon_menu_expanded_top_start_margin
                    )
                val iconViewBackgroundMarginStart =
                    resources.getDimensionPixelSize(
                        R.dimen.task_thumbnail_icon_menu_background_margin_top_start
                    )
                val iconMargins = (iconViewMarginStart + iconViewBackgroundMarginStart) * 2
                // setMaxWidth() needs to be called before mIconView.setIconOrientation which is
                // called in the super below.
                (leftTopTaskContainer.iconView as IconAppChipView).maxWidth =
                    groupedTaskViewSizes.first.x - iconMargins
                (rightBottomTaskContainer.iconView as IconAppChipView).maxWidth =
                    groupedTaskViewSizes.second.x - iconMargins
            }
        }
        super.setOrientationState(orientationState)
        updateIconPlacement()
    }

    private fun updateIconPlacement() {
        val splitBoundsConfig = splitBoundsConfig ?: return
        val deviceProfile = container.deviceProfile
        val taskIconHeight = deviceProfile.overviewTaskIconSizePx
        val inSplitSelection = getThisTaskCurrentlyInSplitSelection() != INVALID_TASK_ID
        var oneIconHiddenDueToSmallWidth = false

        if (enableFlexibleTwoAppSplit()) {
            // Update values for both icons' setFlexSplitAlpha. Mainly, we want to hide an icon if
            // its app tile is too small. But we also have to set the alphas back if we go to
            // split selection.
            val hideLeftTopIcon: Boolean
            val hideRightBottomIcon: Boolean
            if (inSplitSelection) {
                hideLeftTopIcon =
                    getThisTaskCurrentlyInSplitSelection() == splitBoundsConfig.leftTopTaskId
                hideRightBottomIcon =
                    getThisTaskCurrentlyInSplitSelection() == splitBoundsConfig.rightBottomTaskId
            } else {
                hideLeftTopIcon = splitBoundsConfig.leftTopTaskPercent < MINIMUM_RATIO_TO_SHOW_ICON
                hideRightBottomIcon =
                    splitBoundsConfig.rightBottomTaskPercent < MINIMUM_RATIO_TO_SHOW_ICON
                if (hideLeftTopIcon || hideRightBottomIcon) {
                    oneIconHiddenDueToSmallWidth = true
                }
            }

            leftTopTaskContainer.iconView.setFlexSplitAlpha(if (hideLeftTopIcon) 0f else 1f)
            rightBottomTaskContainer.iconView.setFlexSplitAlpha(if (hideRightBottomIcon) 0f else 1f)
        }

        if (enableOverviewIconMenu()) {
            val isDeviceRtl = Utilities.isRtl(resources)
            val groupedTaskViewSizes =
                pagedOrientationHandler.getGroupedTaskViewSizes(
                    deviceProfile,
                    splitBoundsConfig,
                    layoutParams.width,
                    layoutParams.height,
                )
            pagedOrientationHandler.setSplitIconParams(
                leftTopTaskContainer.iconView.asView(),
                rightBottomTaskContainer.iconView.asView(),
                taskIconHeight,
                groupedTaskViewSizes.first.x,
                groupedTaskViewSizes.first.y,
                layoutParams.height,
                layoutParams.width,
                isDeviceRtl,
                deviceProfile,
                splitBoundsConfig,
                inSplitSelection,
                oneIconHiddenDueToSmallWidth,
            )
        } else {
            pagedOrientationHandler.setSplitIconParams(
                leftTopTaskContainer.iconView.asView(),
                rightBottomTaskContainer.iconView.asView(),
                taskIconHeight,
                leftTopTaskContainer.snapshotView.measuredWidth,
                leftTopTaskContainer.snapshotView.measuredHeight,
                measuredHeight,
                measuredWidth,
                isLayoutRtl,
                deviceProfile,
                splitBoundsConfig,
                inSplitSelection,
                oneIconHiddenDueToSmallWidth,
            )
        }
    }

    fun updateSplitBoundsConfig(splitBounds: SplitConfigurationOptions.SplitBounds?) {
        splitBoundsConfig = splitBounds
        taskContainers.forEach {
            it.digitalWellBeingToast?.splitBounds = splitBoundsConfig
            it.digitalWellBeingToast?.initialize()
        }
        invalidate()
    }

    override fun launchAsStaticTile(): RunnableList? {
        val recentsView = recentsView ?: return null
        val endCallback = RunnableList()
        // Callbacks run from remote animation when recents animation not currently running
        InteractionJankMonitorWrapper.begin(
            this,
            Cuj.CUJ_SPLIT_SCREEN_ENTER,
            "Enter form GroupedTaskView",
        )
        launchTaskInternal(isQuickSwitch = false, launchingExistingTaskView = true) {
            endCallback.executeAllAndDestroy()
            InteractionJankMonitorWrapper.end(Cuj.CUJ_SPLIT_SCREEN_ENTER)
        }

        // Callbacks get run from recentsView for case when recents animation already running
        recentsView.addSideTaskLaunchCallback(endCallback)
        return endCallback
    }

    override fun launchWithoutAnimation(
        isQuickSwitch: Boolean,
        callback: (launched: Boolean) -> Unit,
    ) {
        launchTaskInternal(isQuickSwitch, launchingExistingTaskView = false, callback)
    }

    /**
     * @param launchingExistingTaskView [SplitSelectStateController.launchExistingSplitPair] uses
     *   existence of GroupedTaskView as control flow of how to animate in the incoming task. If
     *   we're launching from overview (from overview thumbnails) then pass in `true`, otherwise
     *   pass in `false` for case like quickswitching from home to task
     */
    private fun launchTaskInternal(
        isQuickSwitch: Boolean,
        launchingExistingTaskView: Boolean,
        callback: (launched: Boolean) -> Unit,
    ) {
        recentsView?.let {
            it.splitSelectController.launchExistingSplitPair(
                if (launchingExistingTaskView) this else null,
                leftTopTaskContainer.task.key.id,
                rightBottomTaskContainer.task.key.id,
                STAGE_POSITION_TOP_OR_LEFT,
                callback,
                isQuickSwitch,
                snapPosition,
            )
            Log.d(
                TAG,
                "launchTaskInternal - launchExistingSplitPair: ${taskIds.contentToString()}, launchingExistingTaskView: $launchingExistingTaskView",
            )
        }
    }

    /**
     * Returns taskId that split selection was initiated with, [INVALID_TASK_ID] if no tasks in this
     * TaskView are part of split selection
     */
    private fun getThisTaskCurrentlyInSplitSelection(): Int {
        val initialTaskId = recentsView?.splitSelectController?.initialTaskId
        return if (initialTaskId != null && containsTaskId(initialTaskId)) initialTaskId
        else INVALID_TASK_ID
    }

    override fun getLastSelectedChildTaskIndex(): Int {
        if (recentsView?.splitSelectController?.isDismissingFromSplitPair == true) {
            // return the container index of the task that wasn't initially selected to split
            // with because that is the only remaining app that can be selected. The coordinate
            // checks below aren't reliable since both of those views may be gone/transformed
            val initSplitTaskId = getThisTaskCurrentlyInSplitSelection()
            if (initSplitTaskId != INVALID_TASK_ID) {
                return if (initSplitTaskId == leftTopTaskContainer.task.key.id) 1 else 0
            }
        }

        // Check which of the two apps was selected
        if (
            rightBottomTaskContainer.iconView.asView().containsPoint(lastTouchDownPosition) ||
                rightBottomTaskContainer.snapshotView.containsPoint(lastTouchDownPosition)
        ) {
            return 1
        }
        return super.getLastSelectedChildTaskIndex()
    }

    private fun View.containsPoint(position: PointF): Boolean {
        val localPos = floatArrayOf(position.x, position.y)
        Utilities.mapCoordInSelfToDescendant(this, this@GroupedTaskView, localPos)
        return Utilities.pointInView(this, localPos[0], localPos[1], 0f /* slop */)
    }

    companion object {
        private const val TAG = "GroupedTaskView"
    }
}
