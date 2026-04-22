/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.desktopmode;

import com.android.wm.shell.desktopmode.DisplayDeskState;

/**
 * Allows external processes to register a listener in WMShell to get updates about desktop task
 * state.
 */
oneway interface IDesktopTaskListener {

    /**
     * Called once when the listener first gets connected to initialize it with the current state of
     * desks in Shell.
     */
    void onListenerConnected(in DisplayDeskState[] displayDeskStates, boolean canCreateDesks);

    /** Desktop tasks visibility has changed. Visible if at least 1 task is visible. */
    void onTasksVisibilityChanged(int displayId, int visibleTasksCount);

    /** @deprecated this is no longer supported. */
    void onStashedChanged(int displayId, boolean stashed);

    /**
     * Shows taskbar corner radius when running desktop tasks are updated if
     * [hasTasksRequiringTaskbarRounding] is true.
     */
    void onTaskbarCornerRoundingUpdate(boolean hasTasksRequiringTaskbarRounding);

    /** Entering desktop mode transition is started, send the signal with transition duration. */
    void onEnterDesktopModeTransitionStarted(int transitionDuration);

    /** Exiting desktop mode transition is started, send the signal with transition duration
     *  and whether the display will end up at the home activity (as opposed to an app's full screen
     *  mode).
     */
    void onExitDesktopModeTransitionStarted(int transitionDuration, boolean shouldEndUpAtHome);

    /**
     * Called when the conditions that allow the creation of a new desk changes. This is a global
     * state for the entire device.
     */
    void onCanCreateDesksChanged(boolean canCreateDesks);

    /** Called when a desk whose ID is `deskId` is added to the display whose ID is `displayId`. */
    void onDeskAdded(int displayId, int deskId);

    /**
     * Called when a desk whose ID is `deskId` is removed from the display whose ID is `displayId`.
     */
    void onDeskRemoved(int displayId, int deskId);

    /**
     * Called when the active desk changes on the display whose ID is `displayId`.
     * If `newActiveDesk` is -1, it means a desk is no longer active on the display.
     * If `oldActiveDesk` is -1, it means a desk was not active on the display.
     */
    void onActiveDeskChanged(int displayId, int newActiveDesk, int oldActiveDesk);
}