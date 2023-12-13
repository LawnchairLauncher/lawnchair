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
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_DIVIDER_MENU_CLOSE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_DIVIDER_MENU_OPEN
import com.android.launcher3.taskbar.TaskbarDividerPopupView.Companion.createAndPopulate
import java.io.PrintWriter

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
                val animateToValue =
                    if (!launcherPrefs.get(TASKBAR_PINNING)) {
                        PINNING_PERSISTENT
                    } else {
                        PINNING_TRANSIENT
                    }
                taskbarSharedState.taskbarWasPinned = animateToValue == PINNING_TRANSIENT
                animateTaskbarPinning(animateToValue)
            }
    }

    fun showPinningView(view: View) {
        context.isTaskbarWindowFullscreen = true
        view.post {
            val popupView = getPopupView(view)
            popupView.requestFocus()
            popupView.onCloseCallback = onCloseCallback
            context.onPopupVisibilityChanged(true)
            popupView.show()
            statsLogManager.logger().log(LAUNCHER_TASKBAR_DIVIDER_MENU_OPEN)
        }
    }

    @VisibleForTesting
    fun getPopupView(view: View): TaskbarDividerPopupView<*> {
        return createAndPopulate(view, context)
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
            taskbarViewController.taskbarIconTranslationXForPinning.animateToValue(animateToValue)
        )

        return animatorSet
    }

    private fun updateIsAnimatingTaskbarPinningAndNotifyTaskbarDragLayer(isAnimating: Boolean) {
        isAnimatingTaskbarPinning = isAnimating
        context.dragLayer.setAnimatingTaskbarPinning(isAnimating)
    }

    @VisibleForTesting
    fun recreateTaskbarAndUpdatePinningValue() {
        updateIsAnimatingTaskbarPinningAndNotifyTaskbarDragLayer(false)
        launcherPrefs.put(TASKBAR_PINNING, !launcherPrefs.get(TASKBAR_PINNING))
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "TaskbarPinningController:")
        pw.println("$prefix\tisAnimatingTaskbarPinning=$isAnimatingTaskbarPinning")
        pw.println("$prefix\tTASKBAR_PINNING shared pref =" + launcherPrefs.get(TASKBAR_PINNING))
    }

    companion object {
        const val PINNING_PERSISTENT = 1f
        const val PINNING_TRANSIENT = 0f
        const val PINNING_ANIMATION_DURATION = 500L
    }
}
