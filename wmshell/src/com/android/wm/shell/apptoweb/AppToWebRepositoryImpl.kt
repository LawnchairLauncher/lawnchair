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
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.IndentingPrintWriter
import android.util.SparseArray
import androidx.core.net.toUri
import androidx.core.util.forEach
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTaskOrganizer.TaskVanishedListener
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.sysui.ShellInit
import java.io.PrintWriter
import kotlin.coroutines.suspendCoroutine

/**
 * App-to-Web has the following features: transferring an app session to the web and transferring
 * a web session to the relevant app. To transfer an app session to the web, we utilize
 * three different [Uri]s:
 * 1. webUri: The web URI provided by the app using [AssistContent]
 * 2. capturedLink: The link used to open the app if app was opened by clicking on a link
 * 3. genericLink: The system provided link for the app
 * In order to create the [Intent] to transfer the user from app to the web, the [Uri]s listed above
 * are checked in the given order and the first non-null link is used. When transferring from the
 * web to an app, the [Uri] must be provided by the browser application through [AssistContent].
 *
 * This Repository encapsulates the data stored for the App-to-Web feature for all tasks and
 * creates the intents used to open switch between an app or browser session.
 */
class AppToWebRepositoryImpl(
    private val context: Context,
    private val assistContentRequester: AssistContentRequester,
    private val genericLinksParser: AppToWebGenericLinksParser,
    shellTaskOrganizer: ShellTaskOrganizer,
    shellInit: ShellInit,
) : TaskVanishedListener, AppToWebRepository {
    private var appToWebDataByTask = SparseArray<TaskAppToWebData>()

    init {
        shellInit.addInitCallback(
            { shellTaskOrganizer.addTaskVanishedListener(this) }, this
        )
    }

    override fun onTaskVanished(taskInfo: RunningTaskInfo) {
        logD("Task %d is vanishing. Removing task data from repository", taskInfo.taskId)
        appToWebDataByTask.remove(taskInfo.taskId)
    }

    /** Sets the captured link for the given task if a new link is provided. */
    override fun setCapturedLink(taskId: Int, link: Uri, timeStamp: Long) {
        val taskData = getOrCreateTaskData(taskId)
        if (taskData.capturedLink?.timeStamp == timeStamp) return
        taskData.capturedLink = CapturedLink(link, timeStamp)
    }

    /**
     * Checks if [capturedLink] is available (non-null and has not been used)  for the given task
     * to use for switching to browser session.
     */
    override fun isCapturedLinkAvailable(taskId: Int): Boolean {
        val taskData = getOrCreateTaskData(taskId)
        val link = taskData.capturedLink ?: return false
        return !link.used
    }

    /** Sets the captured link as used for the given task. */
    override fun onCapturedLinkUsed(taskId: Int) {
        val taskData = getOrCreateTaskData(taskId)
        taskData.capturedLink?.setUsed()
    }

    /**
     * Records the timestamp of the most recent request to show the App-to-Web education  for the
     * given task and returns [true] if new request is received.
     */
    override fun updateAppToWebEducationRequestTimestamp(
        taskId: Int,
        latestOpenInBrowserEducationTimestamp: Long
    ): Boolean {
        val taskData = getOrCreateTaskData(taskId)
        if (latestOpenInBrowserEducationTimestamp == 0L
            || (latestOpenInBrowserEducationTimestamp == taskData.educationRequestTimestamp)
        ) {
            return false
        }
        logD(
            "Updating education request timestamp with timestamp %d for task %d",
            latestOpenInBrowserEducationTimestamp,
            taskId
        )
        taskData.educationRequestTimestamp = latestOpenInBrowserEducationTimestamp
        return true
    }

    /** Returns true if browser application and [Uri] are available for the given task. */
    override suspend fun isBrowserSessionAvailable(taskInfo: RunningTaskInfo): Boolean {
        logD("Checking for valid browser session for task %d", taskInfo.taskId)
        // If no browser application is available, return false
        context.packageManager.getDefaultBrowserPackageNameAsUser(taskInfo.userId)
            ?: return false

        if (isCapturedLinkAvailable(taskInfo.taskId) || getGenericLink(taskInfo) != null) {
            return true
        }
        val assistContent = assistContentRequester.requestAssistContent(taskInfo.taskId)
        return assistContent?.getSessionWebUri() != null
    }

    /**
     * Retrieves the latest webUri and genericLink  for the given task. If the task requesting the
     * intent [isBrowserApp], intent is created to switch to application if link was provided by
     * browser app and a relevant application exists to host the app. Otherwise, returns intent to
     * switch to browser if webUri, capturedLink, or genericLink is available.
     *
     * Note that the capturedLink should be updated separately using [setCapturedLink]
     *
     */
    override suspend fun getAppToWebIntent(
        taskInfo: RunningTaskInfo,
        isBrowserApp: Boolean
    ): Intent? {
        logD("Updating browser links for task %d", taskInfo.taskId)
        val assistContent = assistContentRequester.requestAssistContent(taskInfo.taskId)
        val webUri = assistContent?.getSessionWebUri()
        return if (isBrowserApp) {
            getAppIntent(taskInfo, webUri)
        } else {
            getBrowserIntent(taskInfo, webUri, getGenericLink(taskInfo))
        }
    }

    private suspend fun AssistContentRequester.requestAssistContent(taskId: Int): AssistContent? =
        suspendCoroutine { continuation ->
            requestAssistContent(taskId) { continuation.resumeWith(Result.success(it)) }
        }

    /** Returns the browser link associated with the given application if available. */
    private fun getBrowserIntent(
        taskInfo: RunningTaskInfo,
        webUri: Uri?,
        genericLink: Uri?
    ): Intent? {
        val taskData = getOrCreateTaskData(taskInfo.taskId)
        val browserLink = webUri ?: if (isCapturedLinkAvailable(taskInfo.taskId)) {
            taskData.capturedLink?.uri
        } else {
            genericLink
        } ?: return null
        return getBrowserIntent(browserLink, context.packageManager, taskInfo.userId)
    }

    private fun getAppIntent(taskInfo: RunningTaskInfo, webUri: Uri?): Intent? {
        webUri ?: return null
        return getAppIntent(
            uri = webUri,
            packageManager = context.packageManager,
            userId = taskInfo.userId,
        )
    }

    private fun getGenericLink(taskInfo: RunningTaskInfo): Uri? {
        logD("Updating generic link for task %d", taskInfo.taskId)
        val baseActivity = taskInfo.baseActivity ?: return null
        return genericLinksParser.getGenericLink(baseActivity.packageName)?.toUri()
    }

    private fun getOrCreateTaskData(taskId: Int) =
        appToWebDataByTask[taskId] ?: TaskAppToWebData().also { appToWebDataByTask[taskId] = it }

    /** Dumps the repository's current state. */
    fun dump(originalWriter: PrintWriter, prefix: String) {
        val pw = IndentingPrintWriter(originalWriter, " ", prefix)
        pw.increaseIndent()
        appToWebDataByTask.forEach { key, value ->
            pw.println("AppToWebRepository for task#$key")
            pw.increaseIndent()
            pw.println("CapturedLink=${value.capturedLink}")
            pw.println("EducationRequestTimestamp=${value.educationRequestTimestamp}")
            pw.decreaseIndent()
        }
    }

    private data class TaskAppToWebData(
        var capturedLink: CapturedLink? = null,
        var educationRequestTimestamp: Long = 0L,
    )

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    /** Encapsulates data associated with a captured link. */
    private data class CapturedLink(val uri: Uri, val timeStamp: Long) {

        /** Signifies if captured link has already been used, making it invalid. */
        var used = false

        /** Sets the captured link as used. */
        fun setUsed() {
            used = true
        }
    }

    companion object {
        private const val TAG = "AppToWebRepository"
    }
}
