/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Rect.intersects
import android.graphics.RectF
import android.util.AttributeSet
import android.util.FloatProperty
import android.util.Log
import android.util.Size
import android.view.Display.INVALID_DISPLAY
import android.view.Gravity
import android.view.View
import android.view.ViewStub
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import com.android.internal.hidden_from_bootclasspath.com.android.window.flags.Flags.enableDesktopRecentsTransitionsCornersBugfix
import com.android.launcher3.Flags.enableDesktopExplodedView
import com.android.launcher3.Flags.enableRefactorTaskContentView
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.R
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.testing.TestLogging
import com.android.launcher3.testing.shared.TestProtocol
import com.android.launcher3.util.KFloatProperty
import com.android.launcher3.util.OverviewReleaseFlags.enableOverviewIconMenu
import com.android.launcher3.Utilities
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.launcher3.util.ViewPool
import com.android.launcher3.util.rects.lerpRect
import com.android.launcher3.util.rects.set
import com.android.quickstep.BaseContainerInterface
import com.android.quickstep.DesktopFullscreenDrawParams
import com.android.quickstep.FullscreenDrawParams
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.ViewUtils
import com.android.quickstep.recents.di.RecentsDependencies
import com.android.quickstep.recents.di.get
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData
import com.android.quickstep.recents.ui.viewmodel.DesktopTaskViewModel
import com.android.quickstep.recents.ui.viewmodel.TaskData
import com.android.quickstep.task.thumbnail.TaskContentView
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.RecentsOrientedState
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus.enableMultipleDesktops
import kotlin.math.roundToInt

import app.lawnchair.theme.color.tokens.ColorTokens

/** TaskView that contains all tasks that are part of the desktop. */
class DesktopTaskView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    TaskView(
        context,
        attrs,
        type = TaskViewType.DESKTOP,
        thumbnailFullscreenParams = DesktopFullscreenDrawParams(context),
    ) {
    private val desktopTask: DesktopTask?
        get() = groupTask as? DesktopTask

    val deskId
        get() = desktopTask?.deskId ?: DesktopVisibilityController.INACTIVE_DESK_ID

    private val contentViewFullscreenParams = FullscreenDrawParams(context)

    private val taskThumbnailViewDeprecatedPool =
        if (!enableRefactorTaskThumbnail()) {
            ViewPool<TaskThumbnailViewDeprecated>(
                context,
                this,
                R.layout.task_thumbnail_deprecated,
                VIEW_POOL_MAX_SIZE,
                VIEW_POOL_INITIAL_SIZE,
            )
        } else null

    private val taskThumbnailViewPool =
        if (enableRefactorTaskThumbnail()) {
            ViewPool<TaskThumbnailView>(
                context,
                this,
                R.layout.task_thumbnail,
                VIEW_POOL_MAX_SIZE,
                VIEW_POOL_INITIAL_SIZE,
            )
        } else null

    private val taskContentViewPool =
        if (enableRefactorTaskContentView()) {
            ViewPool<TaskContentView>(
                context,
                this,
                R.layout.task_content_view,
                VIEW_POOL_MAX_SIZE,
                VIEW_POOL_INITIAL_SIZE,
            )
        } else null

    private val tempPointF = PointF()
    private val lastComputedTaskSize = Rect()
    private lateinit var iconView: TaskViewIcon
    private lateinit var contentView: DesktopTaskContentView
    private lateinit var backgroundView: View

    private var viewModel: DesktopTaskViewModel? = null

    /**
     * Holds the default (user placed) positions of task windows. This can be moved into the
     * viewModel once RefactorTaskThumbnail has been launched.
     */
    private var fullscreenTaskPositions: List<DesktopTaskBoundsData> = emptyList()

    /**
     * Holds the previous organized task positions. This is used to animate between two sets of
     * organized task positions when a task is being dismissed.
     */
    private var previousOrganizedDesktopTaskPositions: List<DesktopTaskBoundsData>? = null

    /**
     * When enableDesktopExplodedView is enabled, this controls the gradual transition from the
     * default positions to the organized non-overlapping positions.
     */
    var explodeProgress = 0.0f
        set(value) {
            field = value
            positionTaskWindows()
        }

    /**
     * When enableDesktopExplodedView is enabled, and a task is removed, this controls the gradual
     * transition from the previous organized task positions to the new.
     */
    private var taskRemoveProgress = 0.0f
        set(value) {
            field = value
            positionTaskWindows()
        }

    private var taskRemoveAnimator: ObjectAnimator? = null

    var remoteTargetHandles: Array<RemoteTargetHandle>? = null
        set(value) {
            field = value
            if (value != null) {
                positionTaskWindows()
            }
        }

    override val displayId: Int
        get() =
            if (enableMultipleDesktops(context)) {
                desktopTask?.displayId ?: INVALID_DISPLAY
            } else {
                super.displayId
            }

    private fun getRemoteTargetHandle(taskId: Int): RemoteTargetHandle? =
        remoteTargetHandles?.firstOrNull {
            it.transformParams.targetSet.firstAppTargetTaskId == taskId
        }

    override fun onFinishInflate() {
        super.onFinishInflate()
        contentView =
            findViewById<DesktopTaskContentView>(R.id.desktop_content).apply {
                updateLayoutParams<LayoutParams> {
                    topMargin = container.deviceProfile.overviewProfile.taskThumbnailTopMarginPx
                }
                cornerRadius = contentViewFullscreenParams.currentCornerRadius
                backgroundView = findViewById(R.id.background)
                backgroundView.setBackgroundColor(
                    resources.getColor(ColorTokens.Neutral2_300.resolveColor(context), context.theme)
                )
            }
    }

    override fun inflateViewStubs() {
        findViewById<ViewStub>(R.id.icon)
            ?.apply {
                layoutResource =
                    if (enableOverviewIconMenu()) R.layout.icon_app_chip_view
                    else R.layout.icon_view
            }
            ?.inflate()
    }

    private fun positionTaskWindows(updateLayout: Boolean = false) {
        if (taskContainers.isEmpty()) {
            return
        }

        val (widthScale, heightScale) = getScreenScaleFactors()
        taskContainers.forEach { taskContainer ->
            val taskId = taskContainer.task.key.id
            val fullscreenTaskBounds =
                fullscreenTaskPositions.firstOrNull { it.taskId == taskId }?.bounds ?: return
            val overviewTaskBounds =
                if (enableDesktopExplodedView()) {
                    viewModel!!
                        .organizedDesktopTaskPositions
                        .firstOrNull { it.taskId == taskId }
                        ?.bounds ?: fullscreenTaskBounds
                } else {
                    fullscreenTaskBounds
                }
            val currentTaskBounds =
                if (enableDesktopExplodedView()) {
                    TEMP_OVERVIEW_TASK_POSITION.apply {
                        // When removing a task, interpolate between its old organized bounds and
                        // [overviewTaskBounds].
                        val previousOrganizedTaskBounds =
                            previousOrganizedDesktopTaskPositions
                                ?.firstOrNull { it.taskId == taskId }
                                ?.bounds
                        if (previousOrganizedTaskBounds != null) {
                            lerpRect(
                                previousOrganizedTaskBounds,
                                overviewTaskBounds,
                                taskRemoveProgress,
                            )
                        } else {
                            set(overviewTaskBounds)
                        }
                        lerpRect(fullscreenTaskBounds, this, explodeProgress)
                    }
                } else {
                    fullscreenTaskBounds
                }

            if (enableDesktopExplodedView()) {
                getRemoteTargetHandle(taskId)?.let { remoteTargetHandle ->
                    val fromRect =
                        TEMP_FROM_RECTF.apply {
                            set(fullscreenTaskBounds)
                            scale(widthScale)
                            offset(
                                lastComputedTaskSize.left.toFloat(),
                                lastComputedTaskSize.top.toFloat(),
                            )
                        }
                    val toRect =
                        TEMP_TO_RECTF.apply {
                            set(currentTaskBounds)
                            scale(widthScale)
                            offset(
                                lastComputedTaskSize.left.toFloat(),
                                lastComputedTaskSize.top.toFloat(),
                            )
                        }
                    val transform = Matrix()
                    transform.setRectToRect(fromRect, toRect, Matrix.ScaleToFit.FILL)
                    remoteTargetHandle.taskViewSimulator.setTaskRectTransform(transform)
                    remoteTargetHandle.taskViewSimulator.apply(remoteTargetHandle.transformParams)
                }

                (taskContainer.taskContentView as? TaskContentView)?.setTaskHeaderAlpha(
                    explodeProgress
                )
            }

            val overviewTaskLeft = overviewTaskBounds.left * widthScale
            val overviewTaskTop = overviewTaskBounds.top * heightScale
            val overviewTaskWidth = overviewTaskBounds.width() * widthScale
            val overviewTaskHeight = overviewTaskBounds.height() * heightScale

            if (updateLayout) {
                // Position the task to the same position as it would be on the desktop
                taskContainer.taskContentView.updateLayoutParams<LayoutParams> {
                    gravity = Gravity.LEFT or Gravity.TOP
                    width = overviewTaskWidth.toInt()
                    height = overviewTaskHeight.toInt()
                    leftMargin = overviewTaskLeft.toInt()
                    topMargin = overviewTaskTop.toInt()
                }
            }

            if (enableDesktopRecentsTransitionsCornersBugfix() && enableRefactorTaskThumbnail()) {
                // When exploded view is disabled, these scale factors will be 1.0. This secondary
                // scale factor is needed because a scale transform is applied to the thumbnail.
                val thumbnailScaleWidth =
                    overviewTaskBounds.width().toFloat() / currentTaskBounds.width()
                val thumbnailScaleHeight =
                    overviewTaskBounds.height().toFloat() / currentTaskBounds.height()
                val screenRect = getScreenRect()
                val contentOutlineBounds =
                    if (intersects(currentTaskBounds, screenRect))
                        Rect(currentTaskBounds).apply {
                            intersectUnchecked(screenRect)
                            // Offset to 0,0 to transform into TaskThumbnailView's coordinate
                            // system.
                            offset(-currentTaskBounds.left, -currentTaskBounds.top)
                            left = (left * widthScale * thumbnailScaleWidth).roundToInt()
                            top = (top * heightScale * thumbnailScaleHeight).roundToInt()
                            right = (right * widthScale * thumbnailScaleWidth).roundToInt()
                            bottom = (bottom * heightScale * thumbnailScaleHeight).roundToInt()
                        }
                    else null

                if (enableRefactorTaskContentView()) {
                    (taskContainer.taskContentView as TaskContentView).outlineBounds =
                        contentOutlineBounds
                } else {
                    taskContainer.thumbnailView.outlineBounds = contentOutlineBounds
                }
            }

            val currentTaskLeft = currentTaskBounds.left * widthScale
            val currentTaskTop = currentTaskBounds.top * heightScale
            val currentTaskWidth = currentTaskBounds.width() * widthScale
            val currentTaskHeight = currentTaskBounds.height() * heightScale
            // During the animation, apply translation and scale such that the view is transformed
            // to where we want, without triggering layout.
            taskContainer.taskContentView.apply {
                pivotX = 0.0f
                pivotY = 0.0f
                translationX = currentTaskLeft - overviewTaskLeft
                translationY = currentTaskTop - overviewTaskTop
                scaleX = if (overviewTaskWidth != 0f) currentTaskWidth / overviewTaskWidth else 1f
                scaleY =
                    if (overviewTaskHeight != 0f) currentTaskHeight / overviewTaskHeight else 1f
            }

            if (taskContainer.task.isMinimized) {
                taskContainer.taskContentView.alpha = explodeProgress
            }
        }
    }

    /** Updates this desktop task to the gives task list defined in `tasks` */
    fun bind(
        desktopTask: DesktopTask,
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory,
    ) {
        this.groupTask = desktopTask
        // Minimized tasks are shown in Overview when exploded view is enabled.
        val tasks =
            if (enableDesktopExplodedView()) {
                desktopTask.tasks
            } else {
                desktopTask.tasks.filterNot { it.isMinimized }
            }
        if (DEBUG) {
            val sb = StringBuilder()
            sb.append("bind tasks=").append(tasks.size).append("\n")
            tasks.forEach { sb.append(" key=${it.key}\n") }
            Log.d(TAG, sb.toString())
        }

        iconView =
            (findViewById<View>(R.id.icon) as TaskViewIcon).apply {
                setIcon(
                    this,
                    ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.ic_desktop_with_bg,
                        context.theme,
                    ),
                )
                setText(resources.getText(R.string.recent_task_desktop))
            }

        cancelPendingLoadTasks()
        val backgroundViewIndex = contentView.indexOfChild(backgroundView)
        taskContainers =
            tasks.map { task ->
                val taskContentView =
                    when {
                        enableRefactorTaskContentView() -> taskContentViewPool!!.view
                        enableRefactorTaskThumbnail() -> taskThumbnailViewPool!!.view
                        else -> taskThumbnailViewDeprecatedPool!!.view
                    }
                contentView.addView(taskContentView, backgroundViewIndex + 1)
                val snapshotView =
                    if (enableRefactorTaskContentView()) {
                        taskContentView.findViewById<TaskThumbnailView>(R.id.snapshot)
                    } else {
                        taskContentView
                    }
                if (enableDesktopExplodedView()) {
                    taskContentView.setOnClickListener {
                        launchTaskWithDesktopController(animated = true, task.key.id)
                    }
                    if (taskContentView is TaskContentView) {
                        taskContentView.isFocusable = true
                        taskContentView.isHoverable = true
                    }
                }

                TaskContainer(
                    this,
                    task,
                    taskContentView,
                    snapshotView,
                    iconView,
                    TransformingTouchDelegate(iconView.asView()),
                    SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
                    digitalWellBeingToast = null,
                    showWindowsView = null,
                    taskOverlayFactory,
                )
            }
        onBind(orientedState)
    }

    override fun onBind(orientedState: RecentsOrientedState) {
        super.onBind(orientedState)

        if (enableRefactorTaskThumbnail()) {
            viewModel =
                DesktopTaskViewModel(organizeDesktopTasksUseCase = RecentsDependencies.get(context))
        }
    }

    override fun onRecycle() {
        super.onRecycle()
        explodeProgress = 0.0f
        taskRemoveProgress = 0.0f
        previousOrganizedDesktopTaskPositions = null
        viewModel = null
        visibility = VISIBLE
        taskContainers.forEach { removeAndRecycleThumbnailView(it) }
        if (enableOverviewIconMenu()) {
            (iconView as IconAppChipView).reset()
        }
        remoteTargetHandles = null
    }

    override fun setOrientationState(orientationState: RecentsOrientedState) {
        super.setOrientationState(orientationState)
        iconView.setIconOrientation(orientationState, isGridTask)
    }

    @SuppressLint("RtlHardcoded")
    override fun updateTaskSize(lastComputedTaskSize: Rect, lastComputedGridTaskSize: Rect) {
        super.updateTaskSize(lastComputedTaskSize, lastComputedGridTaskSize)
        this.lastComputedTaskSize.set(lastComputedTaskSize)

        updateTaskPositions()
    }

    override fun onTaskListVisibilityChanged(visible: Boolean, changes: Int) {
        super.onTaskListVisibilityChanged(visible, changes)
        if (needsUpdate(changes, FLAG_UPDATE_CORNER_RADIUS)) {
            contentViewFullscreenParams.updateCornerRadius(context)
        }
    }

    override fun onIconLoaded(taskContainer: TaskContainer) {
        // Update contentDescription of snapshotView only, individual task icon is unused.
        taskContainer.snapshotView.contentDescription = taskContainer.task.titleDescription
    }

    override fun setIconState(container: TaskContainer, state: TaskData?) {
        container.snapshotView.contentDescription = (state as? TaskData.Data)?.titleDescription
    }

    // Ignoring [onIconUnloaded] as all tasks shares the same Desktop icon
    override fun onIconUnloaded(taskContainer: TaskContainer) {}

    // thumbnailView is laid out differently and is handled in onMeasure
    override fun updateThumbnailSize() {}

    override fun getThumbnailBounds(bounds: Rect, relativeToDragLayer: Boolean) {
        if (relativeToDragLayer) {
            container.dragLayer.getDescendantRectRelativeToSelf(contentView, bounds)
        } else {
            bounds.set(contentView)
        }
    }

    /**
     * Launches the desktop task and activate the task with [taskIdToReorderToFront] if it's
     * provided and already on the desktop. It will exit Overview to desktop and activate the
     * according new task afterwards if applicable.
     */
    private fun launchTaskWithDesktopController(
        animated: Boolean,
        taskIdToReorderToFront: Int? = null,
    ): RunnableList? {
        val recentsView = recentsView ?: return null
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "launchDesktopFromRecents",
            taskIds.contentToString(),
        )
        val endCallback = RunnableList()
        val desktopController = recentsView.desktopRecentsController
        checkNotNull(desktopController) { "recentsController is null" }

        if (taskIdToReorderToFront != null) {
            // The to-be-activated window should animate on top of other apps during shell
            // transition.
            val remoteTargetHandle = getRemoteTargetHandle(taskIdToReorderToFront)
            // The layer swapping is only applied after [createRecentsWindowAnimator] starts, which
            // will bring the [remoteTargetHandles] above Recents, therefore this call won't affect
            // the base surface in [DepthController].
            remoteTargetHandle?.taskViewSimulator?.setDrawsAboveOtherApps(true)
        }
        val launchDesktopFromRecents = {
            desktopController.launchDesktopFromRecents(this, animated, taskIdToReorderToFront) {
                endCallback.executeAllAndDestroy()
            }
        }
        if (enableMultipleDesktops(context) && desktopTask?.tasks?.isEmpty() == true) {
            recentsView.switchToScreenshot {
                recentsView.finishRecentsAnimation(
                    /* toRecents= */ true,
                    /* shouldPip= */ false,
                    launchDesktopFromRecents,
                )
            }
        } else {
            launchDesktopFromRecents()
        }
        Log.d(
            TAG,
            "launchTaskWithDesktopController: ${taskIds.contentToString()}, withRemoteTransition: $animated",
        )

        // Callbacks get run from recentsView for case when recents animation already running
        recentsView.addSideTaskLaunchCallback(endCallback)
        return endCallback
    }

    override fun launchAsStaticTile() = launchTaskWithDesktopController(animated = true)

    override fun launchWithoutAnimation(
        isQuickSwitch: Boolean,
        callback: (launched: Boolean) -> Unit,
    ) = launchTaskWithDesktopController(animated = false)?.add { callback(true) } ?: callback(false)

    // Return true when Task cannot be launched as fullscreen (i.e. in split select state) to skip
    // putting DesktopTaskView to split as it's not supported.
    override fun confirmSecondSplitSelectApp(): Boolean =
        recentsView?.canLaunchFullscreenTask() != true

    // TODO(b/330685808) support overlay for Screenshot action
    override fun setOverlayEnabled(overlayEnabled: Boolean) {}

    override fun onFullscreenProgressChanged(fullscreenProgress: Float) {
        backgroundView.alpha = 1 - fullscreenProgress
        updateSettledProgressFullscreen(fullscreenProgress)
    }

    override fun updateFullscreenParams() {
        super.updateFullscreenParams()
        updateFullscreenParams(contentViewFullscreenParams)
        contentView.cornerRadius = contentViewFullscreenParams.currentCornerRadius
    }

    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        super.addChildrenForAccessibility(outChildren)
        ViewUtils.addAccessibleChildToList(backgroundView, outChildren)
    }

    fun removeTaskFromExplodedView(taskId: Int, animate: Boolean) {
        if (!enableDesktopExplodedView()) {
            Log.e(
                TAG,
                "removeTaskFromExplodedView called when enableDesktopExplodedView flag is false",
            )
            return
        }

        // Remove the task's [taskContainer] and its associated Views.
        val taskContainer = getTaskContainerById(taskId) ?: return
        removeAndRecycleThumbnailView(taskContainer)
        taskContainer.destroy()
        taskContainers = taskContainers.filterNot { it == taskContainer }

        // Dismiss the current DesktopTaskView if all its windows are closed.
        if (taskContainers.isEmpty()) {
            recentsView?.dismissTaskView(this, animate, /* removeTask= */ true)
        } else {
            // If this task has a live window, then hide it.
            // TODO(b/413120214) The dismissed view should fade out.
            getRemoteTargetHandle(taskId)?.let {
                it.taskViewSimulator.setTaskRectTransform(Matrix().apply { postScale(0.0f, 0.0f) })
                it.taskViewSimulator.apply(it.transformParams)
            }

            // TODO(b/413130378) Nicer handling of multiple quick task dismissals.
            taskRemoveAnimator?.cancel()
            taskRemoveAnimator =
                ObjectAnimator.ofFloat(this, TASK_REMOVE_PROGRESS, 0f, 1f).apply {
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animator: Animator) {
                                previousOrganizedDesktopTaskPositions = null
                                taskRemoveAnimator = null
                            }
                        }
                    )
                    start()
                }

            // Store the current organized positions before computing new ones. This allows us to
            // animate from the current layout to the new.
            previousOrganizedDesktopTaskPositions = viewModel!!.organizedDesktopTaskPositions
            updateTaskPositions()
        }
    }

    private fun removeAndRecycleThumbnailView(taskContainer: TaskContainer) {
        contentView.removeView(taskContainer.taskContentView)
        when {
            enableRefactorTaskContentView() ->
                taskContentViewPool!!.recycle(taskContainer.taskContentView as TaskContentView)
            enableRefactorTaskThumbnail() ->
                taskThumbnailViewPool!!.recycle(taskContainer.taskContentView as TaskThumbnailView)
            else -> taskThumbnailViewDeprecatedPool!!.recycle(taskContainer.thumbnailViewDeprecated)
        }
    }

    private fun updateTaskPositions() {
        BaseContainerInterface.getTaskDimension(mContext, container.deviceProfile, tempPointF)
        val desktopSize = Size(tempPointF.x.toInt(), tempPointF.y.toInt())
        DEFAULT_BOUNDS.set(0, 0, desktopSize.width / 4, desktopSize.height / 4)

        fullscreenTaskPositions =
            taskContainers.map {
                DesktopTaskBoundsData(it.task.key.id, it.task.appBounds ?: DEFAULT_BOUNDS)
            }

        if (enableDesktopExplodedView()) {
            val (widthScale, heightScale) = getScreenScaleFactors()
            val res = context.resources
            val layoutConfig =
                DesktopLayoutConfig(
                    topBottomMarginOneRow =
                        (res.getDimensionPixelSize(R.dimen.desktop_top_bottom_margin_one_row) /
                                heightScale)
                            .toInt(),
                    topMarginMultiRows =
                        (res.getDimensionPixelSize(R.dimen.desktop_top_margin_multi_rows) /
                                heightScale)
                            .toInt(),
                    bottomMarginMultiRows =
                        (res.getDimensionPixelSize(R.dimen.desktop_bottom_margin_multi_rows) /
                                heightScale)
                            .toInt(),
                    leftRightMarginOneRow =
                        (res.getDimensionPixelSize(R.dimen.desktop_left_right_margin_one_row) /
                                widthScale)
                            .toInt(),
                    leftRightMarginMultiRows =
                        (res.getDimensionPixelSize(R.dimen.desktop_left_right_margin_multi_rows) /
                                widthScale)
                            .toInt(),
                    horizontalPaddingBetweenTasks =
                        (res.getDimensionPixelSize(
                                R.dimen.desktop_horizontal_padding_between_tasks
                            ) / widthScale)
                            .toInt(),
                    verticalPaddingBetweenTasks =
                        (res.getDimensionPixelSize(R.dimen.desktop_vertical_padding_between_tasks) /
                                heightScale)
                            .toInt(),
                )

            viewModel?.organizeDesktopTasks(desktopSize, fullscreenTaskPositions, layoutConfig)
        }
        positionTaskWindows(updateLayout = true)
    }

    /**
     * Calculates the scale factors for the desktop task view's width and height. This is determined
     * by comparing the available task view dimensions (after accounting for margins like
     * [thumbnailTopMarginPx]) against the total screen dimensions.
     *
     * @return A [Pair] where the first value is the scale factor for width and the second is for
     *   height.
     */
    private fun getScreenScaleFactors(): Pair<Float, Float> {
        val thumbnailTopMarginPx = container.deviceProfile.overviewProfile.taskThumbnailTopMarginPx
        val taskViewWidth = layoutParams.width
        val taskViewHeight = layoutParams.height - thumbnailTopMarginPx

        val screenRect = getScreenRect()
        val widthScale = taskViewWidth / screenRect.width().toFloat()
        val heightScale = taskViewHeight / screenRect.height().toFloat()

        return Pair(widthScale, heightScale)
    }

    /** Returns the dimensions of the screen. */
    private fun getScreenRect(): Rect {
        BaseContainerInterface.getTaskDimension(mContext, container.deviceProfile, tempPointF)
        return Rect(0, 0, tempPointF.x.toInt(), tempPointF.y.toInt())
    }

    companion object {
        private const val TAG = "DesktopTaskView"
        private const val DEBUG = false
        private const val VIEW_POOL_MAX_SIZE = 5

        // As DesktopTaskView is inflated in background, use initialSize=0 to avoid initPool.
        private const val VIEW_POOL_INITIAL_SIZE = 0
        private val DEFAULT_BOUNDS = Rect()
        // Temporaries used for various purposes to avoid allocations.
        private val TEMP_OVERVIEW_TASK_POSITION = Rect()
        private val TEMP_FROM_RECTF = RectF()
        private val TEMP_TO_RECTF = RectF()
        private val TASK_REMOVE_PROGRESS: FloatProperty<DesktopTaskView> =
            KFloatProperty(DesktopTaskView::taskRemoveProgress)
    }
}
