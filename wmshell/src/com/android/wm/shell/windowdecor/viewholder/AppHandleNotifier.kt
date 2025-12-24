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

package com.android.wm.shell.windowdecor.viewholder

import android.util.Log
import android.window.DesktopExperienceFlags
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.annotations.ExternalThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import java.util.Objects
import java.util.concurrent.Executor
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Implements both the registering of the listeners for those who needs to consume changes related
 * to appHandles as well as those who need to update said consumers.
 */
@Singleton
class AppHandleNotifier(
    private val shellExecutor: ShellExecutor,
    private val windowDecorCaptionRepository: WindowDecorCaptionRepository,
    @ShellMainThread private val mainScope: CoroutineScope,
) {
    private val TAG = ShellProtoLogGroup.WM_SHELL_APP_HANDLES.tag

    /** The key=callback must be invoked on its respective value=executor. */
    private val listeners = mutableMapOf<AppHandlePositionCallback, Executor>()
    /** Key should be taskId an app handle is associated with. */
    private var currentHandles: MutableMap<Int, AppHandleIdentifier> = mutableMapOf()
    /** Instance returned to external sysui. */
    private val appHandleImpl = AppHandlesImpl()

    init {
        if (DesktopExperienceFlags.ENABLE_APP_HANDLE_POSITION_REPORTING.isTrue()) {
            mainScope.launch {
                windowDecorCaptionRepository.captionStateFlow.collect { captionState ->
                    when (captionState) {
                        is CaptionState.NoCaption -> {
                            removeHandle(captionState.taskId)
                        }
                        is CaptionState.AppHeader -> {
                            removeHandle(captionState.runningTaskInfo.taskId)
                        }
                        is CaptionState.AppHandle -> {
                            addHandle(captionState.appHandleIdentifier)
                        }
                    }
                }
            }
        }
    }

    /** Will immediately invoke the [listener] on the provided [sysuiExecutor]. */
    fun addListener(sysuiExecutor: Executor, listener: AppHandlePositionCallback) {
        listeners[listener] = sysuiExecutor
        val handlesToNotify = currentHandles.toMap()

        // Notify the new/updated listener using ITS specified executor
        try {
            sysuiExecutor.execute { listener.onAppHandlesUpdated(handlesToNotify) }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to execute initial notification for " + "listener on $sysuiExecutor",
                e,
            )
        }
    }

    fun removeListener(listener: AppHandlePositionCallback) {
        listeners.remove(listener)
    }

    /**
     * Adds or updates a single App Handle using its taskId as the key. Triggers notification to
     * listeners.
     *
     * @param handle The AppHandleIdentifier to add or update.
     */
    private fun addHandle(handle: AppHandleIdentifier) {
        val key = handle.taskId
        ProtoLog.v(
            ShellProtoLogGroup.WM_SHELL_APP_HANDLES,
            "Requesting add/update handle for taskId:%s",
            key,
        )
        val existingHandle = currentHandles[key]
        if (existingHandle != null && Objects.equals(existingHandle, handle)) {
            return
        }
        currentHandles[key] = handle
        notifyListeners()
    }

    /**
     * Removes a single App Handle based on its taskId. Triggers notification to listeners ONLY if a
     * handle was actually removed.
     *
     * @param taskId The Task ID of the handle to remove. // *** Parameter Renamed ***
     */
    private fun removeHandle(taskId: Int) {
        ProtoLog.v(
            ShellProtoLogGroup.WM_SHELL_APP_HANDLES,
            "Requesting remove handle for taskId:%s",
            taskId,
        )
        // Use taskId to remove from the map
        if (currentHandles.remove(taskId) == null) {
            return
        }
        notifyListeners()
    }

    fun asAppHandleImpl(): AppHandles {
        return appHandleImpl
    }

    // --- Central Notification Method  ---

    /** Notifies all registered listeners with the current state of the handles map. */
    private fun notifyListeners() {
        // Create an immutable snapshot of the current state to send to listeners
        val handlesToNotifyMap = currentHandles.toMap()
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_APP_HANDLES, "Notifying listeners of handle update")
        listeners.forEach { (callback, executor) ->
            try {
                executor.execute { callback.onAppHandlesUpdated(handlesToNotifyMap) }
            } catch (e: Exception) {
                Log.e(
                    "AppHandleManagerImpl",
                    "Failed to dispatch notification to " + "callback on $executor",
                    e,
                )
            }
        }
    }

    /**
     * This class acts as a thread boundary to ensure that we run shell code on the shell thread.
     * This class gets handed off to sysui, which by default calls this on the sysui main thread.
     */
    @ExternalThread
    private inner class AppHandlesImpl : AppHandles {
        override fun addListener(sysuiExecutor: Executor, listener: AppHandlePositionCallback) {
            shellExecutor.execute { this@AppHandleNotifier.addListener(sysuiExecutor, listener) }
        }

        override fun removeListener(listener: AppHandlePositionCallback) {
            shellExecutor.execute { this@AppHandleNotifier.removeListener(listener) }
        }
    }
}
