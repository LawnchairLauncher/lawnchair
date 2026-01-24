/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar

import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.animation.doOnEnd
import com.android.app.animation.Interpolators
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING_IN_DESKTOP_MODE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DESKTOP_MODE_TASKBAR_PINNED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DESKTOP_MODE_TASKBAR_UNPINNED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_DIVIDER_MENU_CLOSE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_DIVIDER_MENU_OPEN
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_PINNED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_UNPINNED
import com.android.launcher3.taskbar.TaskbarDividerPopupView.Companion.createAndPopulate
import java.io.PrintWriter
import kotlin.jvm.optionals.getOrNull

/** Controls taskbar pinning through a popup view. */
class TaskbarPinningController(private val context: TaskbarActivityContext) :
    TaskbarControllers.LoggableTaskbarController {

    private lateinit var controllers: TaskbarControllers
    private lateinit var taskbarSharedState: TaskbarSharedState
    private lateinit var launcherPrefs: LauncherPrefs
    private val statsLogManager = context.statsLogManager
    @VisibleForTesting var isAnimatingTaskbarPinning = false
    @VisibleForTesting lateinit var onCloseCallback: (preferenceChanged: Boolean) -> Unit

    @SuppressLint("VisibleForTests")
    fun init(taskbarControllers: TaskbarControllers, sharedState: TaskbarSharedState) {
        controllers = taskbarControllers
        taskbarSharedState = sharedState
        launcherPrefs = context.launcherPrefs
        onCloseCallback =
            fun(didPreferenceChange: Boolean) {
                statsLogManager.logger().log(LAUNCHER_TASKBAR_DIVIDER_MENU_CLOSE)
                context.dragLayer.post { context.onPopupVisibilityChanged(false) }

                if (!didPreferenceChange) {
                    return
                }

                if (
                    controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar(
                        context.displayId
                    )
                ) {
                    val shouldPinDesktopTaskbar =
                        !launcherPrefs.get(TASKBAR_PINNING_IN_DESKTOP_MODE)
                    val logEvent =
                        if (shouldPinDesktopTaskbar) {
                            LAUNCHER_DESKTOP_MODE_TASKBAR_PINNED
                        } else {
                            LAUNCHER_DESKTOP_MODE_TASKBAR_UNPINNED
                        }
                    statsLogManager.logger().log(logEvent)
                    launcherPrefs.put(TASKBAR_PINNING_IN_DESKTOP_MODE, shouldPinDesktopTaskbar)
                    return
                }

                val shouldPinTaskbar = !launcherPrefs.get(TASKBAR_PINNING)

                val animateToValue =
                    if (shouldPinTaskbar) {
                        statsLogManager.logger().log(LAUNCHER_TASKBAR_PINNED)
                        PINNING_PERSISTENT
                    } else {
                        statsLogManager.logger().log(LAUNCHER_TASKBAR_UNPINNED)
                        PINNING_TRANSIENT
                    }

                taskbarSharedState.taskbarWasPinned = animateToValue == PINNING_TRANSIENT
                animateTaskbarPinning(animateToValue)
            }
    }

    fun showPinningView(view: View, horizontalPosition: Float = -1f) {
        context.isTaskbarWindowFullscreen = true
        view.post {
            val popupView = getPopupView(view, horizontalPosition)
            popupView.requestFocus()
            popupView.onCloseCallback = onCloseCallback
            context.onPopupVisibilityChanged(true)
            popupView.show()
            statsLogManager.logger().log(LAUNCHER_TASKBAR_DIVIDER_MENU_OPEN)
        }
    }

    @VisibleForTesting
    fun getPopupView(view: View, horizontalPosition: Float = -1f): TaskbarDividerPopupView<*> {
        return createAndPopulate(view, context, horizontalPosition)
    }

    @VisibleForTesting
    fun animateTaskbarPinning(animateToValue: Float) {
        val taskbarViewController = controllers.taskbarViewController
        val animatorSet =
            getAnimatorSetForTaskbarPinningAnimation(animateToValue).apply {
                doOnEnd { recreateTaskbarAndUpdatePinningValue() }
                duration = PINNING_ANIMATION_DURATION
            }
        controllers.taskbarOverlayController.hideWindow()
        updateIsAnimatingTaskbarPinningAndNotifyTaskbarDragLayer(true)
        taskbarViewController.animateAwayNotificationDotsDuringTaskbarPinningAnimation()
        animatorSet.start()
    }

    @VisibleForTesting
    fun getAnimatorSetForTaskbarPinningAnimation(animateToValue: Float): AnimatorSet {
        val animatorSet = AnimatorSet()
        val taskbarViewController = controllers.taskbarViewController
        val dragLayerController = controllers.taskbarDragLayerController

        animatorSet.playTogether(
            dragLayerController.taskbarBackgroundProgress.animateToValue(animateToValue),
            taskbarViewController.taskbarIconTranslationYForPinning.animateToValue(animateToValue),
            taskbarViewController.taskbarIconScaleForPinning.animateToValue(animateToValue),
            taskbarViewController.taskbarIconTranslationXForPinning.animateToValue(animateToValue),
        )
        controllers.bubbleControllers.getOrNull()?.bubbleBarViewController?.let {
            // if bubble bar is not visible no need to add it`s animations
            if (!it.isBubbleBarVisible) return@let
            animatorSet.playTogether(it.bubbleBarPinning.animateToValue(animateToValue))
        }
        animatorSet.interpolator = Interpolators.EMPHASIZED
        return animatorSet
    }

    private fun updateIsAnimatingTaskbarPinningAndNotifyTaskbarDragLayer(isAnimating: Boolean) {
        isAnimatingTaskbarPinning = isAnimating
        context.dragLayer.setAnimatingTaskbarPinning(isAnimating)
    }

    @VisibleForTesting
    fun recreateTaskbarAndUpdatePinningValue() {
        updateIsAnimatingTaskbarPinningAndNotifyTaskbarDragLayer(false)
        if (
            controllers.taskbarDesktopModeController.isInDesktopModeAndNotInOverview(
                context.displayId
            )
        ) {
            launcherPrefs.put(
                TASKBAR_PINNING_IN_DESKTOP_MODE,
                !launcherPrefs.get(TASKBAR_PINNING_IN_DESKTOP_MODE),
            )
        } else {
            launcherPrefs.put(TASKBAR_PINNING, !launcherPrefs.get(TASKBAR_PINNING))
        }
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "TaskbarPinningController:")
        pw.println("$prefix\tisAnimatingTaskbarPinning=$isAnimatingTaskbarPinning")
        pw.println("$prefix\tTASKBAR_PINNING shared pref =" + launcherPrefs.get(TASKBAR_PINNING))
        pw.println(
            "$prefix\tTASKBAR_PINNING_IN_DESKTOP_MODE shared pref in desktop mode =" +
                launcherPrefs.get(TASKBAR_PINNING_IN_DESKTOP_MODE)
        )
    }

    companion object {
        const val PINNING_PERSISTENT = 1f
        const val PINNING_TRANSIENT = 0f
        const val PINNING_ANIMATION_DURATION = 600L
    }
}
