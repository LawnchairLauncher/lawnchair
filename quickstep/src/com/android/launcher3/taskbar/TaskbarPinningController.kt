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
import android.view.View
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
    private val launcherPrefs = LauncherPrefs.get(context)
    private val statsLogManager = context.statsLogManager
    private var isAnimatingTaskbarPinning = false

    fun init(taskbarControllers: TaskbarControllers, sharedState: TaskbarSharedState) {
        controllers = taskbarControllers
        taskbarSharedState = sharedState
    }

    fun showPinningView(view: View) {
        context.isTaskbarWindowFullscreen = true

        view.post {
            val popupView = createAndPopulate(view, context)
            popupView.requestFocus()

            popupView.onCloseCallback =
                callback@{ didPreferenceChange ->
                    statsLogManager.logger().log(LAUNCHER_TASKBAR_DIVIDER_MENU_CLOSE)
                    context.dragLayer.post { context.onPopupVisibilityChanged(false) }

                    if (!didPreferenceChange) {
                        return@callback
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
            context.onPopupVisibilityChanged(true)
            popupView.show()
            statsLogManager.logger().log(LAUNCHER_TASKBAR_DIVIDER_MENU_OPEN)
        }
    }

    private fun animateTaskbarPinning(animateToValue: Float) {
        val animatorSet = AnimatorSet()
        val taskbarViewController = controllers.taskbarViewController
        val dragLayerController = controllers.taskbarDragLayerController

        animatorSet.playTogether(
            dragLayerController.taskbarBackgroundProgress.animateToValue(animateToValue),
            taskbarViewController.taskbarIconTranslationYForPinning.animateToValue(animateToValue),
            taskbarViewController.taskbarIconScaleForPinning.animateToValue(animateToValue),
            taskbarViewController.taskbarIconTranslationXForPinning.animateToValue(animateToValue)
        )

        animatorSet.doOnEnd { recreateTaskbarAndUpdatePinningValue() }
        animatorSet.duration = PINNING_ANIMATION_DURATION
        updateIsAnimatingTaskbarPinningAndNotifyTaskbarDragLayer(true)
        animatorSet.start()
    }

    private fun updateIsAnimatingTaskbarPinningAndNotifyTaskbarDragLayer(isAnimating: Boolean) {
        isAnimatingTaskbarPinning = isAnimating
        context.dragLayer.setAnimatingTaskbarPinning(isAnimating)
    }

    private fun recreateTaskbarAndUpdatePinningValue() {
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
