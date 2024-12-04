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
package com.android.wm.shell.draganddrop

import android.app.ActivityManager
import android.os.RemoteException
import android.os.Trace
import android.os.Trace.TRACE_TAG_WINDOW_MANAGER
import android.util.Log
import android.view.DragEvent
import android.view.IWindowManager
import android.window.IGlobalDragListener
import android.window.IUnhandledDragCallback
import androidx.annotation.VisibleForTesting
import com.android.internal.protolog.common.ProtoLog
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.protolog.ShellProtoLogGroup
import java.util.function.Consumer
import kotlin.random.Random

/**
 * Manages the listener and callbacks for unhandled global drags.
 * This is only used by DragAndDropController and should not be used directly by other classes.
 */
class GlobalDragListener(
    private val wmService: IWindowManager,
    private val mainExecutor: ShellExecutor
) {
    private var callback: GlobalDragListenerCallback? = null

    private val globalDragListener: IGlobalDragListener =
        object : IGlobalDragListener.Stub() {
            override fun onCrossWindowDrop(taskInfo: ActivityManager.RunningTaskInfo) {
                mainExecutor.execute() {
                    this@GlobalDragListener.onCrossWindowDrop(taskInfo)
                }
            }

            override fun onUnhandledDrop(event: DragEvent, callback: IUnhandledDragCallback) {
                mainExecutor.execute() {
                    this@GlobalDragListener.onUnhandledDrop(event, callback)
                }
            }
        }

    /**
     * Callbacks for global drag events.
     */
    interface GlobalDragListenerCallback {
        /**
         * Called when a global drag is successfully handled by another window.
         */
        fun onCrossWindowDrop(taskInfo: ActivityManager.RunningTaskInfo) {}

        /**
         * Called when a global drag is unhandled (ie. dropped outside of all visible windows, or
         * dropped on a window that does not want to handle it).
         *
         * The implementer _must_ call onFinishedCallback, and if it consumes the drop, then it is
         * also responsible for releasing up the drag surface provided via the drag event.
         */
        fun onUnhandledDrop(dragEvent: DragEvent, onFinishedCallback: Consumer<Boolean>) {}
    }

    /**
     * Sets a listener for callbacks when an unhandled drag happens.
     */
    fun setListener(listener: GlobalDragListenerCallback?) {
        val updateWm = (callback == null && listener != null)
                || (callback != null && listener == null)
        callback = listener
        if (updateWm) {
            try {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                    "%s unhandled drag listener",
                    if (callback != null) "Registering" else "Unregistering")
                wmService.setGlobalDragListener(
                    if (callback != null) globalDragListener else null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to set unhandled drag listener")
            }
        }
    }

    @VisibleForTesting
    fun onCrossWindowDrop(taskInfo: ActivityManager.RunningTaskInfo) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
            "onCrossWindowDrop: %s", taskInfo)
        callback?.onCrossWindowDrop(taskInfo)
    }

    @VisibleForTesting
    fun onUnhandledDrop(dragEvent: DragEvent, wmCallback: IUnhandledDragCallback) {
        val traceCookie = Random.nextInt()
        Trace.asyncTraceBegin(TRACE_TAG_WINDOW_MANAGER, "GlobalDragListener.onUnhandledDrop",
            traceCookie);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
            "onUnhandledDrop: %s", dragEvent)
        if (callback == null) {
            wmCallback.notifyUnhandledDropComplete(false)
            Trace.asyncTraceEnd(TRACE_TAG_WINDOW_MANAGER, "GlobalDragListener.onUnhandledDrop",
                traceCookie);
            return
        }

        callback?.onUnhandledDrop(dragEvent) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                "Notifying onUnhandledDrop complete: %b", it)
            wmCallback.notifyUnhandledDropComplete(it)
            Trace.asyncTraceEnd(TRACE_TAG_WINDOW_MANAGER, "GlobalDragListener.onUnhandledDrop",
                traceCookie);
        }
    }

    companion object {
        private val TAG = GlobalDragListener::class.java.simpleName
    }
}
