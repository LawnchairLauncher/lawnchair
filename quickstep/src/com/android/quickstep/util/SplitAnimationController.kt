/*
 *  Copyright (C) 2023 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.quickstep.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import com.android.app.animation.Interpolators
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource
import com.android.launcher3.views.BaseDragLayer
import com.android.quickstep.views.FloatingTaskView
import com.android.quickstep.views.IconView
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.SplitInstructionsView
import com.android.quickstep.views.TaskThumbnailView
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer
import java.util.function.Supplier

/**
 * Utils class to help run animations for initiating split screen from launcher.
 * Will be expanded with future refactors. Works in conjunction with the state stored in
 * [SplitSelectStateController]
 */
class SplitAnimationController(val splitSelectStateController: SplitSelectStateController) {
    companion object {
        // Break this out into maybe enums? Abstractions into its own classes? Tbd.
        data class SplitAnimInitProps(
                val originalView: View,
                val originalBitmap: Bitmap?,
                val iconDrawable: Drawable,
                val fadeWithThumbnail: Boolean,
                val isStagedTask: Boolean,
                val iconView: View?
        )
    }

    /**
     * Returns different elements to animate for the initial split selection animation
     * depending on the state of the surface from which the split was initiated
     */
    fun getFirstAnimInitViews(taskViewSupplier: Supplier<TaskView>,
                              splitSelectSourceSupplier: Supplier<SplitSelectSource?>)
            : SplitAnimInitProps {
        val splitSelectSource = splitSelectSourceSupplier.get()
        if (!splitSelectStateController.isAnimateCurrentTaskDismissal) {
            // Initiating from home
            return SplitAnimInitProps(splitSelectSource!!.view, originalBitmap = null,
                    splitSelectSource.drawable, fadeWithThumbnail = false, isStagedTask = true,
                    iconView = null)
        } else if (splitSelectStateController.isDismissingFromSplitPair) {
            // Initiating split from overview, but on a split pair
            val taskView = taskViewSupplier.get()
            for (container : TaskIdAttributeContainer in taskView.taskIdAttributeContainers) {
                if (container.task.getKey().getId() == splitSelectStateController.initialTaskId) {
                    val drawable = getDrawable(container.iconView, splitSelectSource)
                    return SplitAnimInitProps(container.thumbnailView,
                            container.thumbnailView.thumbnail, drawable!!,
                            fadeWithThumbnail = true, isStagedTask = true,
                            iconView = container.iconView
                    )
                }
            }
            throw IllegalStateException("Attempting to init split from existing split pair " +
                    "without a valid taskIdAttributeContainer")
        } else {
            // Initiating split from overview on fullscreen task TaskView
            val taskView = taskViewSupplier.get()
            val drawable = getDrawable(taskView.iconView, splitSelectSource)
            return SplitAnimInitProps(taskView.thumbnail, taskView.thumbnail.thumbnail,
                    drawable!!, fadeWithThumbnail = true, isStagedTask = true,
                    taskView.iconView
            )
        }
    }

    /**
     * Returns the drawable that's provided in iconView, however if that
     * is null it falls back to the drawable that's in splitSelectSource.
     * TaskView's icon drawable can be null if the TaskView is scrolled far enough off screen
     * @return [Drawable]
     */
    fun getDrawable(iconView: IconView, splitSelectSource: SplitSelectSource?) : Drawable? {
        if (iconView.drawable == null && splitSelectSource != null) {
            return splitSelectSource.drawable
        }
        return iconView.drawable
    }

    /**
     * When selecting first app from split pair, second app's thumbnail remains. This animates
     * the second thumbnail by expanding it to take up the full taskViewWidth/Height and overlaying
     * it with [TaskThumbnailView]'s splashView. Adds animations to the provided builder.
     * Note: The app that **was not** selected as the first split app should be the container that's
     * passed through.
     *
     * @param builder Adds animation to this
     * @param taskIdAttributeContainer container of the app that **was not** selected
     * @param isPrimaryTaskSplitting if true, task that was split would be top/left in the pair
     *                               (opposite of that representing [taskIdAttributeContainer])
     */
    fun addInitialSplitFromPair(taskIdAttributeContainer: TaskIdAttributeContainer,
                                builder: PendingAnimation, deviceProfile: DeviceProfile,
                                taskViewWidth: Int, taskViewHeight: Int,
                                isPrimaryTaskSplitting: Boolean) {
        val thumbnail = taskIdAttributeContainer.thumbnailView
        val iconView: View = taskIdAttributeContainer.iconView
        builder.add(ObjectAnimator.ofFloat(thumbnail, TaskThumbnailView.SPLASH_ALPHA, 1f))
        thumbnail.setShowSplashForSplitSelection(true)
        if (deviceProfile.isLandscape) {
            // Center view first so scaling happens uniformly, alternatively we can move pivotX to 0
            val centerThumbnailTranslationX: Float = (taskViewWidth - thumbnail.width) / 2f
            val centerIconTranslationX: Float = (taskViewWidth - iconView.width) / 2f
            val finalScaleX: Float = taskViewWidth.toFloat() / thumbnail.width
            builder.add(ObjectAnimator.ofFloat(thumbnail,
                    TaskThumbnailView.SPLIT_SELECT_TRANSLATE_X, centerThumbnailTranslationX))
            // icons are anchored from Gravity.END, so need to use negative translation
            builder.add(ObjectAnimator.ofFloat(iconView, View.TRANSLATION_X,
                    -centerIconTranslationX))
            builder.add(ObjectAnimator.ofFloat(thumbnail, View.SCALE_X, finalScaleX))

            // Reset other dimensions
            // TODO(b/271468547), can't set Y translate to 0, need to account for top space
            thumbnail.scaleY = 1f
            val translateYResetVal: Float = if (!isPrimaryTaskSplitting) 0f else
                deviceProfile.overviewTaskThumbnailTopMarginPx.toFloat()
            builder.add(ObjectAnimator.ofFloat(thumbnail,
                    TaskThumbnailView.SPLIT_SELECT_TRANSLATE_Y,
                    translateYResetVal))
        } else {
            val thumbnailSize = taskViewHeight - deviceProfile.overviewTaskThumbnailTopMarginPx
            // Center view first so scaling happens uniformly, alternatively we can move pivotY to 0
            // primary thumbnail has layout margin above it, so secondary thumbnail needs to take
            // that into account. We should migrate to only using translations otherwise this
            // asymmetry causes problems..

            // Icon defaults to center | horizontal, we add additional translation for split
            val centerIconTranslationX = 0f
            var centerThumbnailTranslationY: Float

            // TODO(b/271468547), primary thumbnail has layout margin above it, so secondary
            //  thumbnail needs to take that into account. We should migrate to only using
            //  translations otherwise this asymmetry causes problems..
            if (isPrimaryTaskSplitting) {
                centerThumbnailTranslationY = (thumbnailSize - thumbnail.height) / 2f
                centerThumbnailTranslationY += deviceProfile.overviewTaskThumbnailTopMarginPx
                        .toFloat()
            } else {
                centerThumbnailTranslationY = (thumbnailSize - thumbnail.height) / 2f
            }
            val finalScaleY: Float = thumbnailSize.toFloat() / thumbnail.height
            builder.add(ObjectAnimator.ofFloat(thumbnail,
                    TaskThumbnailView.SPLIT_SELECT_TRANSLATE_Y, centerThumbnailTranslationY))

            // icons are anchored from Gravity.END, so need to use negative translation
            builder.add(ObjectAnimator.ofFloat(iconView, View.TRANSLATION_X,
                    centerIconTranslationX))
            builder.add(ObjectAnimator.ofFloat(thumbnail, View.SCALE_Y, finalScaleY))

            // Reset other dimensions
            thumbnail.scaleX = 1f
            builder.add(ObjectAnimator.ofFloat(thumbnail,
                    TaskThumbnailView.SPLIT_SELECT_TRANSLATE_X, 0f))
        }
    }

    /** Does not play any animation if user is not currently in split selection state. */
    fun playPlaceholderDismissAnim(launcher: StatefulActivity<*>) {
        if (!splitSelectStateController.isSplitSelectActive) {
            return
        }

        val anim = createPlaceholderDismissAnim(launcher)
        anim.start()
    }

    /** Returns [AnimatorSet] which slides initial split placeholder view offscreen. */
    fun createPlaceholderDismissAnim(launcher: StatefulActivity<*>) : AnimatorSet {
        val animatorSet = AnimatorSet()
        val recentsView : RecentsView<*, *> = launcher.getOverviewPanel()
        val floatingTask: FloatingTaskView = splitSelectStateController.firstFloatingTaskView
                ?: return animatorSet

        // We are in split selection state currently, transitioning to another state
        val dragLayer: BaseDragLayer<*> = launcher.dragLayer
        val onScreenRectF = RectF()
        Utilities.getBoundsForViewInDragLayer(dragLayer, floatingTask,
                Rect(0, 0, floatingTask.width, floatingTask.height),
                false, null, onScreenRectF)
        // Get the part of the floatingTask that intersects with the DragLayer (i.e. the
        // on-screen portion)
        onScreenRectF.intersect(
                dragLayer.left.toFloat(),
                dragLayer.top.toFloat(),
                dragLayer.right.toFloat(),
                dragLayer.bottom
                        .toFloat()
        )
        animatorSet.play(ObjectAnimator.ofFloat(floatingTask,
                FloatingTaskView.PRIMARY_TRANSLATE_OFFSCREEN,
                recentsView.pagedOrientationHandler
                        .getFloatingTaskOffscreenTranslationTarget(
                                floatingTask,
                                onScreenRectF,
                                floatingTask.stagePosition,
                                launcher.deviceProfile
                        )))
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                splitSelectStateController.resetState()
                safeRemoveViewFromDragLayer(launcher,
                        splitSelectStateController.splitInstructionsView)
            }
        })
        return animatorSet
    }

    /**
     * Returns a [PendingAnimation] to animate in the chip to instruct a user to select a second
     * app for splitscreen
     */
    fun getShowSplitInstructionsAnim(launcher: StatefulActivity<*>) : PendingAnimation {
        safeRemoveViewFromDragLayer(launcher, splitSelectStateController.splitInstructionsView)
        val splitInstructionsView = SplitInstructionsView.getSplitInstructionsView(launcher)
        splitSelectStateController.splitInstructionsView = splitInstructionsView
        val timings = AnimUtils.getDeviceOverviewToSplitTimings(launcher.deviceProfile.isTablet)
        val anim = PendingAnimation(100 /*duration */)
        anim.setViewAlpha(splitInstructionsView, 1f,
                Interpolators.clampToProgress(Interpolators.LINEAR,
                        timings.instructionsContainerFadeInStartOffset,
                        timings.instructionsContainerFadeInEndOffset))
        anim.setViewAlpha(splitInstructionsView!!.textView, 1f,
                Interpolators.clampToProgress(Interpolators.LINEAR,
                        timings.instructionsTextFadeInStartOffset,
                        timings.instructionsTextFadeInEndOffset))
        anim.addFloat(splitInstructionsView, SplitInstructionsView.UNFOLD, 0.1f, 1f,
                Interpolators.clampToProgress(Interpolators.EMPHASIZED_DECELERATE,
                        timings.instructionsUnfoldStartOffset,
                        timings.instructionsUnfoldEndOffset))
        return anim
    }

    /** Removes the split instructions view from [launcher] drag layer. */
    fun removeSplitInstructionsView(launcher: StatefulActivity<*>) {
        safeRemoveViewFromDragLayer(launcher, splitSelectStateController.splitInstructionsView)
    }

    private fun safeRemoveViewFromDragLayer(launcher: StatefulActivity<*>, view: View?) {
        if (view != null) {
            launcher.dragLayer.removeView(view)
        }
    }
}
