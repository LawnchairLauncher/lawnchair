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

package com.android.quickstep.util

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import android.view.View
import com.android.quickstep.task.thumbnail.TaskContentView

/**
 * Sets a click listener on the view that launches an activity using the provided [intent]
 *
 * Performs scale up animation.
 *
 * @property intent the intent to use for launching the activity
 * @property targetDescription a short text describing the target activity beings opened that can be
 *   used for logging (e.g. usage settings for task A).
 */
fun View.setActivityStarterClickListener(intent: Intent, targetDescription: String) {
    setOnClickListener { view ->
        try {
            val options = ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.width, view.height)
            context.startActivity(intent, options.toBundle())
        } catch (e: ActivityNotFoundException) {
            Log.e(TaskContentView.TAG, "Failed to open $targetDescription ", e)
        }
    }
}
