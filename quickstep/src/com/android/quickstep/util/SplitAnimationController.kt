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
import android.animation.ValueAnimator
import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.View
import android.view.WindowManager
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import androidx.annotation.VisibleForTesting
import com.android.app.animation.Interpolators
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.QuickstepTransitionManager
import com.android.launcher3.Utilities
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource
import com.android.launcher3.views.BaseDragLayer
import com.android.quickstep.TaskViewUtils
import com.android.quickstep.views.FloatingAppPairView
import com.android.quickstep.views.FloatingTaskView
import com.android.quickstep.views.GroupedTaskView
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.SplitInstructionsView
import com.android.quickstep.views.TaskThumbnailView
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer
import com.android.quickstep.views.TaskViewIcon
import java.util.Optional
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
                            iconView = container.iconView.asView()
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
                    taskView.iconView.asView()
            )
        }
    }

    /**
     * Returns the drawable that's provided in iconView, however if that
     * is null it falls back to the drawable that's in splitSelectSource.
     * TaskView's icon drawable can be null if the TaskView is scrolled far enough off screen
     * @return [Drawable]
     */
    fun getDrawable(iconView: TaskViewIcon, splitSelectSource: SplitSelectSource?) : Drawable? {
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
        val iconView: View = taskIdAttributeContainer.iconView.asView()
        builder.add(ObjectAnimator.ofFloat(thumbnail, TaskThumbnailView.SPLASH_ALPHA, 1f))
        thumbnail.setShowSplashForSplitSelection(true)
        if (deviceProfile.isLeftRightSplit) {
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
        splitInstructionsView.alpha = 0f
        anim.setViewAlpha(splitInstructionsView, 1f,
                Interpolators.clampToProgress(Interpolators.LINEAR,
                        timings.instructionsContainerFadeInStartOffset,
                        timings.instructionsContainerFadeInEndOffset))
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

    /**
     * Animates the first placeholder view to fullscreen and launches its task.
     * TODO(b/276361926): Remove the [resetCallback] option once contextual launches
     */
    fun playAnimPlaceholderToFullscreen(launcher: StatefulActivity<*>, view: View,
                                        resetCallback: Optional<Runnable>) {
        val stagedTaskView = view as FloatingTaskView

        val isTablet: Boolean = launcher.deviceProfile.isTablet
        val duration = if (isTablet) SplitAnimationTimings.TABLET_CONFIRM_DURATION else
            SplitAnimationTimings.PHONE_CONFIRM_DURATION
        val pendingAnimation = PendingAnimation(duration.toLong())
        val firstTaskStartingBounds = Rect()
        val firstTaskEndingBounds = Rect()

        stagedTaskView.getBoundsOnScreen(firstTaskStartingBounds)
        launcher.dragLayer.getBoundsOnScreen(firstTaskEndingBounds)
        splitSelectStateController.setLaunchingFirstAppFullscreen()

        stagedTaskView.addConfirmAnimation(
                pendingAnimation,
                RectF(firstTaskStartingBounds),
                firstTaskEndingBounds,
                false /* fadeWithThumbnail */,
                true /* isStagedTask */)

        pendingAnimation.addEndListener {
            splitSelectStateController.launchInitialAppFullscreen {
                if (FeatureFlags.enableSplitContextually()) {
                    splitSelectStateController.resetState()
                } else if (resetCallback.isPresent) {
                    resetCallback.get().run()
                }
            }
        }

        pendingAnimation.buildAnim().start()
    }

    /**
     * Called when launching a specific pair of apps, e.g. when tapping a pair of apps in Overview,
     * or launching an app pair from its Home icon. Selects the appropriate launch animation and
     * plays it.
     */
    fun playSplitLaunchAnimation(
        launchingTaskView: GroupedTaskView?,
        launchingIconView: AppPairIcon?,
        initialTaskId: Int,
        secondTaskId: Int,
        apps: Array<RemoteAnimationTarget>?,
        wallpapers: Array<RemoteAnimationTarget>?,
        nonApps: Array<RemoteAnimationTarget>?,
        stateManager: StateManager<*>,
        depthController: DepthController?,
        info: TransitionInfo?,
        t: Transaction?,
        finishCallback: Runnable
    ) {
        if (info == null && t == null) {
            // (Legacy animation) Tapping a split tile in Overview
            // TODO (b/315490678): Ensure that this works with app pairs flow
            check(apps != null && wallpapers != null && nonApps != null) {
                "trying to call composeRecentsSplitLaunchAnimatorLegacy, but encountered an " +
                    "unexpected null"
            }

            composeRecentsSplitLaunchAnimatorLegacy(
                launchingTaskView,
                initialTaskId,
                secondTaskId,
                apps,
                wallpapers,
                nonApps,
                stateManager,
                depthController,
                finishCallback
            )

            return
        }

        if (launchingTaskView != null) {
            // Tapping a split tile in Overview
            check(info != null && t != null) {
                "trying to launch a GroupedTaskView, but encountered an unexpected null"
            }

            composeRecentsSplitLaunchAnimator(
                launchingTaskView,
                stateManager,
                depthController,
                info,
                t,
                finishCallback
            )
        } else if (launchingIconView != null) {
            // Tapping an app pair icon
            check(info != null && t != null) {
                "trying to launch an app pair icon, but encountered an unexpected null"
            }

            composeIconSplitLaunchAnimator(
                launchingIconView,
                initialTaskId,
                secondTaskId,
                info,
                t,
                finishCallback
            )
        } else {
            // Fallback case: simple fade-in animation
            check(info != null && t != null) {
                "trying to call composeFadeInSplitLaunchAnimator, but encountered an " +
                    "unexpected null"
            }

            composeFadeInSplitLaunchAnimator(initialTaskId, secondTaskId, info, t, finishCallback)
        }
    }

    /**
     * When the user taps a split tile in Overview, this will play the tasks' launch animation from
     * the position of the tapped tile.
     */
    @VisibleForTesting
    fun composeRecentsSplitLaunchAnimator(
        launchingTaskView: GroupedTaskView,
        stateManager: StateManager<*>,
        depthController: DepthController?,
        info: TransitionInfo,
        t: Transaction,
        finishCallback: Runnable
    ) {
        TaskViewUtils.composeRecentsSplitLaunchAnimator(
            launchingTaskView,
            stateManager,
            depthController,
            info,
            t,
            finishCallback
        )
    }

    /**
     * LEGACY VERSION: When the user taps a split tile in Overview, this will play the tasks' launch
     * animation from the position of the tapped tile.
     */
    @VisibleForTesting
    fun composeRecentsSplitLaunchAnimatorLegacy(
        launchingTaskView: GroupedTaskView?,
        initialTaskId: Int,
        secondTaskId: Int,
        apps: Array<RemoteAnimationTarget>,
        wallpapers: Array<RemoteAnimationTarget>,
        nonApps: Array<RemoteAnimationTarget>,
        stateManager: StateManager<*>,
        depthController: DepthController?,
        finishCallback: Runnable
    ) {
        TaskViewUtils.composeRecentsSplitLaunchAnimatorLegacy(
            launchingTaskView,
            initialTaskId,
            secondTaskId,
            apps,
            wallpapers,
            nonApps,
            stateManager,
            depthController,
            finishCallback
        )
    }

    /**
     * When the user taps an app pair icon to launch split, this will play the tasks' launch
     * animation from the position of the icon.
     */
    @VisibleForTesting
    fun composeIconSplitLaunchAnimator(
        launchingIconView: AppPairIcon,
        initialTaskId: Int,
        secondTaskId: Int,
        transitionInfo: TransitionInfo,
        t: Transaction,
        finishCallback: Runnable
    ) {
        val launcher = Launcher.getLauncher(launchingIconView.context)
        val dp = launcher.deviceProfile

        // Create an AnimatorSet that will run both shell and launcher transitions together
        val launchAnimation = AnimatorSet()
        val progressUpdater = ValueAnimator.ofFloat(0f, 1f)
        val timings = AnimUtils.getDeviceAppPairLaunchTimings(dp.isTablet)
        progressUpdater.setDuration(timings.getDuration().toLong())
        progressUpdater.interpolator = Interpolators.LINEAR

        // Find the root shell leash that we want to fade in (parent of both app windows and
        // the divider). For simplicity, we search using the initialTaskId.
        var rootShellLayer: SurfaceControl? = null
        var dividerPos = 0

        for (change in transitionInfo.changes) {
            val taskInfo: RunningTaskInfo = change.taskInfo ?: continue
            val taskId = taskInfo.taskId
            val mode = change.mode

            if (taskId == initialTaskId || taskId == secondTaskId) {
                check(
                    mode == WindowManager.TRANSIT_OPEN || mode == WindowManager.TRANSIT_TO_FRONT
                ) {
                    "Expected task to be showing, but it is $mode"
                }
            }

            if (taskId == initialTaskId) {
                var splitRoot1 = change
                val parentToken = change.parent
                if (parentToken != null) {
                    splitRoot1 = transitionInfo.getChange(parentToken) ?: change
                }

                val topLevelToken = splitRoot1.parent
                if (topLevelToken != null) {
                    rootShellLayer = transitionInfo.getChange(topLevelToken)?.leash
                }

                dividerPos =
                    if (dp.isLeftRightSplit) change.endAbsBounds.right
                    else change.endAbsBounds.bottom
            }
        }

        check(rootShellLayer != null) {
            "Could not find a TransitionInfo.Change matching the initialTaskId"
        }

        // Shell animation: the apps are revealed toward end of the launch animation
        progressUpdater.addUpdateListener { valueAnimator: ValueAnimator ->
            val progress =
                Interpolators.clampToProgress(
                    Interpolators.LINEAR,
                    valueAnimator.animatedFraction,
                    timings.appRevealStartOffset,
                    timings.appRevealEndOffset
                )

            // Set the alpha of the shell layer (2 apps + divider)
            t.setAlpha(rootShellLayer, progress)
            t.apply()
        }

        // Create a new floating view in Launcher, positioned above the launching icon
        val drawableArea = launchingIconView.iconDrawableArea
        val appIcon1 = launchingIconView.info.contents[0].newIcon(launchingIconView.context)
        val appIcon2 = launchingIconView.info.contents[1].newIcon(launchingIconView.context)
        appIcon1.setBounds(0, 0, dp.iconSizePx, dp.iconSizePx)
        appIcon2.setBounds(0, 0, dp.iconSizePx, dp.iconSizePx)
        val floatingView =
            FloatingAppPairView.getFloatingAppPairView(
                launcher,
                drawableArea,
                appIcon1,
                appIcon2,
                dividerPos
            )

        // Launcher animation: animate the floating view, expanding to fill the display surface
        progressUpdater.addUpdateListener(
            object : MultiValueUpdateListener() {
                var mDx =
                    FloatProp(
                        floatingView.startingPosition.left,
                        dp.widthPx / 2f - floatingView.startingPosition.width() / 2f,
                        0f /* delay */,
                        timings.getDuration().toFloat(),
                        Interpolators.clampToProgress(
                            timings.getStagedRectXInterpolator(),
                            timings.stagedRectSlideStartOffset,
                            timings.stagedRectSlideEndOffset
                        )
                    )
                var mDy =
                    FloatProp(
                        floatingView.startingPosition.top,
                        dp.heightPx / 2f - floatingView.startingPosition.height() / 2f,
                        0f /* delay */,
                        timings.getDuration().toFloat(),
                        Interpolators.clampToProgress(
                            Interpolators.EMPHASIZED,
                            timings.stagedRectSlideStartOffset,
                            timings.stagedRectSlideEndOffset
                        )
                    )
                var mScaleX =
                    FloatProp(
                        1f /* start */,
                        dp.widthPx / floatingView.startingPosition.width(),
                        0f /* delay */,
                        timings.getDuration().toFloat(),
                        Interpolators.clampToProgress(
                            Interpolators.EMPHASIZED,
                            timings.stagedRectSlideStartOffset,
                            timings.stagedRectSlideEndOffset
                        )
                    )
                var mScaleY =
                    FloatProp(
                        1f /* start */,
                        dp.heightPx / floatingView.startingPosition.height(),
                        0f /* delay */,
                        timings.getDuration().toFloat(),
                        Interpolators.clampToProgress(
                            Interpolators.EMPHASIZED,
                            timings.stagedRectSlideStartOffset,
                            timings.stagedRectSlideEndOffset
                        )
                    )

                override fun onUpdate(percent: Float, initOnly: Boolean) {
                    floatingView.progress = percent
                    floatingView.x = mDx.value
                    floatingView.y = mDy.value
                    floatingView.scaleX = mScaleX.value
                    floatingView.scaleY = mScaleY.value
                    floatingView.invalidate()
                }
            }
        )

        // When animation ends, remove the floating view and run finishCallback
        progressUpdater.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    safeRemoveViewFromDragLayer(launcher, floatingView)
                    finishCallback.run()
                }
            }
        )

        launchAnimation.play(progressUpdater)
        launchAnimation.start()
    }

    /**
     * If we are launching split screen without any special animation from a starting View, we
     * simply fade in the starting apps and fade out launcher.
     */
    @VisibleForTesting
    fun composeFadeInSplitLaunchAnimator(
        initialTaskId: Int,
        secondTaskId: Int,
        transitionInfo: TransitionInfo,
        t: Transaction,
        finishCallback: Runnable
    ) {
        var splitRoot1: Change? = null
        var splitRoot2: Change? = null
        val openingTargets = ArrayList<SurfaceControl>()
        for (change in transitionInfo.changes) {
            val taskInfo: RunningTaskInfo = change.taskInfo ?: continue
            val taskId = taskInfo.taskId
            val mode = change.mode

            // Find the target tasks' root tasks since those are the split stages that need to
            // be animated (the tasks themselves are children and thus inherit animation).
            if (taskId == initialTaskId || taskId == secondTaskId) {
                check(
                    mode == WindowManager.TRANSIT_OPEN || mode == WindowManager.TRANSIT_TO_FRONT
                ) {
                    "Expected task to be showing, but it is $mode"
                }
            }

            if (taskId == initialTaskId) {
                splitRoot1 = change
                val parentToken1 = change.parent
                if (parentToken1 != null) {
                    splitRoot1 = transitionInfo.getChange(parentToken1) ?: change
                }

                if (splitRoot1?.leash != null) {
                    openingTargets.add(splitRoot1.leash)
                }
            }

            if (taskId == secondTaskId) {
                splitRoot2 = change
                val parentToken2 = change.parent
                if (parentToken2 != null) {
                    splitRoot2 = transitionInfo.getChange(parentToken2) ?: change
                }

                if (splitRoot2?.leash != null) {
                    openingTargets.add(splitRoot2.leash)
                }
            }
        }

        val animTransaction = Transaction()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.setDuration(QuickstepTransitionManager.SPLIT_LAUNCH_DURATION.toLong())
        animator.addUpdateListener { valueAnimator: ValueAnimator ->
            val progress = valueAnimator.animatedFraction
            for (leash in openingTargets) {
                animTransaction.setAlpha(leash, progress)
            }
            animTransaction.apply()
        }

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    for (leash in openingTargets) {
                        animTransaction.show(leash).setAlpha(leash, 0.0f)
                    }
                    animTransaction.apply()
                }

                override fun onAnimationEnd(animation: Animator) {
                    finishCallback.run()
                }
            }
        )

        if (splitRoot1 != null) {
            // Set the highest level split root alpha; we could technically use the parent of
            // either splitRoot1 or splitRoot2
            val parentToken = splitRoot1.parent
            var rootLayer: Change? = null
            if (parentToken != null) {
                rootLayer = transitionInfo.getChange(parentToken)
            }
            if (rootLayer != null && rootLayer.leash != null) {
                t.setAlpha(rootLayer.leash, 1f)
            }
        }

        t.apply()
        animator.start()
    }

    private fun safeRemoveViewFromDragLayer(launcher: StatefulActivity<*>, view: View?) {
        if (view != null) {
            launcher.dragLayer.removeView(view)
        }
    }
}
