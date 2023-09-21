/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.unfold.system

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.os.Trace
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.systemui.unfold.util.CurrentActivityTypeProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityManagerActivityTypeProvider
@Inject
constructor(private val activityManager: ActivityManager) : CurrentActivityTypeProvider {

    override val isHomeActivity: Boolean?
        get() = _isHomeActivity

    private var _isHomeActivity: Boolean? = null


    override fun init() {
        _isHomeActivity = activityManager.isOnHomeActivity()
        TaskStackChangeListeners.getInstance().registerTaskStackListener(taskStackChangeListener)
    }

    override fun uninit() {
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(taskStackChangeListener)
    }

    private val taskStackChangeListener =
        object : TaskStackChangeListener {
            override fun onTaskMovedToFront(taskInfo: RunningTaskInfo) {
                _isHomeActivity = taskInfo.isHomeActivity()
            }
        }

    private fun RunningTaskInfo.isHomeActivity(): Boolean =
        topActivityType == WindowConfiguration.ACTIVITY_TYPE_HOME

    private fun ActivityManager.isOnHomeActivity(): Boolean? {
        try {
            Trace.beginSection("isOnHomeActivity")
            return getRunningTasks(/* maxNum= */ 1)?.firstOrNull()?.isHomeActivity()
        } finally {
            Trace.endSection()
        }
    }
}
