/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.tapl;

import android.graphics.Point;

/** Launchable that can serve as a source for dragging and dropping to the workspace. */
interface WorkspaceDragSource {

    /**
     * Drags an object to the center of homescreen.
     *
     * @param startsActivity   whether it's expected to start an activity.
     * @param isWidgetShortcut whether we drag a widget shortcut
     */
    default void dragToWorkspace(boolean startsActivity, boolean isWidgetShortcut) {
        Launchable launchable = getLaunchable();
        LauncherInstrumentation launcher = launchable.mLauncher;
        try (LauncherInstrumentation.Closable e = launcher.eventsCheck()) {
            final Point launchableCenter = launchable.getObject().getVisibleCenter();
            final Point displaySize = launcher.getRealDisplaySize();
            final int width = displaySize.x / 2;
            Workspace.dragIconToWorkspace(
                    launcher,
                    launchable,
                    new Point(
                            launchableCenter.x >= width
                                    ? launchableCenter.x - width / 2
                                    : launchableCenter.x + width / 2,
                            displaySize.y / 2),
                    launchable.getLongPressIndicator(),
                    startsActivity,
                    isWidgetShortcut,
                    launchable::addExpectedEventsForLongClick);
        }
    }

    /** This method requires public access, however should not be called in tests. */
    Launchable getLaunchable();
}
