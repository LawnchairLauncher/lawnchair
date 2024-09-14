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
import com.android.launcher3.R
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.launcher3.util.ViewPool
import com.android.launcher3.util.rects.set
import com.android.quickstep.BaseContainerInterface
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.util.RecentsOrientedState
import com.android.systemui.shared.recents.model.Task

/** TaskView that contains all tasks that are part of the desktop. */
class DesktopTaskView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    TaskView(context, attrs) {

    private val snapshotDrawParams =
        object : FullscreenDrawParams(context) {
            // DesktopTaskView thumbnail's corner radius is independent of fullscreenProgress.
            override fun computeTaskCornerRadius(context: Context) =
                computeWindowCornerRadius(context)
        }
    private val taskThumbnailViewPool =
        ViewPool<TaskThumbnailViewDeprecated>(
            context,
            this,
            R.layout.task_thumbnail,
            VIEW_POOL_MAX_SIZE,
            VIEW_POOL_INITIAL_SIZE
        )
    private val tempPointF = PointF()
    private val tempRect = Rect()
    private lateinit var backgroundView: View
    private lateinit var iconView: TaskViewIcon
    private var childCountAtInflation = 0

    override fun onFinishInflate() {
        super.onFinishInflate()
        backgroundView =
            findViewById<View>(R.id.background)!!.apply {
                updateLayoutParams<LayoutParams> {
                    topMargin = container.deviceProfile.overviewTaskThumbnailTopMarginPx
                }
                background =
                    ShapeDrawable(RoundRectShape(FloatArray(8) { taskCornerRadius }, null, null))
                        .apply {
                            setTint(
                                resources.getColor(
                                    android.R.color.system_neutral2_300,
                                    context.theme
                                )
                            )
                        }
            }
        iconView =
            getOrInflateIconView(R.id.icon).apply {
                val iconBackground = resources.getDrawable(R.drawable.bg_circle, context.theme)
                val icon = resources.getDrawable(R.drawable.ic_desktop, context.theme)
                setIcon(this, LayerDrawable(arrayOf(iconBackground, icon)))
            }
        childCountAtInflation = childCount
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val containerWidth = MeasureSpec.getSize(widthMeasureSpec)
        var containerHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(containerWidth, containerHeight)

        if (taskContainers.isEmpty()) {
            return
        }

        val thumbnailTopMarginPx = container.deviceProfile.overviewTaskThumbnailTopMarginPx
        containerHeight -= thumbnailTopMarginPx

        BaseContainerInterface.getTaskDimension(mContext, container.deviceProfile, tempPointF)
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
        taskContainers.forEach {
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
            it.thumbnailViewDeprecated.measure(
                MeasureSpec.makeMeasureSpec(thumbWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(thumbHeight, MeasureSpec.EXACTLY)
            )

            // Position the task to the same position as it would be on the desktop
            val positionInParent = it.task.positionInParent ?: ORIGIN
            val taskX = (positionInParent.x * scaleWidth).toInt()
            var taskY = (positionInParent.y * scaleHeight).toInt()
            // move task down by margin size
            taskY += thumbnailTopMarginPx
            it.thumbnailViewDeprecated.x = taskX.toFloat()
            it.thumbnailViewDeprecated.y = taskY.toFloat()
            if (DEBUG) {
                Log.d(
                    TAG,
                    "onMeasure: task=${it.task.key} thumb=[$thumbWidth,$thumbHeight]" +
                        " pos=[$taskX,$taskY]"
                )
            }
        }
    }

    override fun onRecycle() {
        super.onRecycle()
        visibility = VISIBLE
    }

    /** Updates this desktop task to the gives task list defined in `tasks` */
    fun bind(
        tasks: List<Task>,
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory
    ) {
        if (DEBUG) {
            val sb = StringBuilder()
            sb.append("bind tasks=").append(tasks.size).append("\n")
            tasks.forEach { sb.append(" key=${it.key}\n") }
            Log.d(TAG, sb.toString())
        }
        cancelPendingLoadTasks()

        if (!isTaskContainersInitialized()) {
            taskContainers = arrayListOf()
        }
        val taskContainers = taskContainers as ArrayList
        taskContainers.ensureCapacity(tasks.size)
        tasks.forEachIndexed { index, task ->
            val thumbnailViewDeprecated: TaskThumbnailViewDeprecated
            if (index >= taskContainers.size) {
                thumbnailViewDeprecated = taskThumbnailViewPool.view
                // Add thumbnailView from to position after the initial child views.
                addView(
                    thumbnailViewDeprecated,
                    childCountAtInflation,
                    LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            } else {
                thumbnailViewDeprecated = taskContainers[index].thumbnailViewDeprecated
            }
            val taskContainer =
                TaskContainer(
                        task,
                        // TODO(b/338360089): Support new TTV for DesktopTaskView
                        thumbnailView = null,
                        thumbnailViewDeprecated,
                        iconView,
                        TransformingTouchDelegate(iconView.asView()),
                        SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
                        digitalWellBeingToast = null,
                        showWindowsView = null,
                        taskOverlayFactory
                    )
                    .apply { thumbnailViewDeprecated.bind(task, overlay) }
            if (index >= taskContainers.size) {
                taskContainers.add(taskContainer)
            } else {
                taskContainers[index] = taskContainer
            }
        }
        repeat(taskContainers.size - tasks.size) {
            with(taskContainers.removeLast()) {
                removeView(thumbnailViewDeprecated)
                taskThumbnailViewPool.recycle(thumbnailViewDeprecated)
            }
        }

        setOrientationState(orientedState)
    }

    override fun needsUpdate(dataChange: Int, flag: Int) =
        if (flag == FLAG_UPDATE_THUMBNAIL) super.needsUpdate(dataChange, flag) else false

    // thumbnailView is laid out differently and is handled in onMeasure
    override fun updateThumbnailSize() {}

    override fun getThumbnailBounds(bounds: Rect, relativeToDragLayer: Boolean) {
        if (relativeToDragLayer) {
            container.dragLayer.getDescendantRectRelativeToSelf(backgroundView, bounds)
        } else {
            bounds.set(backgroundView)
        }
    }

    override fun launchTaskAnimated(): RunnableList? {
        val recentsView = recentsView ?: return null
        val endCallback = RunnableList()
        val desktopController = recentsView.desktopRecentsController
        checkNotNull(desktopController) { "recentsController is null" }
        desktopController.launchDesktopFromRecents(this) { endCallback.executeAllAndDestroy() }
        Log.d(TAG, "launchTaskAnimated - launchDesktopFromRecents: ${taskIds.contentToString()}")

        // Callbacks get run from recentsView for case when recents animation already running
        recentsView.addSideTaskLaunchCallback(endCallback)
        return endCallback
    }

    override fun launchTask(callback: (launched: Boolean) -> Unit, isQuickSwitch: Boolean) {
        launchTasks()
        callback(true)
    }

    // Desktop tile can't be in split screen
    override fun confirmSecondSplitSelectApp(): Boolean = false

    // TODO(b/330685808) support overlay for Screenshot action
    override fun setOverlayEnabled(overlayEnabled: Boolean) {}

    override fun onFullscreenProgressChanged(fullscreenProgress: Float) {
        // Don't show background while we are transitioning to/from fullscreen
        backgroundView.visibility = if (fullscreenProgress > 0) INVISIBLE else VISIBLE
    }

    override fun updateCurrentFullscreenParams() {
        super.updateCurrentFullscreenParams()
        updateFullscreenParams(snapshotDrawParams)
    }

    override fun getThumbnailFullscreenParams() = snapshotDrawParams

    companion object {
        private const val TAG = "DesktopTaskView"
        private const val DEBUG = false
        private const val VIEW_POOL_MAX_SIZE = 10
        // As DesktopTaskView is inflated in background, use initialSize=0 to avoid initPool.
        private const val VIEW_POOL_INITIAL_SIZE = 0
        private val ORIGIN = Point(0, 0)
    }
}
