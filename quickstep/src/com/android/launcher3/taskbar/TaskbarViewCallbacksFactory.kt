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

import android.app.contextualsearch.ContextualSearchManager.ENTRYPOINT_LONG_PRESS_META
import android.content.Context
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.util.ResourceBasedOverride
import com.android.launcher3.util.ResourceBasedOverride.Overrides
import com.android.quickstep.TopTaskTracker
import com.android.quickstep.util.ContextualSearchInvoker

/** Creates [TaskbarViewCallbacks] instances. */
open class TaskbarViewCallbacksFactory : ResourceBasedOverride {

    open fun create(
        activity: TaskbarActivityContext,
        controllers: TaskbarControllers,
        taskbarView: TaskbarView,
    ): TaskbarViewCallbacks {
        return object : TaskbarViewCallbacks(activity, controllers, taskbarView) {
            override fun triggerAllAppsButtonLongClick() {
                super.triggerAllAppsButtonLongClick()

                val contextualSearchInvoked =
                    ContextualSearchInvoker(activity).show(ENTRYPOINT_LONG_PRESS_META)
                if (contextualSearchInvoked) {
                    val runningPackage =
                        TopTaskTracker.INSTANCE[activity].getCachedTopTask(
                                /* filterOnlyVisibleRecents */ true,
                                activity.display.displayId,
                            )
                            .getPackageName()
                    activity.statsLogManager
                        .logger()
                        .withPackageName(runningPackage)
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_SUCCESSFUL_META)
                }
            }

            override fun isAllAppsButtonHapticFeedbackEnabled(context: Context): Boolean {
                return longPressAllAppsToStartContextualSearch(context)
            }
        }
    }

    open fun longPressAllAppsToStartContextualSearch(context: Context): Boolean =
        ContextualSearchInvoker(context).runContextualSearchInvocationChecksAndLogFailures()

    companion object {
        @JvmStatic
        fun newInstance(context: Context): TaskbarViewCallbacksFactory {
            return Overrides.getObject(
                TaskbarViewCallbacksFactory::class.java,
                context,
                R.string.taskbar_view_callbacks_factory_class,
            )
        }
    }
}
