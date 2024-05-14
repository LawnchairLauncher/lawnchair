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

import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.ViewPool
import com.android.quickstep.BaseContainerInterface
import com.android.quickstep.RecentsModel
import com.android.quickstep.util.RecentsOrientedState
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.QuickStepContract
import java.util.function.Consumer

/** TaskView that contains all tasks that are part of the desktop. */
// TODO(b/249371338): TaskView needs to be refactored to have better support for N tasks.
class DesktopTaskView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) :
    TaskView(context, attrs) {

    private val pendingThumbnailRequests = mutableListOf<CancellableTask<*>>()
    private val snapshotDrawParams =
        object : FullscreenDrawParams(context) {
            override fun computeTaskCornerRadius(context: Context) =
                QuickStepContract.getWindowCornerRadius(context)

            override fun computeWindowCornerRadius(context: Context) =
                QuickStepContract.getWindowCornerRadius(context)
        }
    private val taskThumbnailViewPool =
        ViewPool<TaskThumbnailViewDeprecated>(
            context,
            this,
            R.layout.task_thumbnail,
            10,
            0 // As DesktopTaskView is inflated in background, use initialSize=0 to avoid initPool.
        )
    private val tempPointF = PointF()
    private val tempRect = Rect()
    private lateinit var backgroundView: View
    private var childCountAtInflation = 0

    init {
        mTaskContainers = ArrayList()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        backgroundView = findViewById(R.id.background)!!
        val topMarginPx = mContainer.deviceProfile.overviewTaskThumbnailTopMarginPx
        backgroundView.updateLayoutParams<LayoutParams> { topMargin = topMarginPx }

        val outerRadii = FloatArray(8) { taskCornerRadius }
        backgroundView.background =
            ShapeDrawable(RoundRectShape(outerRadii, null, null)).apply {
                setTint(resources.getColor(android.R.color.system_neutral2_300, context.theme))
            }

        val iconBackground = resources.getDrawable(R.drawable.bg_circle, context.theme)
        val icon = resources.getDrawable(R.drawable.ic_desktop, context.theme)
        setIcon(mIconView, LayerDrawable(arrayOf(iconBackground, icon)))

        childCountAtInflation = childCount
    }

    override fun getThumbnailBounds(bounds: Rect, relativeToDragLayer: Boolean) {
        if (relativeToDragLayer) {
            mContainer.dragLayer.getDescendantRectRelativeToSelf(backgroundView, bounds)
        } else {
            bounds.set(
                backgroundView.left,
                backgroundView.top,
                backgroundView.right,
                backgroundView.bottom
            )
        }
    }

    override fun bind(task: Task, orientedState: RecentsOrientedState) {
        bind(listOf(task), orientedState)
    }

    /** Updates this desktop task to the gives task list defined in `tasks` */
    fun bind(tasks: List<Task>, orientedState: RecentsOrientedState) {
        if (DEBUG) {
            val sb = StringBuilder()
            sb.append("bind tasks=").append(tasks.size).append("\n")
            tasks.forEach { sb.append(" key=${it.key}\n") }
            Log.d(TAG, sb.toString())
        }
        cancelPendingLoadTasks()

        (mTaskContainers as ArrayList).ensureCapacity(tasks.size)
        tasks.forEachIndexed { index, task ->
            val thumbnailView: TaskThumbnailViewDeprecated
            if (index >= mTaskContainers.size) {
                thumbnailView = taskThumbnailViewPool.view
                // Add thumbnailView from to position after the initial child views.
                addView(
                    thumbnailView,
                    childCountAtInflation,
                    LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            } else {
                thumbnailView = mTaskContainers[index].thumbnailView
            }
            thumbnailView.bind(task)
            val taskContainer =
                TaskContainer(
                    task,
                    thumbnailView,
                    mIconView,
                    SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
                    null
                )
            if (index >= mTaskContainers.size) {
                mTaskContainers.add(taskContainer)
            } else {
                mTaskContainers[index] = taskContainer
            }
        }
        while (mTaskContainers.size > tasks.size) {
            mTaskContainers.removeLast().apply {
                removeView(thumbnailView)
                taskThumbnailViewPool.recycle(thumbnailView)
            }
        }

        setOrientationState(orientedState)
    }

    override fun onTaskListVisibilityChanged(visible: Boolean, changes: Int) {
        cancelPendingLoadTasks()
        if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
            mTaskContainers.forEach {
                if (visible) {
                    RecentsModel.INSTANCE.get(context)
                        .thumbnailCache
                        .updateThumbnailInBackground(it.task) { thumbnailData: ThumbnailData ->
                            it.thumbnailView.setThumbnail(it.task, thumbnailData)
                        }
                        ?.apply { pendingThumbnailRequests.add(this) }
                } else {
                    it.thumbnailView.setThumbnail(null, null)
                    // Reset the task thumbnail ref
                    it.task.thumbnail = null
                }
            }
        }
    }

    // thumbnailView is laid out differently and is handled in onMeasure
    override fun setThumbnailOrientation(orientationState: RecentsOrientedState) {}

    override fun cancelPendingLoadTasks() {
        pendingThumbnailRequests.forEach { it.cancel() }
        pendingThumbnailRequests.clear()
    }

    override fun launchTaskAnimated(): RunnableList? {
        val recentsView = recentsView ?: return null
        val endCallback = RunnableList()
        val desktopController = recentsView.desktopRecentsController
        if (desktopController != null) {
            desktopController.launchDesktopFromRecents(this) { endCallback.executeAllAndDestroy() }
            Log.d(
                TAG,
                "launchTaskAnimated - launchDesktopFromRecents: ${taskIds.contentToString()}"
            )
        } else {
            Log.d(
                TAG,
                "launchTaskAnimated - recentsController is null: ${taskIds.contentToString()}"
            )
        }

        // Callbacks get run from recentsView for case when recents animation already running
        recentsView.addSideTaskLaunchCallback(endCallback)
        return endCallback
    }

    override fun launchTask(callback: Consumer<Boolean>, isQuickswitch: Boolean) {
        launchTasks()
        callback.accept(true)
    }

    public override fun refreshThumbnails(thumbnailDatas: HashMap<Int, ThumbnailData?>?) {
        // Sets new thumbnails based on the incoming data and refreshes the rest.
        thumbnailDatas?.let {
            mTaskContainers.forEach {
                val thumbnailData = thumbnailDatas[it.task.key.id]
                if (thumbnailData != null) {
                    it.thumbnailView.setThumbnail(it.task, thumbnailData)
                } else {
                    // Refresh the rest that were not updated.
                    it.thumbnailView.refresh()
                }
            }
        }
    }

    override fun onRecycle() {
        resetPersistentViewTransforms()
        // Clear any references to the thumbnail (it will be re-read either from the cache or the
        // system on next bind)
        mTaskContainers.forEach { it.thumbnailView.setThumbnail(it.task, null) }
        setOverlayEnabled(false)
        onTaskListVisibilityChanged(false)
        visibility = VISIBLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val containerWidth = MeasureSpec.getSize(widthMeasureSpec)
        var containerHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(containerWidth, containerHeight)

        if (mTaskContainers.isEmpty()) {
            return
        }

        val thumbnailTopMarginPx = mContainer.deviceProfile.overviewTaskThumbnailTopMarginPx
        containerHeight -= thumbnailTopMarginPx

        BaseContainerInterface.getTaskDimension(mContext, mContainer.deviceProfile, tempPointF)
        val windowWidth = tempPointF.x.toInt()
        val windowHeight = tempPointF.y.toInt()
        val scaleWidth = containerWidth / windowWidth.toFloat()
        val scaleHeight = containerHeight / windowHeight.toFloat()
        if (DEBUG) {
            Log.d(
                TAG,
                "onMeasure: container=[$containerWidth,$containerHeight] " +
                    "window=[$windowWidth,$windowHeight] scale=[$scaleWidth,$scaleHeight]"
            )
        }

        // Desktop tile is a shrunk down version of launcher and freeform task thumbnails.
        mTaskContainers.forEach {
            // Default to quarter of the desktop if we did not get app bounds.
            val taskSize =
                it.task.appBounds
                    ?: tempRect.apply {
                        left = 0
                        top = 0
                        right = windowWidth / 4
                        bottom = windowHeight / 4
                    }
            val thumbWidth = (taskSize.width() * scaleWidth).toInt()
            val thumbHeight = (taskSize.height() * scaleHeight).toInt()
            it.thumbnailView.measure(
                MeasureSpec.makeMeasureSpec(thumbWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(thumbHeight, MeasureSpec.EXACTLY)
            )

            // Position the task to the same position as it would be on the desktop
            val positionInParent = it.task.positionInParent ?: ORIGIN
            val taskX = (positionInParent.x * scaleWidth).toInt()
            var taskY = (positionInParent.y * scaleHeight).toInt()
            // move task down by margin size
            taskY += thumbnailTopMarginPx
            it.thumbnailView.x = taskX.toFloat()
            it.thumbnailView.y = taskY.toFloat()
            if (DEBUG) {
                Log.d(
                    TAG,
                    "onMeasure: task=${it.task.key} thumb=[$thumbWidth,$thumbHeight]" +
                        " pos=[$taskX,$taskY]"
                )
            }
        }
    }

    // TODO(b/330685808) support overlay for Screenshot action
    override fun setOverlayEnabled(overlayEnabled: Boolean) {}

    override fun setFullscreenProgress(progress: Float) {
        // TODO(b/249371338): this copies parent implementation and makes it work for N thumbs
        val boundProgress = Utilities.boundToRange(progress, 0f, 1f)
        mFullscreenProgress = boundProgress
        mIconView.setVisibility(if (boundProgress < 1) VISIBLE else INVISIBLE)
        // Don't show background while we are transitioning to/from fullscreen
        backgroundView.visibility = if (mFullscreenProgress > 0) INVISIBLE else VISIBLE
        mTaskContainers.forEach {
            it.thumbnailView.taskOverlay.setFullscreenProgress(boundProgress)
        }
        // Animate icons and DWB banners in/out, except in QuickSwitch state, when tiles are
        // oversized and banner would look disproportionately large.
        if (
            mContainer.getOverviewPanel<RecentsView<*, *>>().getStateManager().state !=
                LauncherState.BACKGROUND_APP
        ) {
            setIconsAndBannersTransitionProgress(boundProgress, true)
        }
        updateSnapshotRadius()
    }

    override fun updateSnapshotRadius() {
        super.updateSnapshotRadius()
        updateFullscreenParams(snapshotDrawParams)
        mTaskContainers.forEach { it.thumbnailView.setFullscreenParams(snapshotDrawParams) }
    }

    override fun setColorTint(amount: Float, tintColor: Int) {
        mTaskContainers.forEach { it.thumbnailView.dimAlpha = amount }
    }

    override fun applyThumbnailSplashAlpha() {
        mTaskContainers.forEach { it.thumbnailView.setSplashAlpha(mTaskThumbnailSplashAlpha) }
    }

    public override fun setThumbnailVisibility(visibility: Int, taskId: Int) {
        mTaskContainers.forEach { it.thumbnailView.visibility = visibility }
    }

    // Desktop tile can't be in split screen
    override fun confirmSecondSplitSelectApp(): Boolean = false

    companion object {
        private const val TAG = "DesktopTaskView"
        private const val DEBUG = true
        private val ORIGIN = Point(0, 0)
    }
}
