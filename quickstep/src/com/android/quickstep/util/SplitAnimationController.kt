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
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.View
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.WindowContainerToken
import androidx.annotation.VisibleForTesting
import com.android.app.animation.Interpolators
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.QuickstepTransitionManager
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.logging.StatsLogManager.EventEnum
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource
import com.android.launcher3.views.BaseDragLayer
import com.android.quickstep.TaskViewUtils
import com.android.quickstep.views.FloatingAppPairView
import com.android.quickstep.views.FloatingTaskView
import com.android.quickstep.views.GroupedTaskView
import com.android.quickstep.views.IconAppChipView
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.SplitInstructionsView
import com.android.quickstep.views.TaskThumbnailViewDeprecated
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskView.TaskContainer
import com.android.quickstep.views.TaskViewIcon
import com.android.wm.shell.shared.TransitionUtil
import java.util.Optional
import java.util.function.Supplier

/**
 * Utils class to help run animations for initiating split screen from launcher. Will be expanded
 * with future refactors. Works in conjunction with the state stored in [SplitSelectStateController]
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
     * Returns different elements to animate for the initial split selection animation depending on
     * the state of the surface from which the split was initiated
     */
    fun getFirstAnimInitViews(
        taskViewSupplier: Supplier<TaskView>,
        splitSelectSourceSupplier: Supplier<SplitSelectSource?>
    ): SplitAnimInitProps {
        val splitSelectSource = splitSelectSourceSupplier.get()
        if (!splitSelectStateController.isAnimateCurrentTaskDismissal) {
            // Initiating from home
            return SplitAnimInitProps(
                splitSelectSource!!.view,
                originalBitmap = null,
                splitSelectSource.drawable,
                fadeWithThumbnail = false,
                isStagedTask = true,
                iconView = null
            )
        } else if (splitSelectStateController.isDismissingFromSplitPair) {
            // Initiating split from overview, but on a split pair
            val taskView = taskViewSupplier.get()
            for (container: TaskContainer in taskView.taskContainers) {
                if (container.task.getKey().getId() == splitSelectStateController.initialTaskId) {
                    val drawable = getDrawable(container.iconView, splitSelectSource)
                    return SplitAnimInitProps(
                        container.thumbnailView,
                        container.thumbnailView.thumbnail,
                        drawable!!,
                        fadeWithThumbnail = true,
                        isStagedTask = true,
                        iconView = container.iconView.asView()
                    )
                }
            }
            throw IllegalStateException(
                "Attempting to init split from existing split pair " +
                    "without a valid taskIdAttributeContainer"
            )
        } else {
            // Initiating split from overview on fullscreen task TaskView
            val taskView = taskViewSupplier.get()
            taskView.taskContainers.first().let {
                val drawable = getDrawable(it.iconView, splitSelectSource)
                return SplitAnimInitProps(
                    it.thumbnailView,
                    it.thumbnailView.thumbnail,
                    drawable!!,
                    fadeWithThumbnail = true,
                    isStagedTask = true,
                    iconView = it.iconView.asView()
                )
            }
        }
    }

    /**
     * Returns the drawable that's provided in iconView, however if that is null it falls back to
     * the drawable that's in splitSelectSource. TaskView's icon drawable can be null if the
     * TaskView is scrolled far enough off screen
     *
     * @return [Drawable]
     */
    fun getDrawable(iconView: TaskViewIcon, splitSelectSource: SplitSelectSource?): Drawable? {
        if (iconView.drawable == null && splitSelectSource != null) {
            return splitSelectSource.drawable
        }
        return iconView.drawable
    }

    /**
     * When selecting first app from split pair, second app's thumbnail remains. This animates the
     * second thumbnail by expanding it to take up the full taskViewWidth/Height and overlaying it
     * with [TaskThumbnailViewDeprecated]'s splashView. Adds animations to the provided builder.
     * Note: The app that **was not** selected as the first split app should be the container that's
     * passed through.
     *
     * @param builder Adds animation to this
     * @param taskIdAttributeContainer container of the app that **was not** selected
     * @param isPrimaryTaskSplitting if true, task that was split would be top/left in the pair
     *   (opposite of that representing [taskIdAttributeContainer])
     */
    fun addInitialSplitFromPair(
        taskIdAttributeContainer: TaskContainer,
        builder: PendingAnimation,
        deviceProfile: DeviceProfile,
        taskViewWidth: Int,
        taskViewHeight: Int,
        isPrimaryTaskSplitting: Boolean
    ) {
        val thumbnail = taskIdAttributeContainer.thumbnailView
        val iconView: View = taskIdAttributeContainer.iconView.asView()
        builder.add(ObjectAnimator.ofFloat(thumbnail, TaskThumbnailViewDeprecated.SPLASH_ALPHA, 1f))
        thumbnail.setShowSplashForSplitSelection(true)
        // With the new `IconAppChipView`, we always want to keep the chip pinned to the
        // top left of the task / thumbnail.
        if (enableOverviewIconMenu()) {
            builder.add(
                ObjectAnimator.ofFloat(
                    (iconView as IconAppChipView).splitTranslationX,
                    MULTI_PROPERTY_VALUE,
                    0f
                )
            )
            builder.add(
                ObjectAnimator.ofFloat(iconView.splitTranslationY, MULTI_PROPERTY_VALUE, 0f)
            )
        }
        if (deviceProfile.isLeftRightSplit) {
            // Center view first so scaling happens uniformly, alternatively we can move pivotX to 0
            val centerThumbnailTranslationX: Float = (taskViewWidth - thumbnail.width) / 2f
            val finalScaleX: Float = taskViewWidth.toFloat() / thumbnail.width
            builder.add(
                ObjectAnimator.ofFloat(
                    thumbnail,
                    TaskThumbnailViewDeprecated.SPLIT_SELECT_TRANSLATE_X,
                    centerThumbnailTranslationX
                )
            )
            if (!enableOverviewIconMenu()) {
                // icons are anchored from Gravity.END, so need to use negative translation
                val centerIconTranslationX: Float = (taskViewWidth - iconView.width) / 2f
                builder.add(
                    ObjectAnimator.ofFloat(iconView, View.TRANSLATION_X, -centerIconTranslationX)
                )
            }
            builder.add(ObjectAnimator.ofFloat(thumbnail, View.SCALE_X, finalScaleX))

            // Reset other dimensions
            // TODO(b/271468547), can't set Y translate to 0, need to account for top space
            thumbnail.scaleY = 1f
            val translateYResetVal: Float =
                if (!isPrimaryTaskSplitting) 0f
                else deviceProfile.overviewTaskThumbnailTopMarginPx.toFloat()
            builder.add(
                ObjectAnimator.ofFloat(
                    thumbnail,
                    TaskThumbnailViewDeprecated.SPLIT_SELECT_TRANSLATE_Y,
                    translateYResetVal
                )
            )
        } else {
            val thumbnailSize = taskViewHeight - deviceProfile.overviewTaskThumbnailTopMarginPx
            // Center view first so scaling happens uniformly, alternatively we can move pivotY to 0
            // primary thumbnail has layout margin above it, so secondary thumbnail needs to take
            // that into account. We should migrate to only using translations otherwise this
            // asymmetry causes problems..

            // Icon defaults to center | horizontal, we add additional translation for split
            var centerThumbnailTranslationY: Float

            // TODO(b/271468547), primary thumbnail has layout margin above it, so secondary
            //  thumbnail needs to take that into account. We should migrate to only using
            //  translations otherwise this asymmetry causes problems..
            if (isPrimaryTaskSplitting) {
                centerThumbnailTranslationY = (thumbnailSize - thumbnail.height) / 2f
                centerThumbnailTranslationY +=
                    deviceProfile.overviewTaskThumbnailTopMarginPx.toFloat()
            } else {
                centerThumbnailTranslationY = (thumbnailSize - thumbnail.height) / 2f
            }
            val finalScaleY: Float = thumbnailSize.toFloat() / thumbnail.height
            builder.add(
                ObjectAnimator.ofFloat(
                    thumbnail,
                    TaskThumbnailViewDeprecated.SPLIT_SELECT_TRANSLATE_Y,
                    centerThumbnailTranslationY
                )
            )

            if (!enableOverviewIconMenu()) {
                // icons are anchored from Gravity.END, so need to use negative translation
                builder.add(ObjectAnimator.ofFloat(iconView, View.TRANSLATION_X, 0f))
            }
            builder.add(ObjectAnimator.ofFloat(thumbnail, View.SCALE_Y, finalScaleY))

            // Reset other dimensions
            thumbnail.scaleX = 1f
            builder.add(
                ObjectAnimator.ofFloat(
                    thumbnail,
                    TaskThumbnailViewDeprecated.SPLIT_SELECT_TRANSLATE_X,
                    0f
                )
            )
        }
    }

    /**
     * Creates and returns a view to fade in at .4 animation progress and adds it to the provided
     * [pendingAnimation]. Assumes that animation will be the final split placeholder launch anim.
     *
     * [secondPlaceholderEndingBounds] refers to the second placeholder view that gets added on
     * screen, not the logical second app. For landscape it's the left app and for portrait the top
     * one.
     */
    fun addDividerPlaceholderViewToAnim(
        pendingAnimation: PendingAnimation,
        container: RecentsViewContainer,
        secondPlaceholderEndingBounds: Rect,
        context: Context
    ): View {
        val mSplitDividerPlaceholderView = View(context)
        val recentsView = container.getOverviewPanel<RecentsView<*, *>>()
        val dp: com.android.launcher3.DeviceProfile = container.getDeviceProfile()
        // Add it before/under the most recently added first floating taskView
        val firstAddedSplitViewIndex: Int =
            container
                .getDragLayer()
                .indexOfChild(recentsView.splitSelectController.firstFloatingTaskView)
        container.getDragLayer().addView(mSplitDividerPlaceholderView, firstAddedSplitViewIndex)
        val lp = mSplitDividerPlaceholderView.layoutParams as InsettableFrameLayout.LayoutParams
        lp.topMargin = 0

        if (dp.isLeftRightSplit) {
            lp.height = secondPlaceholderEndingBounds.height()
            lp.width =
                container
                    .asContext()
                    .resources
                    .getDimensionPixelSize(R.dimen.split_divider_handle_region_height)
            mSplitDividerPlaceholderView.translationX =
                secondPlaceholderEndingBounds.right - lp.width / 2f
            mSplitDividerPlaceholderView.translationY = 0f
        } else {
            lp.height =
                container
                    .asContext()
                    .resources
                    .getDimensionPixelSize(R.dimen.split_divider_handle_region_height)
            lp.width = secondPlaceholderEndingBounds.width()
            mSplitDividerPlaceholderView.translationY =
                secondPlaceholderEndingBounds.top - lp.height / 2f
            mSplitDividerPlaceholderView.translationX = 0f
        }

        mSplitDividerPlaceholderView.alpha = 0f
        mSplitDividerPlaceholderView.setBackgroundColor(
            container.asContext().resources.getColor(R.color.taskbar_background_dark)
        )
        val timings = AnimUtils.getDeviceSplitToConfirmTimings(dp.isTablet)
        pendingAnimation.setViewAlpha(
            mSplitDividerPlaceholderView,
            1f,
            Interpolators.clampToProgress(timings.stagedRectScaleXInterpolator, 0.4f, 1f)
        )
        return mSplitDividerPlaceholderView
    }

    /** Does not play any animation if user is not currently in split selection state. */
    fun playPlaceholderDismissAnim(container: RecentsViewContainer, splitDismissEvent: EventEnum) {
        if (!splitSelectStateController.isSplitSelectActive) {
            return
        }

        val anim = createPlaceholderDismissAnim(container, splitDismissEvent, null /*duration*/)
        anim.start()
    }

    /**
     * Returns [AnimatorSet] which slides initial split placeholder view offscreen and logs an event
     * for why split is being dismissed
     */
    fun createPlaceholderDismissAnim(
        container: RecentsViewContainer,
        splitDismissEvent: EventEnum,
        duration: Long?
    ): AnimatorSet {
        val animatorSet = AnimatorSet()
        duration?.let { animatorSet.duration = it }
        val recentsView: RecentsView<*, *> = container.getOverviewPanel()
        val floatingTask: FloatingTaskView =
            splitSelectStateController.firstFloatingTaskView ?: return animatorSet

        // We are in split selection state currently, transitioning to another state
        val dragLayer: BaseDragLayer<*> = container.dragLayer
        val onScreenRectF = RectF()
        Utilities.getBoundsForViewInDragLayer(
            dragLayer,
            floatingTask,
            Rect(0, 0, floatingTask.width, floatingTask.height),
            false,
            null,
            onScreenRectF
        )
        // Get the part of the floatingTask that intersects with the DragLayer (i.e. the
        // on-screen portion)
        onScreenRectF.intersect(
            dragLayer.left.toFloat(),
            dragLayer.top.toFloat(),
            dragLayer.right.toFloat(),
            dragLayer.bottom.toFloat()
        )
        animatorSet.play(
            ObjectAnimator.ofFloat(
                floatingTask,
                FloatingTaskView.PRIMARY_TRANSLATE_OFFSCREEN,
                recentsView.pagedOrientationHandler.getFloatingTaskOffscreenTranslationTarget(
                    floatingTask,
                    onScreenRectF,
                    floatingTask.stagePosition,
                    container.deviceProfile
                )
            )
        )
        animatorSet.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    splitSelectStateController.resetState()
                    safeRemoveViewFromDragLayer(
                        container,
                        splitSelectStateController.splitInstructionsView
                    )
                }
            }
        )
        splitSelectStateController.logExitReason(splitDismissEvent)
        return animatorSet
    }

    /**
     * Returns a [PendingAnimation] to animate in the chip to instruct a user to select a second app
     * for splitscreen
     */
    fun getShowSplitInstructionsAnim(container: RecentsViewContainer): PendingAnimation {
        safeRemoveViewFromDragLayer(container, splitSelectStateController.splitInstructionsView)
        val splitInstructionsView = SplitInstructionsView.getSplitInstructionsView(container)
        splitSelectStateController.splitInstructionsView = splitInstructionsView
        val timings = AnimUtils.getDeviceOverviewToSplitTimings(container.deviceProfile.isTablet)
        val anim = PendingAnimation(100 /*duration */)
        splitInstructionsView.alpha = 0f
        anim.setViewAlpha(
            splitInstructionsView,
            1f,
            Interpolators.clampToProgress(
                Interpolators.LINEAR,
                timings.instructionsContainerFadeInStartOffset,
                timings.instructionsContainerFadeInEndOffset
            )
        )
        anim.addFloat(
            splitInstructionsView,
            SplitInstructionsView.UNFOLD,
            0.1f,
            1f,
            Interpolators.clampToProgress(
                Interpolators.EMPHASIZED_DECELERATE,
                timings.instructionsUnfoldStartOffset,
                timings.instructionsUnfoldEndOffset
            )
        )
        return anim
    }

    /** Removes the split instructions view from [launcher] drag layer. */
    fun removeSplitInstructionsView(container: RecentsViewContainer) {
        safeRemoveViewFromDragLayer(container, splitSelectStateController.splitInstructionsView)
    }

    /**
     * Animates the first placeholder view to fullscreen and launches its task.
     *
     * TODO(b/276361926): Remove the [resetCallback] option once contextual launches
     */
    fun playAnimPlaceholderToFullscreen(
        container: RecentsViewContainer,
        view: View,
        resetCallback: Optional<Runnable>
    ) {
        val stagedTaskView = view as FloatingTaskView

        val isTablet: Boolean = container.deviceProfile.isTablet
        val duration =
            if (isTablet) SplitAnimationTimings.TABLET_CONFIRM_DURATION
            else SplitAnimationTimings.PHONE_CONFIRM_DURATION

        val pendingAnimation = PendingAnimation(duration.toLong())
        val firstTaskStartingBounds = Rect()
        val firstTaskEndingBounds = Rect()

        stagedTaskView.getBoundsOnScreen(firstTaskStartingBounds)
        container.dragLayer.getBoundsOnScreen(firstTaskEndingBounds)
        splitSelectStateController.setLaunchingFirstAppFullscreen()

        stagedTaskView.addConfirmAnimation(
            pendingAnimation,
            RectF(firstTaskStartingBounds),
            firstTaskEndingBounds,
            false /* fadeWithThumbnail */,
            true /* isStagedTask */
        )

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
            val appPairLaunchingAppIndex = hasChangesForBothAppPairs(launchingIconView, info)
            if (appPairLaunchingAppIndex == -1) {
                // Launch split app pair animation
                composeIconSplitLaunchAnimator(launchingIconView, info, t, finishCallback)
            } else {
                composeFullscreenIconSplitLaunchAnimator(
                    launchingIconView,
                    info,
                    t,
                    finishCallback,
                    appPairLaunchingAppIndex
                )
            }
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
     * @return -1 if [transitionInfo] contains both apps of the app pair to be animated, otherwise
     *   the integer index corresponding to [launchingIconView]'s contents for the single app to be
     *   animated
     */
    fun hasChangesForBothAppPairs(
        launchingIconView: AppPairIcon,
        transitionInfo: TransitionInfo
    ): Int {
        val intent1 = launchingIconView.info.getFirstApp().intent.component?.packageName
        val intent2 = launchingIconView.info.getSecondApp().intent.component?.packageName
        var launchFullscreenAppIndex = -1
        for (change in transitionInfo.changes) {
            val taskInfo: RunningTaskInfo = change.taskInfo ?: continue
            if (
                TransitionUtil.isOpeningType(change.mode) &&
                    taskInfo.windowingMode == WINDOWING_MODE_FULLSCREEN
            ) {
                val baseIntent = taskInfo.baseIntent.component?.packageName
                if (baseIntent == intent1) {
                    if (launchFullscreenAppIndex > -1) {
                        launchFullscreenAppIndex = -1
                        break
                    }
                    launchFullscreenAppIndex = 0
                } else if (baseIntent == intent2) {
                    if (launchFullscreenAppIndex > -1) {
                        launchFullscreenAppIndex = -1
                        break
                    }
                    launchFullscreenAppIndex = 1
                }
            }
        }
        return launchFullscreenAppIndex
    }

    /**
     * When the user taps an app pair icon to launch split, this will play the tasks' launch
     * animation from the position of the icon.
     *
     * To find the root shell leash that we want to fade in, we do the following: The Changes we
     * receive in transitionInfo are structured like this
     *
     *     Root (grandparent)
     *     |
     *     |--> Split Root 1 (left/top side parent) (WINDOWING_MODE_MULTI_WINDOW)
     *     |   |
     *     |    --> App 1 (left/top side child) (WINDOWING_MODE_MULTI_WINDOW)
     *     |--> Divider
     *     |--> Split Root 2 (right/bottom side parent) (WINDOWING_MODE_MULTI_WINDOW)
     *         |
     *          --> App 2 (right/bottom side child) (WINDOWING_MODE_MULTI_WINDOW)
     *
     * We want to animate the Root (grandparent) so that it affects both apps and the divider. To do
     * this, we find one of the nodes with WINDOWING_MODE_MULTI_WINDOW (one of the left-side ones,
     * for simplicity) and traverse the tree until we find the grandparent.
     *
     * This function is only called when we are animating the app pair in from scratch. It is NOT
     * called when we are animating in from an existing visible TaskView tile or an app that is
     * already on screen.
     */
    @VisibleForTesting
    fun composeIconSplitLaunchAnimator(
        launchingIconView: AppPairIcon,
        transitionInfo: TransitionInfo,
        t: Transaction,
        finishCallback: Runnable
    ) {
        // If launching an app pair from Taskbar inside of an app context (no access to Launcher),
        // use the scale-up animation
        if (launchingIconView.context is TaskbarActivityContext) {
            composeScaleUpLaunchAnimation(
                transitionInfo,
                t,
                finishCallback,
                WINDOWING_MODE_MULTI_WINDOW
            )
            return
        }

        // Else we are in Launcher and can launch with the full icon stretch-and-split animation.
        val launcher = QuickstepLauncher.getLauncher(launchingIconView.context)
        val dp = launcher.deviceProfile

        // Create an AnimatorSet that will run both shell and launcher transitions together
        val launchAnimation = AnimatorSet()
        var rootCandidate: Change? = null

        for (change in transitionInfo.changes) {
            val taskInfo: RunningTaskInfo = change.taskInfo ?: continue

            // TODO (b/316490565): Replace this logic when SplitBounds is available to
            //  startAnimation() and we can know the precise taskIds of launching tasks.
            // Find a change that has WINDOWING_MODE_MULTI_WINDOW.
            if (
                taskInfo.windowingMode == WINDOWING_MODE_MULTI_WINDOW &&
                    (change.mode == TRANSIT_OPEN || change.mode == TRANSIT_TO_FRONT)
            ) {
                // Check if it is a left/top app.
                val isLeftTopApp =
                    (dp.isLeftRightSplit && change.endAbsBounds.left == 0) ||
                        (!dp.isLeftRightSplit && change.endAbsBounds.top == 0)
                if (isLeftTopApp) {
                    // Found one!
                    rootCandidate = change
                    break
                }
            }
        }

        // If we could not find a proper root candidate, something went wrong.
        check(rootCandidate != null) { "Could not find a split root candidate" }

        // Find the place where our left/top app window meets the divider (used for the
        // launcher side animation)
        val dividerPos =
            if (dp.isLeftRightSplit) rootCandidate.endAbsBounds.right
            else rootCandidate.endAbsBounds.bottom

        // Recurse up the tree until parent is null, then we've found our root.
        var parentToken: WindowContainerToken? = rootCandidate.parent
        while (parentToken != null) {
            rootCandidate = transitionInfo.getChange(parentToken) ?: break
            parentToken = rootCandidate.parent
        }

        // Make sure nothing weird happened, like getChange() returning null.
        check(rootCandidate != null) { "Failed to find a root leash" }

        // Create a new floating view in Launcher, positioned above the launching icon
        val drawableArea = launchingIconView.iconDrawableArea
        val appIcon1 = launchingIconView.info.getFirstApp().newIcon(launchingIconView.context)
        val appIcon2 = launchingIconView.info.getSecondApp().newIcon(launchingIconView.context)
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
        floatingView.bringToFront()

        launchAnimation.play(
            getIconLaunchValueAnimator(t, dp, finishCallback, launcher, floatingView, rootCandidate)
        )
        launchAnimation.start()
    }

    /**
     * Similar to [composeIconSplitLaunchAnimator], but instructs [FloatingAppPairView] to animate a
     * single fullscreen icon + background instead of for a pair
     */
    @VisibleForTesting
    fun composeFullscreenIconSplitLaunchAnimator(
        launchingIconView: AppPairIcon,
        transitionInfo: TransitionInfo,
        t: Transaction,
        finishCallback: Runnable,
        launchFullscreenIndex: Int
    ) {
        // If launching an app pair from Taskbar inside of an app context (no access to Launcher),
        // use the scale-up animation
        if (launchingIconView.context is TaskbarActivityContext) {
            composeScaleUpLaunchAnimation(
                transitionInfo,
                t,
                finishCallback,
                WINDOWING_MODE_FULLSCREEN
            )
            return
        }

        // Else we are in Launcher and can launch with the full icon stretch-and-split animation.
        val launcher = QuickstepLauncher.getLauncher(launchingIconView.context)
        val dp = launcher.deviceProfile

        // Create an AnimatorSet that will run both shell and launcher transitions together
        val launchAnimation = AnimatorSet()

        val appInfo =
            launchingIconView.info.getContents()[launchFullscreenIndex] as WorkspaceItemInfo
        val intentToLaunch = appInfo.intent.component?.packageName
        var rootCandidate: Change? = null
        for (change in transitionInfo.changes) {
            val taskInfo: RunningTaskInfo = change.taskInfo ?: continue
            val baseIntent = taskInfo.baseIntent.component?.packageName
            if (
                TransitionUtil.isOpeningType(change.mode) &&
                    taskInfo.windowingMode == WINDOWING_MODE_FULLSCREEN &&
                    baseIntent == intentToLaunch
            ) {
                rootCandidate = change
            }
        }

        // If we could not find a proper root candidate, something went wrong.
        check(rootCandidate != null) { "Could not find a split root candidate" }

        // Recurse up the tree until parent is null, then we've found our root.
        var parentToken: WindowContainerToken? = rootCandidate.parent
        while (parentToken != null) {
            rootCandidate = transitionInfo.getChange(parentToken) ?: break
            parentToken = rootCandidate.parent
        }

        // Make sure nothing weird happened, like getChange() returning null.
        check(rootCandidate != null) { "Failed to find a root leash" }

        // Create a new floating view in Launcher, positioned above the launching icon
        val drawableArea = launchingIconView.iconDrawableArea
        val appIcon = appInfo.newIcon(launchingIconView.context)
        appIcon.setBounds(0, 0, dp.iconSizePx, dp.iconSizePx)

        val floatingView =
            FloatingAppPairView.getFloatingAppPairView(
                launcher,
                drawableArea,
                appIcon,
                null /*appIcon2*/,
                0 /*dividerPos*/
            )
        floatingView.bringToFront()
        launchAnimation.play(
            getIconLaunchValueAnimator(t, dp, finishCallback, launcher, floatingView, rootCandidate)
        )
        launchAnimation.start()
    }

    private fun getIconLaunchValueAnimator(
        t: Transaction,
        dp: com.android.launcher3.DeviceProfile,
        finishCallback: Runnable,
        launcher: QuickstepLauncher,
        floatingView: FloatingAppPairView,
        rootCandidate: Change
    ): ValueAnimator {
        val progressUpdater = ValueAnimator.ofFloat(0f, 1f)
        val timings = AnimUtils.getDeviceAppPairLaunchTimings(dp.isTablet)
        progressUpdater.setDuration(timings.getDuration().toLong())
        progressUpdater.interpolator = Interpolators.LINEAR

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
            t.setAlpha(rootCandidate.leash, progress)
            t.apply()
        }

        progressUpdater.addUpdateListener(
            object : MultiValueUpdateListener() {
                var mDx =
                    FloatProp(
                        floatingView.startingPosition.left,
                        dp.widthPx / 2f - floatingView.startingPosition.width() / 2f,
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
        progressUpdater.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    safeRemoveViewFromDragLayer(launcher, floatingView)
                    finishCallback.run()
                }
            }
        )

        return progressUpdater
    }

    /**
     * This is a scale-up-and-fade-in animation (34% to 100%) for launching an app in Overview when
     * there is no visible associated tile to expand from. [windowingMode] helps determine whether
     * we are looking for a split or a single fullscreen [Change]
     */
    @VisibleForTesting
    fun composeScaleUpLaunchAnimation(
        transitionInfo: TransitionInfo,
        t: Transaction,
        finishCallback: Runnable,
        windowingMode: Int
    ) {
        val launchAnimation = AnimatorSet()
        val progressUpdater = ValueAnimator.ofFloat(0f, 1f)
        progressUpdater.setDuration(QuickstepTransitionManager.APP_LAUNCH_DURATION)
        progressUpdater.interpolator = Interpolators.EMPHASIZED

        var rootCandidate: Change? = null

        for (change in transitionInfo.changes) {
            val taskInfo: RunningTaskInfo = change.taskInfo ?: continue

            // TODO (b/316490565): Replace this logic when SplitBounds is available to
            //  startAnimation() and we can know the precise taskIds of launching tasks.
            if (
                taskInfo.windowingMode == windowingMode &&
                    (change.mode == TRANSIT_OPEN || change.mode == TRANSIT_TO_FRONT)
            ) {
                // Found one!
                rootCandidate = change
                break
            }
        }

        // If we could not find a proper root candidate, something went wrong.
        check(rootCandidate != null) { "Could not find a split root candidate" }

        // Recurse up the tree until parent is null, then we've found our root.
        var parentToken: WindowContainerToken? = rootCandidate.parent
        while (parentToken != null) {
            rootCandidate = transitionInfo.getChange(parentToken) ?: break
            parentToken = rootCandidate.parent
        }

        // Make sure nothing weird happened, like getChange() returning null.
        check(rootCandidate != null) { "Failed to find a root leash" }

        // Starting position is a 34% size tile centered in the middle of the screen.
        // Ending position is the full device screen.
        val screenBounds = rootCandidate.endAbsBounds
        val startingScale = 0.34f
        val startX =
            screenBounds.left +
                ((screenBounds.right - screenBounds.left) * ((1 - startingScale) / 2f))
        val startY =
            screenBounds.top +
                ((screenBounds.bottom - screenBounds.top) * ((1 - startingScale) / 2f))
        val endX = screenBounds.left
        val endY = screenBounds.top

        progressUpdater.addUpdateListener { valueAnimator: ValueAnimator ->
            val progress = valueAnimator.animatedFraction

            val x = startX + ((endX - startX) * progress)
            val y = startY + ((endY - startY) * progress)
            val scale = startingScale + ((1 - startingScale) * progress)

            t.setPosition(rootCandidate.leash, x, y)
            t.setScale(rootCandidate.leash, scale, scale)
            t.setAlpha(rootCandidate.leash, progress)
            t.apply()
        }

        // When animation ends,  run finishCallback
        progressUpdater.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
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
                check(mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT) {
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

        if (splitRoot1 != null) {
            // Set the highest level split root alpha; we could technically use the parent of
            // either splitRoot1 or splitRoot2
            val parentToken = splitRoot1.parent
            var rootLayer: Change? = null
            if (parentToken != null) {
                rootLayer = transitionInfo.getChange(parentToken)
            }
            if (rootLayer != null && rootLayer.leash != null) {
                openingTargets.add(rootLayer.leash)
            }
        }

        val animTransaction = Transaction()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.setDuration(QuickstepTransitionManager.SPLIT_LAUNCH_DURATION.toLong())
        animator.addUpdateListener { valueAnimator: ValueAnimator ->
            val progress =
                Interpolators.clampToProgress(
                    Interpolators.LINEAR,
                    valueAnimator.animatedFraction,
                    0.8f,
                    1f
                )
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

        t.apply()
        animator.start()
    }

    private fun safeRemoveViewFromDragLayer(container: RecentsViewContainer, view: View?) {
        if (view != null) {
            container.dragLayer.removeView(view)
        }
    }
}
