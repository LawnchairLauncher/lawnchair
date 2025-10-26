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

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.model.data.TaskViewItemInfo
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.ViewUtils.addAccessibleChildToList
import com.android.quickstep.recents.domain.usecase.ThumbnailPosition
import com.android.quickstep.recents.ui.mapper.TaskUiStateMapper
import com.android.quickstep.recents.ui.viewmodel.TaskData
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData

/** Holder for all Task dependent information. */
class TaskContainer(
    val taskView: TaskView,
    val task: Task,
    val snapshotView: View,
    val iconView: TaskViewIcon,
    /**
     * This technically can be a vanilla [android.view.TouchDelegate] class, however that class
     * requires setting the touch bounds at construction, so we'd repeatedly be created many
     * instances unnecessarily as scrolling occurs, whereas [TransformingTouchDelegate] allows touch
     * delegated bounds only to be updated.
     */
    val iconTouchDelegate: TransformingTouchDelegate,
    /** Defaults to STAGE_POSITION_UNDEFINED if in not a split screen task view */
    @SplitConfigurationOptions.StagePosition val stagePosition: Int,
    val digitalWellBeingToast: DigitalWellBeingToast?,
    val showWindowsView: View?,
    taskOverlayFactory: TaskOverlayFactory,
) {
    val overlay: TaskOverlayFactory.TaskOverlay<*> = taskOverlayFactory.createOverlay(this)
    var thumbnailPosition: ThumbnailPosition? = null
    private var overlayEnabledStatus = false

    init {
        if (enableRefactorTaskThumbnail()) {
            require(snapshotView is TaskThumbnailView)
        } else {
            require(snapshotView is TaskThumbnailViewDeprecated)
        }
    }

    internal var thumbnailData: ThumbnailData? = null
        private set

    val thumbnail: Bitmap?
        /** If possible don't use this. It should be replaced as part of b/331753115. */
        get() =
            if (enableRefactorTaskThumbnail()) thumbnailData?.thumbnail
            else thumbnailViewDeprecated.thumbnail

    val thumbnailView: TaskThumbnailView
        get() {
            require(enableRefactorTaskThumbnail())
            return snapshotView as TaskThumbnailView
        }

    val thumbnailViewDeprecated: TaskThumbnailViewDeprecated
        get() {
            require(!enableRefactorTaskThumbnail())
            return snapshotView as TaskThumbnailViewDeprecated
        }

    var isThumbnailValid: Boolean = false
        internal set

    val shouldShowSplashView: Boolean
        get() =
            if (enableRefactorTaskThumbnail()) taskView.shouldShowSplash()
            else thumbnailViewDeprecated.shouldShowSplashView()

    /** Builds proto for logging */
    val itemInfo: TaskViewItemInfo
        get() = TaskViewItemInfo(taskView, this)

    fun bind() = {
            digitalWellBeingToast?.bind(task, taskView, snapshotView, stagePosition)
            if (!enableRefactorTaskThumbnail()) {
                thumbnailViewDeprecated.bind(task, overlay, taskView)
            }
        }

    fun destroy() = {
            digitalWellBeingToast?.destroy()
            snapshotView.scaleX = 1f
            snapshotView.scaleY = 1f
            overlay.reset()
            if (enableRefactorTaskThumbnail()) {
                isThumbnailValid = false
                thumbnailData = null
                thumbnailView.onRecycle()
            } else {
                thumbnailViewDeprecated.setShowSplashForSplitSelection(false)
            }

            if (enableOverviewIconMenu()) {
                (iconView as IconAppChipView).reset()
            }
        }

    fun setOverlayEnabled(enabled: Boolean) {
        if (!enableRefactorTaskThumbnail()) {
            thumbnailViewDeprecated.setOverlayEnabled(enabled)
        }
    }

    fun setOverlayEnabled(enabled: Boolean, thumbnailPosition: ThumbnailPosition?) {
        if (enableRefactorTaskThumbnail()) {
            if (overlayEnabledStatus != enabled || this.thumbnailPosition != thumbnailPosition) {
                overlayEnabledStatus = enabled

                refreshOverlay(thumbnailPosition)
            }
        }
    }

    fun refreshOverlay(thumbnailPosition: ThumbnailPosition?) = {
            this.thumbnailPosition = thumbnailPosition
            when {
                !overlayEnabledStatus -> overlay.reset()
                thumbnailPosition == null -> {
                    Log.e(TAG, "Thumbnail position was null during overlay refresh", Exception())
                    overlay.reset()
                }
                else ->
                    overlay.initOverlay(
                        task,
                        thumbnailData?.thumbnail,
                        thumbnailPosition.matrix,
                        thumbnailPosition.isRotated,
                    )
            }
        }

    fun addChildForAccessibility(outChildren: ArrayList<View>) {
        addAccessibleChildToList(iconView.asView(), outChildren)
        addAccessibleChildToList(snapshotView, outChildren)
        showWindowsView?.let { addAccessibleChildToList(it, outChildren) }
        digitalWellBeingToast?.let { addAccessibleChildToList(it, outChildren) }
        overlay.addChildForAccessibility(outChildren)
    }

    fun setState(
        state: TaskData?,
        liveTile: Boolean,
        hasHeader: Boolean,
        clickCloseListener: OnClickListener?,
    ) = {
            thumbnailView.setState(
                TaskUiStateMapper.toTaskThumbnailUiState(
                    state,
                    liveTile,
                    hasHeader,
                    clickCloseListener,
                ),
                state?.taskId,
            )
            thumbnailData = if (state is TaskData.Data) state.thumbnailData else null
            overlay.setThumbnailState(thumbnailData)
        }

    fun updateTintAmount(tintAmount: Float) {
        thumbnailView.updateTintAmount(tintAmount)
    }

    /**
     * Updates the progress of the menu opening animation.
     *
     * This function propagates the given `progress` value to the `thumbnailView` allowing the
     * thumbnail view to animate its visual state in sync with the menu's opening/closing
     * transition.
     *
     * @param progress The progress of the menu opening animation (from closed=0 to fully open=1)
     */
    fun updateMenuOpenProgress(progress: Float) {
        thumbnailView.updateMenuOpenProgress(progress)
    }

    /**
     * Updates the thumbnail splash progress for a given task.
     *
     * This function manages the visual feedback of a "splash" effect that can be displayed over a
     * thumbnail image, typically during loading or updating. It calculates the alpha (transparency)
     * of the splash based on the provided progress and then applies this alpha to the thumbnail
     * view if it should be displayed.
     *
     * @param progress The progress of the operation, ranging from 0.0 to 1.0
     */
    fun updateThumbnailSplashProgress(progress: Float) {
        if (enableRefactorTaskThumbnail()) {
            thumbnailView.updateSplashAlpha(progress)
        } else {
            thumbnailViewDeprecated.setSplashAlpha(progress)
        }
    }

    fun updateThumbnailMatrix(matrix: Matrix) {
        thumbnailView.setImageMatrix(matrix)
    }

    companion object {
        const val TAG = "TaskContainer"
    }
}
