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

package com.android.wm.shell.common

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.view.Display.DEFAULT_DISPLAY
import android.window.DesktopExperienceFlags.ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY
import android.window.WindowContainerTransaction

/** Creates home intent **/
class HomeIntentProvider(
    private val context: Context,
) {
    fun addLaunchHomePendingIntent(
        wct: WindowContainerTransaction, displayId: Int, userId: Int? = null
    ) {
        val userHandle =
            if (userId != null) UserHandle.of(userId) else UserHandle.of(ActivityManager.getCurrentUser())

        val launchHomeIntent = Intent(Intent.ACTION_MAIN).apply {
            if (displayId != DEFAULT_DISPLAY) {
                addCategory(Intent.CATEGORY_SECONDARY_HOME)
            } else {
                addCategory(Intent.CATEGORY_HOME)
            }
        }
        val options = ActivityOptions.makeBasic().apply {
            launchWindowingMode = WINDOWING_MODE_FULLSCREEN
            pendingIntentBackgroundActivityStartMode =
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            if (ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY.isTrue) {
                launchDisplayId = displayId
            }
        }
        val pendingIntent = PendingIntent.getActivityAsUser(
            context,
            /* requestCode= */ 0,
            launchHomeIntent,
            PendingIntent.FLAG_IMMUTABLE,
            /* options= */ null,
            userHandle,
        )
        wct.sendPendingIntent(pendingIntent, launchHomeIntent, options.toBundle())
    }
}