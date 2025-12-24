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

package com.android.wm.shell.apptoweb

import android.app.ActivityManager.RunningTaskInfo
import android.content.Intent
import android.net.Uri

/** Interface for storing and retrieving data for app-to-web. */
interface AppToWebRepository {

    /**
     * Updates the most recent show education request timestamp and returns [true] when new request
     * is received.
     */
    fun updateAppToWebEducationRequestTimestamp(
        taskId: Int, latestOpenInBrowserEducationTimestamp: Long
    ): Boolean

    /**
     * Returns true if browser app and valid URI is available to switch to viewing app content
     * on browser.
     */
    suspend fun isBrowserSessionAvailable(taskInfo: RunningTaskInfo): Boolean

    /** Returns [true] if repository has a saved and unused captured link. */
    fun isCapturedLinkAvailable(taskId: Int): Boolean

    /** Sets the captured link for the given task if a new link is provided. */
    fun setCapturedLink(taskId: Int, link: Uri, timeStamp: Long)

    /** Sets the captured link as used for the given task. */
    fun onCapturedLinkUsed(taskId: Int)

    /**
     * Retrieves the latest webUri and genericLink  for the given task. If the task requesting the
     * intent [isBrowserApp], intent is created to switch to application if link was provided by
     * browser app and a relevant application exists to host the app. Otherwise, returns intent to
     * switch to browser if webUri, capturedLink, or genericLink is available.
     *
     * Note that the capturedLink should be updated separately using [setCapturedLink]
     *
     */
    suspend fun getAppToWebIntent(taskInfo: RunningTaskInfo, isBrowserApp: Boolean): Intent?
}
