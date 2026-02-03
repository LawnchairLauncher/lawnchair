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
package com.android.wm.shell.common.pip

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.window.DesktopExperienceFlags
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler
import com.android.wm.shell.desktopmode.desktopfirst.isDisplayDesktopFirst
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.recents.RecentsTransitionStateListener
import com.android.wm.shell.recents.RecentsTransitionStateListener.RecentsTransitionState
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING
import com.android.wm.shell.shared.pip.PipFlags
import java.util.Optional

/** Helper class for PiP on Desktop Mode. */
class PipDesktopState(
    private val pipDisplayLayoutState: PipDisplayLayoutState,
    recentsTransitionHandler: RecentsTransitionHandler,
    private val desktopUserRepositoriesOptional: Optional<DesktopUserRepositories>,
    private val dragToDesktopTransitionHandlerOptional: Optional<DragToDesktopTransitionHandler>,
    val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
) {
    @RecentsTransitionState
    private var recentsTransitionState = TRANSITION_STATE_NOT_RUNNING

    init {
        recentsTransitionHandler.addTransitionStateListener(
            object : RecentsTransitionStateListener {
                override fun onTransitionStateChanged(@RecentsTransitionState state: Int) {
                    logV(
                        "Recents transition state changed: %s",
                        RecentsTransitionStateListener.stateToString(state),
                    )
                    recentsTransitionState = state
                }
            }
        )
    }

    /**
     * Returns whether PiP in Desktop Windowing is enabled by checking the following:
     * - PiP in Desktop Windowing flag is enabled
     * - DesktopUserRepositories is present
     * - DragToDesktopTransitionHandler is present
     */
    fun isDesktopWindowingPipEnabled(): Boolean =
        DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP.isTrue &&
                desktopUserRepositoriesOptional.isPresent &&
                dragToDesktopTransitionHandlerOptional.isPresent

    /**
     * Returns whether PiP in Connected Displays is enabled by checking the following:
     * - PiP in Desktop Windowing is enabled
     * - PiP in Connected Displays flag is enabled
     * - PiP2 is enabled
     */
    fun isConnectedDisplaysPipEnabled(): Boolean =
        isDesktopWindowingPipEnabled() &&
                DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_PIP.isTrue &&
                PipFlags.isPip2ExperimentEnabled

    /**
     * Returns whether dragging PiP in Connected Displays is enabled by checking the following:
     * - Dragging PiP in Connected Displays flag is enabled
     * - PiP in Connected Displays flag is enabled
     * - PiP2 flag is enabled
     */
    fun isDraggingPipAcrossDisplaysEnabled(): Boolean =
        DesktopExperienceFlags.ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS.isTrue &&
                isConnectedDisplaysPipEnabled()

    /** Returns whether the display with the PiP task is in freeform windowing mode. */
    private fun isDisplayInFreeform(): Boolean {
        val tdaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(
            pipDisplayLayoutState.displayId
        )

        return tdaInfo?.configuration?.windowConfiguration?.windowingMode == WINDOWING_MODE_FREEFORM
    }

    /** Returns whether PiP is active in a display that is in active Desktop Mode session. */
    fun isPipInDesktopMode(): Boolean {
        if (!isDesktopWindowingPipEnabled()) {
            return false
        }

        val displayId = pipDisplayLayoutState.displayId
        logV(
            "isPipInDesktopMode isAnyDeskActive=%b isDisplayDesktopFirst=%b",
            desktopUserRepositoriesOptional.get().current.isAnyDeskActive(displayId),
            rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId),
        )
        return desktopUserRepositoriesOptional.get().current.isAnyDeskActive(displayId) ||
                rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)
    }

    /** Returns whether the display with the given id is a Desktop-first display. */
    fun isDisplayDesktopFirst(displayId: Int) =
        rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)

    /** Returns whether Recents is in the middle of animating. */
    fun isRecentsAnimating(): Boolean =
        RecentsTransitionStateListener.isAnimating(recentsTransitionState)

    /** Returns the windowing mode to restore to when resizing out of PIP direction. */
    fun getOutPipWindowingMode(): Int {
        val isInDesktop = isPipInDesktopMode()
        // Temporary workaround for b/409201669: Always expand to fullscreen if we're exiting PiP
        // in the middle of Recents animation from Desktop session.
        if (isRecentsAnimating() && isInDesktop) {
            return WINDOWING_MODE_FULLSCREEN
        }

        // If we are exiting PiP while the device is in Desktop mode, the task should expand to
        // freeform windowing mode.
        // 1) If the display windowing mode is freeform or if the ENABLE_MULTIPLE_DESKTOPS_BACKEND
        //    flag is true, set windowing mode to UNDEFINED so it will resolve the windowing mode to
        //    the display or root desk's windowing mode (which is always FREEFORM).
        // 2) Otherwise, set windowing mode to FREEFORM.
        if (isInDesktop) {
            return if (isDisplayInFreeform()
                || DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                WINDOWING_MODE_UNDEFINED
            } else {
                WINDOWING_MODE_FREEFORM
            }
        }

        // By default, or if the task is going to fullscreen, reset the windowing mode to undefined.
        return WINDOWING_MODE_UNDEFINED
    }

    /** Returns whether there is a drag-to-desktop transition in progress. */
    fun isDragToDesktopInProgress(): Boolean =
        isDesktopWindowingPipEnabled() && dragToDesktopTransitionHandlerOptional.get().inProgress

    /** Returns the DisplayLayout associated with the display where PiP window is in. */
    fun getCurrentDisplayLayout(): DisplayLayout = pipDisplayLayoutState.displayLayout

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "PipDesktopState"
    }
}
