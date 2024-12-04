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

package com.android.wm.shell.desktopmode

import android.util.Log
import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/** Log Aster UIEvents for desktop windowing mode. */
@WMSingleton
class DesktopModeUiEventLogger
@Inject
constructor(
    private val mUiEventLogger: UiEventLogger,
    private val mInstanceIdSequence: InstanceIdSequence
) {
    /**
     * Logs an event for a CUI, on a particular package.
     *
     * @param uid The user id associated with the package the user is interacting with
     * @param packageName The name of the package the user is interacting with
     * @param event The event type to generate
     */
    fun log(uid: Int, packageName: String, event: DesktopUiEventEnum) {
        if (packageName.isEmpty() || uid < 0) {
            Log.d(TAG, "Skip logging since package name is empty or bad uid")
            return
        }
        mUiEventLogger.log(event, uid, packageName)
    }

    /** Retrieves a new instance id for a new interaction. */
    fun getNewInstanceId(): InstanceId = mInstanceIdSequence.newInstanceId()

    /**
     * Logs an event as part of a particular CUI, on a particular package.
     *
     * @param instanceId The id identifying an interaction, potentially taking place across multiple
     *   surfaces. There should be a new id generated for each distinct CUI.
     * @param uid The user id associated with the package the user is interacting with
     * @param packageName The name of the package the user is interacting with
     * @param event The event type to generate
     */
    fun logWithInstanceId(
        instanceId: InstanceId,
        uid: Int,
        packageName: String,
        event: DesktopUiEventEnum
    ) {
        if (packageName.isEmpty() || uid < 0) {
            Log.d(TAG, "Skip logging since package name is empty or bad uid")
            return
        }
        mUiEventLogger.logWithInstanceId(event, uid, packageName, instanceId)
    }

    companion object {
        /** Enums for logging desktop windowing mode UiEvents. */
        enum class DesktopUiEventEnum(private val mId: Int) : UiEventLogger.UiEventEnum {

            @UiEvent(doc = "Resize the window in desktop windowing mode by dragging the edge")
            DESKTOP_WINDOW_EDGE_DRAG_RESIZE(1721),
            @UiEvent(doc = "Resize the window in desktop windowing mode by dragging the corner")
            DESKTOP_WINDOW_CORNER_DRAG_RESIZE(1722),
            @UiEvent(doc = "Tap on the window header maximize button in desktop windowing mode")
            DESKTOP_WINDOW_MAXIMIZE_BUTTON_TAP(1723),
            @UiEvent(doc = "Double tap on window header to maximize it in desktop windowing mode")
            DESKTOP_WINDOW_HEADER_DOUBLE_TAP_TO_MAXIMIZE(1724);

            override fun getId(): Int = mId
        }

        private const val TAG = "DesktopModeUiEventLogger"
    }
}
