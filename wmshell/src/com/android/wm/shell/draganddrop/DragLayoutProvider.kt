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

import android.content.res.Configuration
import android.view.DragEvent
import android.view.SurfaceControl
import android.view.ViewGroup
import android.window.WindowContainerToken
import com.android.internal.logging.InstanceId
import java.io.PrintWriter

/** Interface to be implemented by any controllers providing a layout for DragAndDrop in Shell */
interface DragLayoutProvider {
    /**
     * Updates the drag layout based on the given drag session.
     */
    fun updateSession(session: DragSession)
    /**
     * Called when a new drag is started.
     */
    fun prepare(session: DragSession, loggerSessionId: InstanceId?)

    /**
     * Shows the drag layout.
     */
    fun show()

    /**
     * Updates the visible drop target as the user drags.
     */
    fun update(event: DragEvent?)

    /**
     * Hides the drag layout and animates out the visible drop targets.
     */
    fun hide(event: DragEvent?, hideCompleteCallback: Runnable?)

    /**
     * Whether target has already been dropped or not
     */
    fun hasDropped(): Boolean

    /**
     * Handles the drop onto a target and animates out the visible drop targets.
     */
    fun drop(
        event: DragEvent?, dragSurface: SurfaceControl,
        hideTaskToken: WindowContainerToken?, dropCompleteCallback: Runnable?
    ): Boolean

    /**
     * Dumps information about this drag layout.
     */
    fun dump(pw: PrintWriter, prefix: String?)

    /**
     * @return a View which will be added to the global root view for drag and drop
     */
    fun addDraggingView(viewGroup: ViewGroup)

    /**
     * Called when the configuration changes.
     */
    fun onConfigChanged(newConfig: Configuration?)
}