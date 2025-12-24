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

package com.android.quickstep.task.apptimer

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.android.launcher3.R
import java.time.Duration

interface ViewModel<T> {
    val uiState: State<T>

    fun getFormattedDuration(duration: Duration, context: Context): String

    fun startActivityWithScaleUpAnimation(
        activityOptions: ActivityOptions,
        context: Context,
        taskPackage: String,
        taskDescription: String?,
    )
}

class TaskAppTimerViewModel : ViewModel<TaskAppTimerUiState> {
    private var _uiState = mutableStateOf<TaskAppTimerUiState>(TaskAppTimerUiState.Uninitialized)

    override val uiState = _uiState

    override fun getFormattedDuration(duration: Duration, context: Context) =
        DurationFormatter.format(context, duration, R.string.shorter_duration_less_than_one_minute)

    override fun startActivityWithScaleUpAnimation(
        activityOptions: ActivityOptions,
        context: Context,
        taskPackage: String,
        taskDescription: String?,
    ) {
        try {
            context.startActivity(appUsageSettingsIntent(taskPackage), activityOptions.toBundle())
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Failed to open $taskDescription ", e)
        }
    }

    fun setState(uiState: TaskAppTimerUiState) {
        _uiState.value = uiState
    }

    private fun appUsageSettingsIntent(packageName: String) =
        Intent(Settings.ACTION_APP_USAGE_SETTINGS)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private companion object {
        const val TAG = "TaskAppTimerViewModel"
    }
}
