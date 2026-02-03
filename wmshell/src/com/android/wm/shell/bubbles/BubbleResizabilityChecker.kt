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

package com.android.wm.shell.bubbles

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Log

/**
 * Checks if an intent is resizable to display in a bubble.
 */
class BubbleResizabilityChecker : ResizabilityChecker {

    override fun isResizableActivity(
        intent: Intent?,
        packageManager: PackageManager, key: String
    ): Boolean {
        if (intent == null) {
            Log.w(TAG, "Unable to send as bubble: $key null intent")
            return false
        }
        val info = intent.resolveActivityInfo(packageManager, 0)
        if (info == null) {
            Log.w(
                TAG, ("Unable to send as bubble: " + key
                        + " couldn't find activity info for intent: " + intent)
            )
            return false
        }
        if (!ActivityInfo.isResizeableMode(info.resizeMode)) {
            Log.w(
                TAG, ("Unable to send as bubble: " + key
                        + " activity is not resizable for intent: " + intent)
            )
            return false
        }
        return true
    }

    companion object {
        private const val TAG = "BubbleResizeChecker"
    }
}