/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.ActivityTaskManager
import android.app.IActivityTaskManager
import android.app.IAssistDataReceiver
import android.app.assist.AssistContent
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.RemoteException
import android.util.Slog
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executor

/**
 * Can be used to request the AssistContent from a provided task id, useful for getting the web uri
 * if provided from the task.
 */
class AssistContentRequester(
    context: Context,
    private val callBackExecutor: Executor,
    private val systemInteractionExecutor: Executor
) {
    interface Callback {
        // Called when the [AssistContent] of the requested task is available.
        fun onAssistContentAvailable(assistContent: AssistContent?)
    }

    private val activityTaskManager: IActivityTaskManager = ActivityTaskManager.getService()
    private val attributionTag: String? = context.attributionTag
    private val packageName: String = context.applicationContext.packageName

    // If system loses the callback, our internal cache of original callback will also get cleared.
    private val pendingCallbacks = Collections.synchronizedMap(WeakHashMap<Any, Callback>())

    /**
     * Request the [AssistContent] from the task with the provided id.
     *
     * @param taskId to query for the content.
     * @param callback to call when the content is available, called on the main thread.
     */
    fun requestAssistContent(taskId: Int, callback: Callback) {
        // ActivityTaskManager interaction here is synchronous, so call off the main thread.
        systemInteractionExecutor.execute {
            try {
                val success = activityTaskManager.requestAssistDataForTask(
                    AssistDataReceiver(callback, this),
                    taskId,
                    packageName,
                    attributionTag,
                    false /* fetchStructure */
                )
                if (!success) {
                    executeOnMainExecutor { callback.onAssistContentAvailable(null) }
                }
            } catch (e: RemoteException) {
                Slog.e(TAG, "Requesting assist content failed for task: $taskId", e)
            }
        }
    }

    private fun executeOnMainExecutor(callback: Runnable) {
        callBackExecutor.execute(callback)
    }

    private class AssistDataReceiver(
            callback: Callback,
            parent: AssistContentRequester
    ) : IAssistDataReceiver.Stub() {
        // The AssistDataReceiver binder callback object is passed to a system server, that may
        // keep hold of it for longer than the lifetime of the AssistContentRequester object,
        // potentially causing a memory leak. In the callback passed to the system server, only
        // keep a weak reference to the parent object and lookup its callback if it still exists.
        private val parentRef: WeakReference<AssistContentRequester>
        private val callbackKey = Any()

        init {
            parent.pendingCallbacks[callbackKey] = callback
            parentRef = WeakReference(parent)
        }

        override fun onHandleAssistData(data: Bundle?) {
            val content = data?.getParcelable(ASSIST_KEY_CONTENT, AssistContent::class.java)
            if (content == null) {
                Slog.d(TAG, "Received AssistData, but no AssistContent found")
                return
            }
            val requester = parentRef.get()
            if (requester != null) {
                val callback = requester.pendingCallbacks[callbackKey]
                if (callback != null) {
                    requester.executeOnMainExecutor { callback.onAssistContentAvailable(content) }
                } else {
                    Slog.d(TAG, "Callback received after calling UI was disposed of")
                }
            } else {
                Slog.d(TAG, "Callback received after Requester was collected")
            }
        }

        override fun onHandleAssistScreenshot(screenshot: Bitmap) {}
    }

    companion object {
        private const val TAG = "AssistContentRequester"
        private const val ASSIST_KEY_CONTENT = "content"
    }
}