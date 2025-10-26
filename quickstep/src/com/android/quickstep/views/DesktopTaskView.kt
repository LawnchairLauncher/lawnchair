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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Rect.intersects
import android.graphics.RectF
import android.util.AttributeSet
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
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.R
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.testing.TestLogging
import com.android.launcher3.testing.shared.TestProtocol
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
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData
import com.android.quickstep.recents.ui.viewmodel.DesktopTaskViewModel
import com.android.quickstep.recents.ui.viewmodel.TaskData
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
    val deskId
        get() = desktopTask?.deskId ?: DesktopVisibilityController.INACTIVE_DESK_ID

    private var desktopTask: DesktopTask? = null

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
     * When enableDesktopExplodedView is enabled, this controls the gradual transition from the
     * default positions to the organized non-overlapping positions.
     */
    var explodeProgress = 0.0f
        set(value) {
            field = value
            positionTaskWindows()
        }

    var remoteTargetHandles: Array<RemoteTargetHandle>? = null
        set(value) {
            field = value
            positionTaskWindows()
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
        contentView =
            findViewById<DesktopTaskContentView>(R.id.desktop_content).apply {
                updateLayoutParams<LayoutParams> {
                    topMargin = container.deviceProfile.overviewTaskThumbnailTopMarginPx
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

    private fun positionTaskWindows() {
        if (taskContainers.isEmpty()) {
            return
        }

        val thumbnailTopMarginPx = container.deviceProfile.overviewTaskThumbnailTopMarginPx

        val taskViewWidth = layoutParams.width
        val taskViewHeight = layoutParams.height - thumbnailTopMarginPx

        BaseContainerInterface.getTaskDimension(mContext, container.deviceProfile, tempPointF)

        val screenWidth = tempPointF.x.toInt()
        val screenHeight = tempPointF.y.toInt()
        val screenRect = Rect(0, 0, screenWidth, screenHeight)
        val scaleWidth = taskViewWidth / screenWidth.toFloat()
        val scaleHeight = taskViewHeight / screenHeight.toFloat()

        taskContainers.forEach {
            val taskId = it.task.key.id
            val fullscreenTaskPosition =
                fullscreenTaskPositions.firstOrNull { it.taskId == taskId } ?: return
            val overviewTaskPosition =
                if (enableDesktopExplodedView()) {
                    viewModel!!
                        .organizedDesktopTaskPositions
                        .firstOrNull { it.taskId == taskId }
                        ?.let { organizedPosition ->
                            TEMP_OVERVIEW_TASK_POSITION.apply {
                                lerpRect(
                                    fullscreenTaskPosition.bounds,
                                    organizedPosition.bounds,
                                    explodeProgress,
                                )
                            }
                        } ?: fullscreenTaskPosition.bounds
                } else {
                    fullscreenTaskPosition.bounds
                }

            if (enableDesktopExplodedView()) {
                getRemoteTargetHandle(taskId)?.let { remoteTargetHandle ->
                    val fromRect =
                        TEMP_FROM_RECTF.apply {
                            set(fullscreenTaskPosition.bounds)
                            scale(scaleWidth)
                            offset(
                                lastComputedTaskSize.left.toFloat(),
                                lastComputedTaskSize.top.toFloat(),
                            )
                        }
                    val toRect =
                        TEMP_TO_RECTF.apply {
                            set(overviewTaskPosition)
                            scale(scaleWidth)
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
            }

            val taskLeft = overviewTaskPosition.left * scaleWidth
            val taskTop = overviewTaskPosition.top * scaleHeight
            val taskWidth = overviewTaskPosition.width() * scaleWidth
            val taskHeight = overviewTaskPosition.height() * scaleHeight
            // TODO(b/394660950): Revisit the choice to update the layout when explodeProgress == 1.
            // To run the explode animation in reverse, it may be simpler to use translation/scale
            // for all cases where the progress is non-zero.
            if (explodeProgress == 0.0f || explodeProgress == 1.0f) {
                // Reset scaling and translation that may have been applied during animation.
                it.snapshotView.apply {
                    scaleX = 1.0f
                    scaleY = 1.0f
                    translationX = 0.0f
                    translationY = 0.0f
                }

                // Position the task to the same position as it would be on the desktop
                it.snapshotView.updateLayoutParams<LayoutParams> {
                    gravity = Gravity.LEFT or Gravity.TOP
                    width = taskWidth.toInt()
                    height = taskHeight.toInt()
                    leftMargin = taskLeft.toInt()
                    topMargin = taskTop.toInt()
                }

                if (
                    enableDesktopRecentsTransitionsCornersBugfix() && enableRefactorTaskThumbnail()
                ) {
                    it.thumbnailView.outlineBounds =
                        if (intersects(overviewTaskPosition, screenRect))
                            Rect(overviewTaskPosition).apply {
                                intersectUnchecked(screenRect)
                                // Offset to 0,0 to transform into TaskThumbnailView's coordinate
                                // system.
                                offset(-overviewTaskPosition.left, -overviewTaskPosition.top)
                                left = (left * scaleWidth).roundToInt()
                                top = (top * scaleHeight).roundToInt()
                                right = (right * scaleWidth).roundToInt()
                                bottom = (bottom * scaleHeight).roundToInt()
                            }
                        else null
                }
            } else {
                // During the animation, apply translation and scale such that the view is
                // transformed to where we want, without triggering layout.
                it.snapshotView.apply {
                    pivotX = 0.0f
                    pivotY = 0.0f
                    translationX = taskLeft - left
                    translationY = taskTop - top
                    scaleX = taskWidth / width.toFloat()
                    scaleY = taskHeight / height.toFloat()
                }
            }
        }
    }

    /** Updates this desktop task to the gives task list defined in `tasks` */
    fun bind(
        desktopTask: DesktopTask,
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory,
    ) {
        this.desktopTask = desktopTask
        // TODO(b/370495260): Minimized tasks should not be filtered with desktop exploded view
        // support.
        // Minimized tasks should not be shown in Overview.
        val tasks = desktopTask.tasks.filterNot { it.isMinimized }
        if (DEBUG) {
            val sb = StringBuilder()
            sb.append("bind tasks=").append(tasks.size).append("\n")
            tasks.forEach { sb.append(" key=${it.key}\n") }
            Log.d(TAG, sb.toString())
        }

        cancelPendingLoadTasks()
        val backgroundViewIndex = contentView.indexOfChild(backgroundView)
        taskContainers =
            tasks.map { task ->
                val snapshotView =
                    if (enableRefactorTaskThumbnail()) {
                        taskThumbnailViewPool!!.view
                    } else {
                        taskThumbnailViewDeprecatedPool!!.view
                    }
                contentView.addView(snapshotView, backgroundViewIndex + 1)

                TaskContainer(
                    this,
                    task,
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
        desktopTask = null
        explodeProgress = 0.0f
        viewModel = null
        visibility = VISIBLE
        taskContainers.forEach { removeAndRecycleThumbnailView(it) }
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

    private fun launchTaskWithDesktopController(animated: Boolean): RunnableList? {
        val recentsView = recentsView ?: return null
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "launchDesktopFromRecents",
            taskIds.contentToString(),
        )
        val endCallback = RunnableList()
        val desktopController = recentsView.desktopRecentsController
        checkNotNull(desktopController) { "recentsController is null" }
        desktopController.launchDesktopFromRecents(this, animated) {
            endCallback.executeAllAndDestroy()
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
            // Otherwise, re-position the remaining task windows.
            // TODO(b/353949276): Implement the re-layout animations.
            updateTaskPositions()
        }
    }

    private fun removeAndRecycleThumbnailView(taskContainer: TaskContainer) {
        contentView.removeView(taskContainer.snapshotView)
        if (enableRefactorTaskThumbnail()) {
            taskThumbnailViewPool!!.recycle(taskContainer.thumbnailView)
        } else {
            taskThumbnailViewDeprecatedPool!!.recycle(taskContainer.thumbnailViewDeprecated)
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
            viewModel?.organizeDesktopTasks(desktopSize, fullscreenTaskPositions)
        }
        positionTaskWindows()
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
    }
}
