/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep

import android.content.Intent
import android.provider.Settings
import android.view.View
import androidx.core.net.toUri
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.popup.SystemShortcut
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskContainer
import com.android.window.flags2.Flags.universalResizableByDefault

/**
 * System shortcut to change the application's aspect ratio compatibility mode.
 *
 * This shows up only on screens that are not compact, ie. shortest-width greater than {@link
 * com.android.launcher3.util.window.WindowManagerProxy#MIN_TABLET_WIDTH}.
 */
class AspectRatioSystemShortcut(
    viewContainer: RecentsViewContainer,
    taskContainer: TaskContainer,
    abstractFloatingViewHelper: AbstractFloatingViewHelper,
) :
    SystemShortcut<RecentsViewContainer>(
        R.drawable.ic_aspect_ratio,
        R.string.recent_task_option_aspect_ratio,
        viewContainer,
        taskContainer.itemInfo,
        taskContainer.taskView,
        abstractFloatingViewHelper,
    ) {
    override fun onClick(view: View) {
        dismissTaskMenuView()

        val intent =
            Intent(Settings.ACTION_MANAGE_USER_ASPECT_RATIO_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (mItemInfo.targetPackage != null) {
            intent.setData(("package:" + mItemInfo.targetPackage).toUri())
        }

        mTarget.startActivitySafely(view, intent, mItemInfo)
        mTarget.statsLogManager
            .logger()
            .withItemInfo(mItemInfo)
            .log(LauncherEvent.LAUNCHER_ASPECT_RATIO_SETTINGS_SYSTEM_SHORTCUT_TAP)
    }

    companion object {
        /** Optionally create a factory for the aspect ratio system shortcut. */
        @JvmOverloads
        fun createFactory(
            abstractFloatingViewHelper: AbstractFloatingViewHelper = AbstractFloatingViewHelper()
        ): TaskShortcutFactory {
            return object : TaskShortcutFactory {
                override fun getShortcuts(
                    viewContainer: RecentsViewContainer,
                    taskContainer: TaskContainer,
                ): List<AspectRatioSystemShortcut>? {
                    return when {
                        // Only available when the feature flag is on.
                        !universalResizableByDefault() -> null

                        // The option is only shown on sw600dp+ screens (checked by isTablet)
                        !viewContainer.deviceProfile.deviceProperties.isTablet -> null

                        else -> {
                            listOf(
                                AspectRatioSystemShortcut(
                                    viewContainer,
                                    taskContainer,
                                    abstractFloatingViewHelper,
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
