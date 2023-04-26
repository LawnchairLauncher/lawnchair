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

import android.view.View
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.taskbar.TaskbarDividerPopupView.Companion.createAndPopulate
import java.io.PrintWriter

/** Controls taskbar pinning through a popup view. */
class TaskbarDividerPopupController(private val context: TaskbarActivityContext) :
    TaskbarControllers.LoggableTaskbarController {

    private lateinit var controllers: TaskbarControllers
    private val launcherPrefs = LauncherPrefs.get(context)

    fun init(taskbarControllers: TaskbarControllers) {
        controllers = taskbarControllers
    }

    fun showPinningView(view: View) {
        context.isTaskbarWindowFullscreen = true

        view.post {
            val popupView = createAndPopulate(view, context)
            popupView.requestFocus()

            popupView.onCloseCallback =
                callback@{ didPreferenceChange ->
                    context.dragLayer.post { context.onPopupVisibilityChanged(false) }

                    if (!didPreferenceChange) {
                        return@callback
                    }

                    if (launcherPrefs.get(TASKBAR_PINNING)) {
                        animateTransientToPersistentTaskbar()
                    } else {
                        animatePersistentToTransientTaskbar()
                    }
                }
            popupView.changePreference = {
                launcherPrefs.put(TASKBAR_PINNING, !launcherPrefs.get(TASKBAR_PINNING))
            }
            context.onPopupVisibilityChanged(true)
            popupView.show()
        }
    }

    // TODO(b/265436799): provide animation/transition from transient taskbar to persistent one
    private fun animateTransientToPersistentTaskbar() {}

    // TODO(b/265436799): provide animation/transition from persistent taskbar to transient one
    private fun animatePersistentToTransientTaskbar() {}

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "TaskbarPinningController:")
    }
}
